package com.game.qa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.game.client.controller.LocalMatchSession;
import com.game.client.screens.BaseScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.launcher.DuelGame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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
        GameScreen selection = new GameScreen(game, new LocalMatchSession("QA"), null);
        selection.demoSelect(2);
        screens.put("selecao-cartas", selection);
        screens.put("loja", new GameScreen(game, shopSession(), null));
        screens.put("configuracoes", new SettingsScreen(game));

        int[][] sizes = {
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
        System.out.println("[ScreenshotProbe] PNGs em " + output.toAbsolutePath());
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
        framebuffer.dispose();
        System.out.println("[ScreenshotProbe] " + file + " " + width + "x" + height);
    }

    private static LocalMatchSession shopSession() {
        LocalMatchSession session = new LocalMatchSession("QA");
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, 2));
        tickUntil(session, GamePhase.SABOTAGE, 30);
        if (session.snapshot().phase() == GamePhase.SABOTAGE) {
            session.passSabotage();
        }
        tickUntil(session, GamePhase.REVEAL, 30);
        session.advance();
        tickUntil(session, GamePhase.SHOP, 30);
        return session;
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
