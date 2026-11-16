package com.game.qa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.game.rendering.PowerCardActor;
import com.badlogic.gdx.utils.ScreenUtils;
import com.game.client.controller.LocalMatchSession;
import com.game.client.screens.BaseScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.rules.GameRules;
import com.game.core.rules.RoundOutcome;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.launcher.DuelGame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Renderiza telas em buffers offscreen nos tamanhos da matriz de QA (independente do monitor
 * físico desta máquina) e grava PNGs em {@code build/qa}.
 */
public final class ScreenshotProbe {

    private ScreenshotProbe() {
    }

    public static void run(DuelGame game) {
        Path output = Path.of("build", "qa");
        try {
            Files.createDirectories(output);
        } catch (Exception e) {
            System.out.println("[ScreenshotProbe] não foi possível criar " + output + ": " + e);
            return;
        }

        Map<String, BaseScreen> screens = new LinkedHashMap<>();
        LocalMatchSession selectionSession = new LocalMatchSession("QA");
        GameScreen selection = new GameScreen(game, selectionSession, null);
        selection.demoSelect(selectionSession.snapshot().rules().maxPlay());
        screens.put("selecao-cartas", selection);
        LocalMatchSession partialSession = new LocalMatchSession("QA");
        GameScreen partial = new GameScreen(game, partialSession, null);
        partial.demoSelect(partialSession.snapshot().rules().maxPlay() - 1);
        screens.put("selecao-4-cartas", partial);
        screens.put("mesa", new GameScreen(game, new LocalMatchSession("QA"), null));
        screens.put("mesa-enviada", new GameScreen(game, tableSession(), null));
        screens.put("poderes", new GameScreen(game, powerSession(), null));
        screens.put("poderes-misturados", new GameScreen(game, powerSession(), null));
        GameScreen selectionPowers = new GameScreen(game, powerSession(), null);
        selectionPowers.demoSelectPowers(2);
        selectionPowers.demoRefusedPowerClick();
        screens.put("poderes-selecao-2", selectionPowers);
        screens.put("revelacao", revealScreen(game, revealSession()));
        screens.put("revelacao-2-poderes",
                revealScreen(game, revealTwoPowersSession()));
        screens.put("contagem", new GameScreen(game, powerSession(), null));
        GameScreen finalCount = new GameScreen(game, revealPowerMixSession(), null);
        finalCount.demoSkipResolution();
        screens.put("contagem-final", finalCount);
        screens.put("tooltip-joker", tooltipScreen(game, PowerKind.JOKER));
        screens.put("tooltip-sabotagem", tooltipScreen(game, PowerKind.SABOTAGE));
        screens.put("tooltip-revelacao", tooltipSlotScreen(game));
        screens.put("configuracoes", new SettingsScreen(game));

        // Tamanhos de carta: 8 cartas na mão (com seleção elevada) nos dois tamanhos pedidos.
        LocalMatchSession cardSession = new LocalMatchSession("QA");
        GameScreen cardScreen = new GameScreen(game, cardSession, null);
        cardScreen.demoSelect(4);
        render(cardScreen, 1365, 695, output.resolve("cartas-1365x695.png"));
        render(cardScreen, 1920, 1080, output.resolve("cartas-1920x1080.png"));

        int[][] sizes = {
                {1365, 695},
                {1366, 768},
                {1920, 1080},
                {1536, 864},   // 1920x1080 @ 125%
                {1093, 614}    // 1366x768 @ 125%
        };
        for (Map.Entry<String, BaseScreen> entry : screens.entrySet()) {
            for (int[] size : sizes) {
                String name = entry.getKey() + "-" + size[0] + "x" + size[1];
                render(entry.getValue(), size[0], size[1], output.resolve(name + ".png"));
            }
        }
        renderPowerSelectionFrames(game, output);
        renderCountFrames(game, output, "contagem", powerSession(),
                new int[]{0, 400, 900, 1500, 2300, 3000});
        renderCountFrames(game, output, "contagem-final", revealPowerMixSession(),
                new int[]{0, 900, 1800, 2700, 3600, 4500, 5400});
        renderTooltipPopin(game, output);
        renderBurnFrames(game, output);
        renderResolutionFrames(game, output);
        renderStage8Shots(game, output);
        System.out.println("[ScreenshotProbe] PNGs em " + output.toAbsolutePath());
    }


    /** Provas estáticas da Stage 8 no tamanho reportado 1365x695. */
    private static void renderStage8Shots(DuelGame game, Path output) {
        int width = 1365;
        int height = 695;
        GameScreen build = new GameScreen(game, new LocalMatchSession("QA"), null);
        build.probeOverflow(width, height);
        build.probeStage().act(1f);
        captureStage(build, width, height, output.resolve("mesa-inicio-1365x695.png"));
        build.demoSelect(5);
        build.demoSubmit();
        build.render(1f / 60f);
        build.probeStage().act(1.2f);
        captureStage(build, width, height, output.resolve("mesa-jogada-1365x695.png"));

        GameScreen flip = new GameScreen(game, revealSession(), null);
        flip.probeOverflow(width, height);
        flip.probeStage().act(0.4f);
        captureStage(flip, width, height, output.resolve("mesa-flip-1365x695.png"));

        GameScreen powers = new GameScreen(game, powerSession(), null);
        powers.probeOverflow(width, height);
        for (int i = 0; i < 900 && !powers.demoTransitionDone(); i++) {
            powers.probeStage().act(1f / 60f);
        }
        powers.probeStage().act(1.2f);
        powers.demoSelectPowers(2);
        powers.demoHoverPower(PowerKind.JOKER);
        powers.probeStage().act(0.2f);
        captureStage(powers, width, height, output.resolve("poderes-7-1365x695.png"));
        captureStage(powers, width, height, output.resolve("layout-final-1365x695.png"));
    }

    private static void captureStage(BaseScreen screen, int width, int height, Path file) {
        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        framebuffer.begin();
        Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screen.probeStage().draw();
        Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        framebuffer.end();
        writePng(raw, width, height, file);
        framebuffer.dispose();
        System.out.println("[ScreenshotProbe] " + file + " " + width + "x" + height);
    }


    /** Prova de pixel: um joker desenhado pelo PowerCardActor deve ser igual ao PNG nativo. */
    public static void runJokerPixelMatch(DuelGame game) {
        java.util.Map<String, String> files = java.util.Map.ofEntries(
                java.util.Map.entry("naipe_afiado", "Briga_de_espada.png"),
                java.util.Map.entry("ouro_ardente", "Chuva_de_ouro.png"),
                java.util.Map.entry("coracao", "cortem_a_cabeca.png"),
                java.util.Map.entry("pau_potente", "Pau_potente.png"),
                java.util.Map.entry("escada", "Escada_alta.png"),
                java.util.Map.entry("rio", "o_mar_esta_para_fichas.png"),
                java.util.Map.entry("par_mult", "Par_perfeito.png"),
                java.util.Map.entry("trinca_ouro", "tres_e_mais_gostoso.png"),
                java.util.Map.entry("quintuplus", "quintuplos.png"));
        int failures = 0;
        for (java.util.Map.Entry<String, String> entry : files.entrySet()) {
            Pixmap source = new Pixmap(Gdx.files.internal("assets/jokers/" + entry.getValue()));
            int w = source.getWidth();
            int h = source.getHeight();
            PowerCardActor actor = new PowerCardActor(game.assets(),
                    new PowerCard(PowerKind.JOKER, entry.getKey()), false, null);
            actor.setShadowEnabled(false);
            actor.showInstantly();
            actor.setSize(w, h);
            actor.setPosition(0f, 0f);
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            SpriteBatch batch = new SpriteBatch();
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0f, 0f, w, h));
            fb.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            actor.draw(batch, 1f);
            batch.end();
            Pixmap rendered = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
            fb.end();
            int[] equal = new int[4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = source.getPixel(x, y);
                    if (sameRgba(a, rendered.getPixel(x, y))) {
                        equal[0]++;
                    }
                    if (sameRgba(a, rendered.getPixel(w - 1 - x, y))) {
                        equal[1]++;
                    }
                    if (sameRgba(a, rendered.getPixel(x, h - 1 - y))) {
                        equal[2]++;
                    }
                    if (sameRgba(a, rendered.getPixel(w - 1 - x, h - 1 - y))) {
                        equal[3]++;
                    }
                }
            }
            int best = Math.max(Math.max(equal[0], equal[1]), Math.max(equal[2], equal[3]));
            double ratio = best / (double) (w * h);
            System.out.printf("[JokerPixelMatch] %s %.4f%n", entry.getKey(), ratio);
            if (ratio < 0.99) {
                failures++;
            }
            rendered.dispose();
            source.dispose();
            batch.dispose();
            fb.dispose();
        }
        if (failures > 0) {
            throw new IllegalStateException("joker-pixel-match: " + failures + " falha(s)");
        }
    }

    private static boolean sameRgba(int a, int b) {
        int alphaA = (a >>> 24) & 0xff;
        int alphaB = (b >>> 24) & 0xff;
        if (alphaA < 64 || alphaB < 64) {
            return true;
        }
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((a >> shift) & 0xff) - ((b >> shift) & 0xff)) > 2) {
                return false;
            }
        }
        return true;
    }

    /** Filmstrip da resolução: voos dos poderes, tooltips, flips e início da contagem final. */
    private static void renderResolutionFrames(DuelGame game, Path output) {
        int width = 1366;
        int height = 768;
        int seed = resolutionSeed();
        LocalMatchSession session = new LocalMatchSession("QA", powerRules(),
                new Random(seed));
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, snapshot.rules().maxPlay()));
        tickUntil(session, GamePhase.SABOTAGE, 30);
        GameScreen screen = new GameScreen(game, session, null);
        screen.probeOverflow(width, height);
        for (int i = 0; i < 900 && !screen.demoTransitionDone(); i++) {
            screen.probeStage().act(1f / 60f);
        }
        screen.demoSelectPowers(2);
        screen.demoConfirmPowers();
        for (int i = 0; i < 600 && !screen.demoIsResolving(); i++) {
            screen.render(1f / 60f);
        }

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        float elapsed = 0f;
        for (int targetMs : new int[]{0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000}) {
            float target = targetMs / 1000f;
            while (elapsed + 0.0001f < target) {
                float dt = Math.min(1f / 60f, target - elapsed);
                screen.probeStage().act(dt);
                elapsed += dt;
            }
            framebuffer.begin();
            Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screen.probeStage().draw();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
            framebuffer.end();
            Path file = output.resolve("resolucao-t" + targetMs + ".png");
            writePng(raw, width, height, file);
            System.out.println("[ScreenshotProbe] " + file);
        }
        framebuffer.dispose();
    }

    /** Semente em que os dois lados jogam 2 poderes e uma sabotagem muda cartas da mesa. */
    private static int resolutionSeed() {
        for (int seed = 1; seed <= 300; seed++) {
            LocalMatchSession session = new LocalMatchSession("QA", powerRules(),
                    new Random(seed));
            GameSnapshot snapshot = session.snapshot();
            session.submitPlay(snapshot.player(1).hand()
                    .subList(0, snapshot.rules().maxPlay()));
            tickUntil(session, GamePhase.SABOTAGE, 30);
            if (session.snapshot().phase() != GamePhase.SABOTAGE) {
                continue;
            }
            session.lockPowers(List.of(0, 1));
            tickUntil(session, GamePhase.REVEAL, 30);
            GameSnapshot reveal = session.snapshot();
            if (reveal.phase() != GamePhase.REVEAL || reveal.lastOutcome() == null) {
                continue;
            }
            RoundOutcome outcome = reveal.lastOutcome();
            if (outcome.player1Powers().size() == 2 && outcome.player2Powers().size() == 2
                    && outcome.powerEvents().stream()
                    .anyMatch(event -> event.cardsBefore() != null)) {
                return seed;
            }
        }
        return 1;
    }

    /** Filmstrip da queima real da mão: spin, dissolução pelo shader e distribuição dos poderes. */
    private static void renderBurnFrames(DuelGame game, Path output) {
        int width = 1366;
        int height = 768;
        GameScreen screen = new GameScreen(game, powerSession(), null);
        screen.probeOverflow(width, height);
        for (int i = 0; i < 900 && !screen.demoTransitionDone(); i++) {
            screen.probeStage().act(1f / 60f);
        }
        screen.demoReplayBurn();

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        float elapsed = 0f;
        for (int targetMs : new int[]{0, 300, 700, 1100, 1600, 2200, 2800}) {
            float target = targetMs / 1000f;
            while (elapsed + 0.0001f < target) {
                float dt = Math.min(1f / 60f, target - elapsed);
                screen.probeStage().act(dt);
                elapsed += dt;
            }
            framebuffer.begin();
            Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screen.probeStage().draw();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
            framebuffer.end();
            Path file = output.resolve("queima-t" + targetMs + ".png");
            writePng(raw, width, height, file);
            try {
                Files.copy(file, output.resolve("queima-nevoeira-t" + targetMs + ".png"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            System.out.println("[ScreenshotProbe] " + file);
        }
        framebuffer.dispose();
    }

    /** Tela de revelação com a resolução já concluída (slots finais visíveis). */
    private static GameScreen revealScreen(DuelGame game, LocalMatchSession session) {
        GameScreen screen = new GameScreen(game, session, null);
        screen.demoFinishPresentation();
        return screen;
    }

    /** Tela com a mão de poderes já visível e o cursor injetado sobre a primeira carta do tipo. */
    private static GameScreen tooltipScreen(DuelGame game, PowerKind kind) {
        GameScreen screen = new GameScreen(game, powerSession(), null);
        screen.probeOverflow(1366, 768);
        // A mão de poderes só nasce no fim da contagem/queima: avança até ela existir.
        for (int i = 0; i < 900 && !screen.demoTransitionDone(); i++) {
            screen.probeStage().act(1f / 60f);
        }
        screen.probeStage().act(0.6f);
        screen.demoHoverPower(kind);
        screen.probeStage().act(0.2f);
        return screen;
    }

    /** Tooltip sobre um poder jogado nos slots da revelação (lado do oponente). */
    private static GameScreen tooltipSlotScreen(DuelGame game) {
        GameScreen screen = new GameScreen(game, revealPowerMixSession(), null);
        screen.demoFinishPresentation();
        screen.probeOverflow(1366, 768);
        screen.probeStage().act(6f);
        screen.probeStage().act(1f);
        screen.probeStage().act(1f);
        screen.demoHoverPlayedPower(false, 0);
        screen.probeStage().act(0.2f);
        return screen;
    }

    /** Filmstrip do pop-in do tooltip (0,85 -> 1,0 em 0,12 s). */
    private static void renderTooltipPopin(DuelGame game, Path output) {
        int width = 1366;
        int height = 768;
        GameScreen screen = new GameScreen(game, powerSession(), null);
        screen.probeOverflow(width, height);
        screen.probeStage().act(4f);
        screen.probeStage().act(1f);
        screen.probeStage().act(1f);
        screen.probeStage().act(0.2f);
        screen.demoHoverPower(PowerKind.JOKER);

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        float elapsed = 0f;
        for (int targetMs : new int[]{0, 30, 60, 90, 120}) {
            float target = targetMs / 1000f;
            while (elapsed + 0.0001f < target) {
                float dt = Math.min(1f / 60f, target - elapsed);
                screen.probeStage().act(dt);
                elapsed += dt;
            }
            framebuffer.begin();
            Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screen.probeStage().draw();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
            framebuffer.end();
            Path file = output.resolve("tooltip-popin-t" + targetMs + ".png");
            writePng(raw, width, height, file);
            System.out.println("[ScreenshotProbe] " + file);
        }
        framebuffer.dispose();
    }

    /**
     * Filmstrip de uma contagem. Avança SÓ o palco (sem tick da sessão) para que o instantâneo
     * não mude no meio da animação.
     */
    private static void renderCountFrames(DuelGame game, Path output, String name,
                                          LocalMatchSession session, int[] timesMs) {
        int width = 1366;
        int height = 768;
        GameScreen screen = new GameScreen(game, session, null);
        screen.probeOverflow(width, height);

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        float elapsed = 0f;
        for (int targetMs : timesMs) {
            float target = targetMs / 1000f;
            while (elapsed + 0.0001f < target) {
                float dt = Math.min(1f / 60f, target - elapsed);
                screen.probeStage().act(dt);
                elapsed += dt;
            }
            framebuffer.begin();
            Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screen.probeStage().draw();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
            framebuffer.end();
            Path file = output.resolve(name + "-t" + targetMs + ".png");
            writePng(raw, width, height, file);
            System.out.println("[ScreenshotProbe] " + file);
        }
        framebuffer.dispose();
    }

    /**
     * Filmstrip de `poderes-selecao-2`: seleciona duas cartas, dispara a recusa da terceira e
     * grava quadros do tremor horizontal de aproximadamente 0,2 s.
     */
    private static void renderPowerSelectionFrames(DuelGame game, Path output) {
        int width = 1366;
        int height = 768;
        GameScreen screen = new GameScreen(game, powerSession(), null);
        screen.probeOverflow(width, height);
        screen.probeStage().act(1f);
        screen.probeStage().act(1f);
        screen.demoSelectPowers(2);
        screen.demoRefusedPowerClick();

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        int[] captures = {0, 2, 4, 6, 8, 10, 12};
        int frame = 0;
        for (int capture : captures) {
            while (frame < capture) {
                framebuffer.begin();
                screen.render(1f / 60f);
                framebuffer.end();
                frame++;
            }
            framebuffer.begin();
            Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screen.probeStage().draw();
            Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
            framebuffer.end();
            int ms = Math.round(frame * (1000f / 60f));
            Path file = output.resolve("poderes-selecao-2-t" + ms + ".png");
            writePng(raw, width, height, file);
            System.out.println("[ScreenshotProbe] " + file + " frame=" + frame);
        }
        framebuffer.dispose();
    }

    private static void render(BaseScreen screen, int width, int height, Path file) {
        screen.probeOverflow(width, height); // resize + layout
        screen.probeStage().act(1f);
        screen.probeStage().act(1f);
        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        framebuffer.begin();
        Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screen.probeStage().draw();
        Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        framebuffer.end();
        writePng(raw, width, height, file);
        framebuffer.dispose();
        System.out.println("[ScreenshotProbe] " + file + " " + width + "x" + height);
    }

    /**
     * Filmstrip da mesa: dirige o {@link GameScreen} real com uma sessão local de semente fixa e
     * passos determinísticos de 1/60 s, gravando quadros da animação de pouso e virada.
     */
    public static void runFrames(DuelGame game) {
        Path output = Path.of("build", "qa");
        try {
            Files.createDirectories(output);
        } catch (Exception e) {
            System.out.println("[ScreenshotProbe] não foi possível criar " + output + ": " + e);
            return;
        }
        int width = 1366;
        int height = 768;
        LocalMatchSession session = new LocalMatchSession("QA", GameRules.DEFAULT, new Random(7));
        GameScreen screen = new GameScreen(game, session, null);
        screen.probeOverflow(width, height);
        screen.probeStage().act(1f);
        screen.probeStage().act(1f);
        screen.demoSelect(session.snapshot().rules().maxPlay());
        screen.demoSubmit();

        int[] captureFrames = {0, 7, 21, 54, 84, 105};
        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        int frame = 0;
        captureFrame(screen, framebuffer, width, height, output, frame);
        for (int capture : captureFrames) {
            if (capture == 0) {
                continue;
            }
            while (frame < capture) {
                framebuffer.begin();
                screen.render(1f / 60f);
                framebuffer.end();
                frame++;
            }
            captureFrame(screen, framebuffer, width, height, output, frame);
        }
        framebuffer.dispose();
        System.out.println("[ScreenshotProbe] filmstrip em " + output.toAbsolutePath());
    }

    private static void captureFrame(GameScreen screen, FrameBuffer framebuffer, int width,
                                     int height, Path output, int frame) {
        framebuffer.begin();
        Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screen.probeStage().draw();
        Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        framebuffer.end();
        int ms = Math.round(frame * (1000f / 60f));
        Path file = output.resolve("mesa-pouso-t" + ms + ".png");
        writePng(raw, width, height, file);
        System.out.println("[ScreenshotProbe] " + file + " frame=" + frame);
    }

    private static void writePng(Pixmap raw, int width, int height, Path file) {
        Pixmap flipped = new Pixmap(width, height, raw.getFormat());
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.drawPixel(x, height - 1 - y, raw.getPixel(x, y));
            }
        }
        PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), flipped);
        raw.dispose();
        flipped.dispose();
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

    /** Revelação em que os dois lados jogaram, no total, 2 jokers e 2 sabotagens. */
    private static LocalMatchSession revealPowerMixSession() {
        for (int seed = 1; seed <= 2000; seed++) {
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
            if (reveal.phase() != GamePhase.REVEAL || reveal.lastOutcome() == null) {
                continue;
            }
            List<PowerCard> played = new ArrayList<>();
            played.addAll(reveal.lastOutcome().player1Powers());
            played.addAll(reveal.lastOutcome().player2Powers());
            long jokers = played.stream().filter(c -> c.kind() == PowerKind.JOKER).count();
            long sabotages = played.stream()
                    .filter(c -> c.kind() == PowerKind.SABOTAGE).count();
            if (jokers == 2 && sabotages == 2) {
                return session;
            }
        }
        return revealTwoPowersSession();
    }

    private static GameRules powerRules() {
        return new GameRules(300, 8, 5, 3, 4, 3, 2);
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
