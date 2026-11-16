package com.game.qa;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.game.client.controller.LocalMatchSession;
import com.game.client.layout.LayoutMetrics;
import com.game.client.screens.BaseScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.rules.GameRules;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.launcher.DuelGame;
import com.game.rendering.PowerCardActor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Verificação de layout da Stage 8 sobre a {@link GameScreen} REAL nos seis tamanhos exigidos,
 * nas três fases que importam (BUILD, SABOTAGE/poderes e REVEAL).
 *
 * <p>Checagens por tela e tamanho: (a) todo controle interativo inteiro dentro do viewport e o
 * clique no centro chega nele; (b) as células do placar comportam o maior valor possível na fonte
 * atual; (c) nenhuma sobreposição entre cartas fora do leque da mão de poderes; (d) em BUILD a
 * fileira do oponente tem exatamente 5 versos, a do jogador está vazia e nenhum placeholder é
 * desenhado; (e) pixel-match dos curingas. Qualquer falha encerra o probe com exceção.</p>
 */
public final class LayoutProbe {

    /** Os seis tamanhos exigidos pela Stage 8. */
    private static final int[][] SIZES = {
            {1093, 614},
            {1280, 720},
            {1365, 695},
            {1366, 768},
            {1536, 864},
            {1920, 1080}
    };

    private LayoutProbe() {
    }

    public static void run(DuelGame game) {
        int failures = 0;
        int checks = 0;

        Map<String, Supplier<BaseScreen>> screens = new LinkedHashMap<>();
        screens.put("BUILD", () -> new GameScreen(game, new LocalMatchSession("QA"), null));
        screens.put("PODERES", () -> new GameScreen(game, powerSession(), null));
        screens.put("REVEAL", () -> new GameScreen(game, revealSession(), null));

        for (Map.Entry<String, Supplier<BaseScreen>> entry : screens.entrySet()) {
            for (int[] size : SIZES) {
                // Tela nova por tamanho: nenhum estado de layout vaza de um tamanho para o outro.
                GameScreen screen = (GameScreen) entry.getValue().get();
                // Conclui contagem/queima para o estado estável da fase antes de medir.
                screen.probeSettle();
                checks++;
                List<String> problems = new ArrayList<>();
                problems.addAll(screen.probeOverflow(size[0], size[1]));
                problems.addAll(checkRegions(screen, size[0], size[1]));
                problems.addAll(checkTally(screen));
                if ("BUILD".equals(entry.getKey())) {
                    problems.addAll(checkBuildRows(screen));
                } else if ("PODERES".equals(entry.getKey())) {
                    problems.addAll(checkPowerHand(screen));
                } else {
                    problems.addAll(checkRevealRows(screen));
                }
                if (problems.isEmpty()) {
                    System.out.printf("[LayoutProbe] %-8s %4dx%-4d PASS%n",
                            entry.getKey(), size[0], size[1]);
                } else {
                    failures++;
                    System.out.printf("[LayoutProbe] %-8s %4dx%-4d FAIL %s%n",
                            entry.getKey(), size[0], size[1], String.join(" | ", problems));
                }
            }
        }

        // (e) o curinga é a própria imagem: comparação de pixel com os 9 PNGs nativos.
        checks++;
        try {
            ScreenshotProbe.runJokerPixelMatch(game);
            System.out.println("[LayoutProbe] joker-pixel-match PASS");
        } catch (RuntimeException e) {
            failures++;
            System.out.println("[LayoutProbe] joker-pixel-match FAIL " + e.getMessage());
        }

        // Configurações continuam cobertas (regressão das telas que não são a mesa).
        BaseScreen settings = new SettingsScreen(game);
        for (int[] size : SIZES) {
            checks++;
            List<String> problems = settings.probeOverflow(size[0], size[1]);
            if (!problems.isEmpty()) {
                failures++;
                System.out.printf("[LayoutProbe] %-8s %4dx%-4d FAIL %s%n",
                        "CONFIG", size[0], size[1], String.join(" | ", problems));
            }
        }

        dumpRegions(game);
        System.out.println("[LayoutProbe] checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            throw new IllegalStateException("layout: " + failures + " tela(s)/tamanho(s) reprovados");
        }
    }

    // ------------------------------------------------------------------ checagens

    /** Todas as regiões dentro do palco e sem sobreposição entre si. */
    private static List<String> checkRegions(GameScreen screen, int width, int height) {
        List<String> problems = new ArrayList<>();
        LayoutMetrics metrics = screen.probeMetrics();
        if (metrics == null) {
            problems.add("sem métricas de layout");
            return problems;
        }
        Map<String, LayoutMetrics.Region> regions = new LinkedHashMap<>();
        regions.put("header", metrics.header);
        regions.put("opponentBar", metrics.opponentBar);
        regions.put("table", metrics.table);
        regions.put("ownBar", metrics.ownBar);
        regions.put("hand", metrics.hand);
        regions.put("actionBar", metrics.actionBar);
        for (Map.Entry<String, LayoutMetrics.Region> entry : regions.entrySet()) {
            if (!entry.getValue().inside(width, height)) {
                problems.add("região " + entry.getKey() + " fora de " + width + "x" + height);
            }
        }
        List<String> names = new ArrayList<>(regions.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                if (regions.get(names.get(i)).overlaps(regions.get(names.get(j)))) {
                    problems.add("regiões sobrepostas: " + names.get(i) + "/" + names.get(j));
                }
            }
        }
        // A barra de ações é reservada primeiro e nunca pode encostar na borda inferior.
        if (metrics.actionBar.y() < 1f) {
            problems.add("barra de ações na borda inferior (y=" + Math.round(metrics.actionBar.y()) + ")");
        }
        if (metrics.cards.handCardHeight()
                < LayoutMetrics.MIN_CARD_HEIGHT * LayoutMetrics.HAND_CARD_SCALE - 0.5f
                || metrics.cards.tableCardHeight() < LayoutMetrics.MIN_CARD_HEIGHT - 0.5f) {
            problems.add("carta abaixo do mínimo (" + Math.round(metrics.cards.handCardHeight())
                    + "/" + Math.round(metrics.cards.tableCardHeight()) + ")");
        }
        return problems;
    }

    /** (b) o placar dos dois lados cabe nas próprias células com os valores máximos. */
    private static List<String> checkTally(GameScreen screen) {
        List<String> problems = new ArrayList<>();
        problems.addAll(screen.probeTable().probeTally(true).probeCellWidths());
        problems.addAll(screen.probeTable().probeTally(false).probeCellWidths());
        Actor own = screen.probeTable().probeTally(true);
        LayoutMetrics.Region table = screen.probeMetrics().table;
        Vector2 position = own.localToStageCoordinates(new Vector2(0f, 0f));
        if (position.x < table.x() - 0.5f || position.x + own.getWidth() > table.right() + 0.5f
                || position.y < table.y() - 0.5f
                || position.y + own.getHeight() > table.top() + 0.5f) {
            Vector2 panel = screen.probeTable().localToStageCoordinates(new Vector2(0f, 0f));
            problems.add("placar fora da região da mesa: placar "
                    + Math.round(position.x) + "," + Math.round(position.y) + " "
                    + Math.round(own.getWidth()) + "x" + Math.round(own.getHeight())
                    + " vs mesa " + Math.round(table.x()) + "," + Math.round(table.y())
                    + " " + Math.round(table.width()) + "x" + Math.round(table.height())
                    + " painel " + Math.round(panel.x) + "," + Math.round(panel.y) + " "
                    + Math.round(screen.probeTable().getWidth()) + "x"
                    + Math.round(screen.probeTable().getHeight()));
        }
        return problems;
    }

    /** (d) BUILD: 5 versos no oponente, fileira do jogador vazia, nenhum placeholder. */
    private static List<String> checkBuildRows(GameScreen screen) {
        List<String> problems = new ArrayList<>();
        int[] opponent = screen.probeTable().probeRow(false);
        int[] own = screen.probeTable().probeRow(true);
        if (opponent[0] != 5 || opponent[1] != 0) {
            problems.add("oponente deveria ter 5 versos (versos=" + opponent[0]
                    + " abertas=" + opponent[1] + " vazios=" + opponent[2] + ")");
        }
        if (own[0] + own[1] != 0) {
            problems.add("fileira do jogador deveria estar vazia (versos=" + own[0]
                    + " abertas=" + own[1] + ")");
        }
        if (screen.probeTable().probeNoPowerVisible(true)
                || screen.probeTable().probeNoPowerVisible(false)) {
            problems.add("\"SEM PODER\" visível fora da revelação");
        }
        if (screen.probeTable().probePowerSlots(true) != 0
                || screen.probeTable().probePowerSlots(false) != 0) {
            problems.add("poder desenhado em BUILD");
        }
        return problems;
    }

    /** REVEAL: as cartas do oponente estão abertas e os poderes jogados aparecem. */
    private static List<String> checkRevealRows(GameScreen screen) {
        List<String> problems = new ArrayList<>();
        int[] opponent = screen.probeTable().probeRow(false);
        if (opponent[1] != 5) {
            problems.add("oponente deveria ter 5 cartas abertas na revelação (abertas="
                    + opponent[1] + " versos=" + opponent[0] + ")");
        }
        GameSnapshot state = screen.probeSnapshot();
        if (state != null && state.player(2).playedPowers().isEmpty()
                && !screen.probeTable().probeNoPowerVisible(false)) {
            problems.add("lado sem poder precisa mostrar \"SEM PODER\" na revelação");
        }
        return problems;
    }

    /** PODERES: 7 cartas dentro da área da mão, sobrepostas no máximo 38%. */
    private static List<String> checkPowerHand(GameScreen screen) {
        List<String> problems = new ArrayList<>();
        GameSnapshot state = screen.probeSnapshot();
        GameRules rules = state == null ? GameRules.DEFAULT : state.rules();
        int expected = rules.jokersPerRound() + rules.sabotagesPerRound();
        int shown = screen.probePowerHand().cards().size();
        if (shown != expected) {
            problems.add("mão de poderes com " + shown + " cartas (esperado " + expected + ")");
        }
        LayoutMetrics.Region hand = screen.probeMetrics().hand;
        float maxOverlap = 0f;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (int i = 0; i < shown; i++) {
            PowerCardActor actor = screen.probePowerHand().actor(i);
            if (actor == null) {
                continue;
            }
            Vector2 position = actor.localToStageCoordinates(new Vector2(0f, 0f));
            left = Math.min(left, position.x);
            right = Math.max(right, position.x + actor.getWidth());
            if (position.x < hand.x() - 0.5f || right > hand.right() + 0.5f
                    || position.y < hand.y() - 0.5f
                    || position.y + actor.getHeight() > hand.top() + 0.5f) {
                problems.add("poder " + i + " fora da área da mão");
            }
            if (i > 0) {
                PowerCardActor previous = screen.probePowerHand().actor(i - 1);
                if (previous != null) {
                    float step = position.x - previous.localToStageCoordinates(new Vector2()).x;
                    maxOverlap = Math.max(maxOverlap, 1f - step / Math.max(1f, actor.getWidth()));
                }
            }
        }
        if (right - left > hand.width() + 0.5f) {
            problems.add("leque de poderes mais largo que a área da mão");
        }
        if (maxOverlap > LayoutMetrics.MAX_POWER_OVERLAP + 0.01f) {
            problems.add("sobreposição do leque acima de 38% (" + Math.round(maxOverlap * 100) + "%)");
        }
        return problems;
    }

    /** Imprime as regiões do layout final em 1365x695 (usado no relatório da stage). */
    private static void dumpRegions(DuelGame game) {
        GameScreen screen = new GameScreen(game, new LocalMatchSession("QA"), null);
        screen.probeOverflow(1365, 695);
        LayoutMetrics metrics = screen.probeMetrics();
        System.out.println("[LayoutProbe] regiões em 1365x695:");
        printRegion("header", metrics.header);
        printRegion("opponentBar", metrics.opponentBar);
        printRegion("table", metrics.table);
        printRegion("ownBar", metrics.ownBar);
        printRegion("hand", metrics.hand);
        printRegion("actionBar", metrics.actionBar);
        printRegion("rightColumn", metrics.rightColumn);
        System.out.printf("[LayoutProbe] cartas: mão %.0fx%.0f mesa %.0fx%.0f passo do leque %.1f%n",
                metrics.cards.handCardWidth(), metrics.cards.handCardHeight(),
                metrics.cards.tableCardWidth(), metrics.cards.tableCardHeight(),
                metrics.powerStep);
    }

    private static void printRegion(String name, LayoutMetrics.Region region) {
        System.out.printf("[LayoutProbe]   %-12s x=%4.0f y=%4.0f w=%4.0f h=%4.0f%n",
                name, region.x(), region.y(), region.width(), region.height());
    }

    // ------------------------------------------------------------------ sessões

    private static LocalMatchSession powerSession() {
        LocalMatchSession session = new LocalMatchSession("QA", powerRules(), new Random(1));
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, snapshot.rules().maxPlay()));
        tickUntil(session, GamePhase.SABOTAGE, 30);
        return session;
    }

    private static LocalMatchSession revealSession() {
        LocalMatchSession session = new LocalMatchSession("QA", powerRules(), new Random(1));
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, snapshot.rules().maxPlay()));
        tickUntil(session, GamePhase.SABOTAGE, 30);
        session.lockPowers(List.of());
        tickUntil(session, GamePhase.REVEAL, 30);
        return session;
    }

    private static GameRules powerRules() {
        GameRules defaults = GameRules.DEFAULT;
        return new GameRules(defaults.startingHp(), defaults.handSize(), defaults.maxPlay(),
                defaults.damageDivisor(), defaults.jokersPerRound(), defaults.sabotagesPerRound(),
                defaults.maxPowersPlayed());
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
