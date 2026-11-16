package com.game.qa;

import com.game.client.controller.LocalMatchSession;
import com.game.client.screens.BaseScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.rules.GameRules;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.launcher.DuelGame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Percorre a árvore de atores em vários tamanhos lógicos e falha se algo visível ultrapassar as
 * bordas horizontais da janela. Cobre a tela de seleção de cartas, os poderes e as configurações.
 */
public final class LayoutProbe {

    private static final int[][] SIZES = {
            {1200, 650}, {1280, 720}, {1366, 768}, {1440, 900},
            {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
            // Áreas lógicas típicas de telas com escala de sistema.
            {1093, 614},  // 1366x768 @ 125%
            {1536, 864},  // 1920x1080 @ 125%
            {1280, 720},  // 1920x1080 @ 150%
            {1707, 960}   // 2560x1440 @ 150%
    };

    private LayoutProbe() {
    }

    public static void run(DuelGame game) {
        Map<String, BaseScreen> screens = new LinkedHashMap<>();
        screens.put("selecao-cartas", new GameScreen(game, new LocalMatchSession("QA"), null));
        screens.put("mesa", new GameScreen(game, new LocalMatchSession("QA"), null));
        screens.put("mesa-enviada", new GameScreen(game, tableSession(), null));
        screens.put("poderes", new GameScreen(game, powerSession(), null));
        screens.put("poderes-misturados", new GameScreen(game, powerSession(), null));
        GameScreen selection = new GameScreen(game, powerSession(), null);
        selection.demoSelectPowers(2);
        selection.demoRefusedPowerClick();
        screens.put("poderes-selecao-2", selection);
        screens.put("revelacao", new GameScreen(game, revealSession(), null));
        screens.put("revelacao-2-poderes", new GameScreen(game, revealTwoPowersSession(), null));
        screens.put("contagem", new GameScreen(game, powerSession(), null));
        screens.put("contagem-final", new GameScreen(game, revealTwoPowersSession(), null));
        screens.put("configuracoes", new SettingsScreen(game));

        int failures = 0;
        int checks = 0;
        for (Map.Entry<String, BaseScreen> entry : screens.entrySet()) {
            BaseScreen screen = entry.getValue();
            for (int[] size : SIZES) {
                checks++;
                List<String> overflow = screen.probeOverflow(size[0], size[1]);
                if (overflow.isEmpty()) {
                    System.out.printf("[LayoutProbe] %-16s %4dx%-4d PASS%n",
                            entry.getKey(), size[0], size[1]);
                } else {
                    failures++;
                    System.out.printf("[LayoutProbe] %-16s %4dx%-4d FAIL %s%n",
                            entry.getKey(), size[0], size[1], String.join(" | ", overflow));
                }
            }
        }
        System.out.println("[LayoutProbe] checks=" + checks + " failures=" + failures);
    }

    private static LocalMatchSession tableSession() {
        LocalMatchSession session = new LocalMatchSession("QA");
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, snapshot.rules().maxPlay()));
        return session;
    }

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

    /** Revelação em que os dois lados jogaram exatamente dois poderes. */
    private static LocalMatchSession revealTwoPowersSession() {
        for (int seed = 1; seed <= 300; seed++) {
            LocalMatchSession session = new LocalMatchSession("QA", powerRules(), new Random(seed));
            GameSnapshot snapshot = session.snapshot();
            session.submitPlay(snapshot.player(1).hand().subList(0, snapshot.rules().maxPlay()));
            tickUntil(session, GamePhase.SABOTAGE, 30);
            if (session.snapshot().phase() != GamePhase.SABOTAGE) {
                continue;
            }
            session.lockPowers(List.of(0, 1));
            tickUntil(session, GamePhase.REVEAL, 30);
            GameSnapshot reveal = session.snapshot();
            if (reveal.phase() == GamePhase.REVEAL && reveal.lastOutcome() != null
                    && reveal.lastOutcome().player1Powers().size() == 2
                    && reveal.lastOutcome().player2Powers().size() == 2) {
                return session;
            }
        }
        return revealSession();
    }

    private static GameRules powerRules() {
        return new GameRules(300, 8, 5, 3, 3, 3, 2);
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
