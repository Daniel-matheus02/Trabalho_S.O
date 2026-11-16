package com.game.qa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.client.screens.GameScreen;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.SessionInfoPayload;
import com.game.server.ServerConfig;
import com.game.server.network.GameServer;
import com.game.server.session.GameSession;
import com.game.launcher.DuelGame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prova visual de rede: DUAS {@link GameScreen} reais, cada uma com sua
 * {@link NetworkMatchSession}, contra um servidor embutido. Joga três rodadas com entradas
 * roteirizadas (seleciona 5, JOGAR MÃO, seleciona os poderes, CONFIRMAR) e grava um PNG de cada
 * janela em cada fase, em 1365x695, sob {@code build/qa}.
 */
public final class NetplayUiProbe {

    private static final int WIDTH = 1365;
    private static final int HEIGHT = 695;
    private static final int ROUNDS = 3;
    private static final long TIMEOUT_MS = 30_000L;

    private NetplayUiProbe() {
    }

    public static int run(DuelGame game) {
        Path output = Path.of("build", "qa");
        List<String> problems = new ArrayList<>();
        GameServer server = null;
        List<GameClient> clients = new ArrayList<>();
        try {
            Files.createDirectories(output);
            server = new GameServer(new ServerConfig("127.0.0.1", 0, 8, 2, 120, 60), 7L);
            server.start();
            System.out.println("[NetplayUI] servidor em 127.0.0.1:" + server.boundPort());

            GameClient anaClient = new GameClient();
            clients.add(anaClient);
            anaClient.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            SessionInfoPayload info = anaClient.createSession("Ana").get(5, TimeUnit.SECONDS);
            GameClient betoClient = new GameClient();
            clients.add(betoClient);
            betoClient.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            betoClient.joinSession(info.sessionCode(), "Beto", null).get(5, TimeUnit.SECONDS);

            NetworkMatchSession anaSession = new NetworkMatchSession(anaClient);
            NetworkMatchSession betoSession = new NetworkMatchSession(betoClient);
            GameScreen ana = new GameScreen(game, anaSession, null);
            GameScreen beto = new GameScreen(game, betoSession, null);
            ana.probeOverflow(WIDTH, HEIGHT);
            beto.probeOverflow(WIDTH, HEIGHT);
            GameSession authoritative = server.sessions().sessionByCode(info.sessionCode());

            drive(ana, beto, anaSession, betoSession, authoritative, output, problems);
        } catch (Exception e) {
            problems.add("erro fatal: " + e);
            e.printStackTrace();
        } finally {
            for (GameClient client : clients) {
                client.close();
            }
            if (server != null) {
                server.close();
            }
            System.out.println("[NetplayUI] PNGs em " + output.toAbsolutePath());
        }
        if (!problems.isEmpty()) {
            System.out.println("[NetplayUI] FALHOU");
            for (String problem : problems) {
                System.out.println("  - " + problem);
            }
            return 1;
        }
        System.out.println("[NetplayUI] PASSOU (3 rodadas, duas janelas reais)");
        return 0;
    }

    private static void drive(GameScreen ana, GameScreen beto, NetworkMatchSession anaSession,
                              NetworkMatchSession betoSession, GameSession authoritative,
                              Path output, List<String> problems) throws Exception {
        float delta = 1f / 60f;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        String lastSignature = "";
        boolean[] submitted = {false, false};
        int captures = 0;

        while (System.currentTimeMillis() < deadline) {
            ana.render(delta);
            beto.render(delta);
            drainErrors(anaSession, betoSession, problems);
            GameSnapshot a = anaSession.snapshot();
            GameSnapshot b = betoSession.snapshot();
            if (a == null || b == null) {
                Thread.sleep(2);
                continue;
            }

            String signature = a.round() + ":" + a.phase() + ":" + b.phase();
            if (!signature.equals(lastSignature)) {
                lastSignature = signature;
                capture(ana, output.resolve("netplay-ui-ana-" + label(a) + "-" + WIDTH + "x"
                        + HEIGHT + ".png"));
                capture(beto, output.resolve("netplay-ui-beto-" + label(b) + "-" + WIDTH + "x"
                        + HEIGHT + ".png"));
                captures += 2;
                submitted[0] = false;
                submitted[1] = false;
            }

            // Entradas roteirizadas, exatamente as do jogador: selecionar 5 e JOGAR MÃO.
            script(ana, anaSession, a, 1, submitted);
            script(beto, betoSession, b, 2, submitted);

            if (a.phase() == GamePhase.GAME_OVER || a.round() > ROUNDS) {
                break;
            }
            Thread.sleep(2);
        }

        if (captures == 0) {
            problems.add("nenhuma captura foi produzida");
        }
        GameSnapshot a = anaSession.snapshot();
        GameSnapshot b = betoSession.snapshot();
        if (a == null || b == null || a.round() < ROUNDS) {
            problems.add("as três rodadas não foram concluídas: ana=" + summary(a)
                    + " beto=" + summary(b));
        }
        if (authoritative != null) {
            GameSnapshot serverA = authoritative.snapshotFor(1);
            GameSnapshot serverB = authoritative.snapshotFor(2);
            if (serverA != null && a != null && serverA.version() == a.version()
                    && !serverA.equals(a)) {
                problems.add("janela da Ana divergiu do servidor em v" + a.version());
            }
            if (serverB != null && b != null && serverB.version() == b.version()
                    && !serverB.equals(b)) {
                problems.add("janela do Beto divergiu do servidor em v" + b.version());
            }
        }
    }

    /** Mesma sequência de cliques do humano; cada ação só é tentada uma vez por estado. */
    private static void script(GameScreen screen, NetworkMatchSession session, GameSnapshot state,
                               int playerId, boolean[] submitted) throws InterruptedException {
        switch (state.phase()) {
            case BUILD -> {
                if (state.isTurnOf(playerId) && !state.player(playerId).submitted()
                        && !submitted[playerId - 1]) {
                    screen.demoSelect(state.rules().maxPlay());
                    screen.demoSubmit();
                    submitted[playerId - 1] = true;
                }
            }
            case SABOTAGE -> {
                if (!state.player(playerId).powersLocked()) {
                    // A contagem da mesa precisa entregar a mão antes do clique.
                    screen.demoFinishPresentation();
                    screen.demoSelectPowers(state.rules().maxPowersPlayed());
                    screen.demoConfirmPowers();
                }
            }
            case REVEAL -> {
                if (!state.player(playerId).ready()) {
                    screen.demoFinishPresentation();
                    screen.demoAdvance();
                }
            }
            default -> {
            }
        }
    }

    private static void drainErrors(NetworkMatchSession ana, NetworkMatchSession beto,
                                    List<String> problems) {
        for (NetworkMatchSession session : List.of(ana, beto)) {
            ClientEvent event;
            while ((event = session.pollEvent()) != null) {
                if (event instanceof ClientEvent.ErrorReceived error && problems.size() < 12) {
                    problems.add("erro de protocolo: " + error.code() + ": " + error.message());
                }
            }
        }
    }

    private static void capture(GameScreen screen, Path file) {
        screen.probeOverflow(WIDTH, HEIGHT);
        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        framebuffer.begin();
        Gdx.gl.glClearColor(0.05f, 0.18f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screen.probeStage().draw();
        Pixmap raw = ScreenUtils.getFrameBufferPixmap(0, 0, WIDTH, HEIGHT);
        framebuffer.end();
        Pixmap flipped = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                flipped.drawPixel(x, HEIGHT - 1 - y, raw.getPixel(x, y));
            }
        }
        PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), flipped);
        raw.dispose();
        flipped.dispose();
        framebuffer.dispose();
        System.out.println("[NetplayUI] " + file);
    }

    private static String label(GameSnapshot snapshot) {
        if (snapshot == null) {
            return "espera";
        }
        return switch (snapshot.phase()) {
            case BUILD -> snapshot.player1().submitted() || snapshot.player2().submitted()
                    ? "rodada" + snapshot.round() + "-jogada"
                    : "rodada" + snapshot.round() + "-mao";
            case SABOTAGE -> "rodada" + snapshot.round() + "-poderes";
            case REVEAL -> "rodada" + snapshot.round() + "-revelacao";
            case GAME_OVER -> "fim";
            default -> "rodada" + snapshot.round() + "-" + snapshot.phase();
        };
    }

    private static String summary(GameSnapshot snapshot) {
        return snapshot == null ? "nenhum"
                : snapshot.phase() + " rodada=" + snapshot.round() + " v=" + snapshot.version();
    }
}
