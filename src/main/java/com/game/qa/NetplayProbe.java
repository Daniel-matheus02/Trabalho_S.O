package com.game.qa;

import com.game.ai.BotBrain;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.core.model.Card;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.RoundResultPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.net.protocol.StatePayload;
import com.game.server.ServerConfig;
import com.game.server.network.GameServer;
import com.game.server.session.GameSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Prova de rede REAL executada por {@code --probe=netplay}: sobe o servidor autoritativo em uma
 * porta efêmera em 127.0.0.1, conecta dois {@link GameClient} de verdade e joga 20 partidas
 * semeadas completas com {@link BotBrain} dos dois lados, com revanche na primeira.
 *
 * <p>É o mesmo cenário do teste {@code NetworkMatchStressTest}, mas executável fora do Gradle
 * para reproduzir uma falha em uma máquina real. Falha com código de saída 1 e imprime o estado
 * dos dois lados quando algo diverge ou o progresso para por 20 s.</p>
 */
public final class NetplayProbe {

    public static final int MATCHES = 20;
    public static final long TIMEOUT_MS = 20_000L;

    private final MessageCodec codec = new MessageCodec();
    private final List<String> problems = new ArrayList<>();
    private final List<GameClient> clients = new ArrayList<>();
    private GameServer server;

    private NetplayProbe() {
    }

    /** Executa a prova e devolve o código de saída (0 = tudo verde). */
    public static int run(String[] args) {
        NetplayProbe probe = new NetplayProbe();
        return probe.execute();
    }

    private int execute() {
        CapturingHandler logs = new CapturingHandler();
        Logger.getLogger("com.game").addHandler(logs);
        try {
            server = new GameServer(new ServerConfig("127.0.0.1", 0, 64, 2, 120, 60),
                    20261115L);
            server.start();
            System.out.println("[Netplay] servidor em 127.0.0.1:" + server.boundPort());
            for (int match = 0; match < MATCHES; match++) {
                playOneSession(match, match == 0);
                System.out.println("[Netplay] partida " + (match + 1) + "/" + MATCHES + " OK");
            }
            if (!logs.throwables.isEmpty()) {
                problems.add("exceções registradas nos logs:\n" + describe(logs.throwables));
            }
            if (!problems.isEmpty()) {
                System.out.println("[Netplay] FALHOU");
                for (String problem : problems) {
                    System.out.println("  - " + problem);
                }
                return 1;
            }
            System.out.println("[Netplay] PASSOU: " + MATCHES
                    + " partidas reais, sem exceção, estado idêntico");
            return 0;
        } catch (Exception e) {
            System.out.println("[Netplay] ERRO FATAL: " + e);
            e.printStackTrace();
            return 1;
        } finally {
            for (GameClient client : clients) {
                client.close();
            }
            clients.clear();
            if (server != null) {
                server.close();
            }
            Logger.getLogger("com.game").removeHandler(logs);
        }
    }

    private void playOneSession(int match, boolean withRematch) throws Exception {
        GameClient anaClient = newClient();
        SessionInfoPayload info = anaClient.createSession("Ana" + match).get(5, TimeUnit.SECONDS);
        GameClient betoClient = newClient();
        SessionInfoPayload joined = betoClient.joinSession(info.sessionCode(), "Beto" + match, null)
                .get(5, TimeUnit.SECONDS);
        if (joined.playerId() != 2) {
            problems.add("o segundo jogador recebeu o assento " + joined.playerId());
        }
        NetworkMatchSession ana = new NetworkMatchSession(anaClient);
        NetworkMatchSession beto = new NetworkMatchSession(betoClient);
        GameSession authoritative = server.sessions().sessionByCode(info.sessionCode());
        if (authoritative == null) {
            problems.add("a sessão " + info.sessionCode() + " não existe no servidor");
            return;
        }
        awaitSnapshot(ana, s -> s.phase() == GamePhase.BUILD, "início (Ana)", authoritative, 1);
        awaitSnapshot(beto, s -> s.phase() == GamePhase.BUILD, "início (Beto)", authoritative, 2);

        BotDriver anaDriver = new BotDriver(ana, new BotBrain(new ScoringEngine(), 5),
                authoritative);
        BotDriver betoDriver = new BotDriver(beto, new BotBrain(new ScoringEngine(), 5),
                authoritative);
        playGame(anaDriver, betoDriver);
        if (withRematch) {
            requestRematches(ana, beto, authoritative);
            playGame(anaDriver, betoDriver);
        }
    }

    private void playGame(BotDriver ana, BotDriver beto) {
        long lastProgressAt = System.currentTimeMillis();
        long lastVersion = -1;
        int lastRound = 0;
        while (true) {
            drainEvents(ana.session(), beto.session());
            ana.step();
            beto.step();
            verifyConsistency(ana.session(), beto.session(), ana.authoritative());

            GameSnapshot a = ana.session().snapshot();
            GameSnapshot b = beto.session().snapshot();
            if (a != null && b != null) {
                long version = Math.max(a.version(), b.version());
                if (version != lastVersion) {
                    lastVersion = version;
                    lastRound = Math.max(lastRound, a.round());
                    lastProgressAt = System.currentTimeMillis();
                }
                if (a.phase() == GamePhase.GAME_OVER && b.phase() == GamePhase.GAME_OVER) {
                    return;
                }
            }
            if (System.currentTimeMillis() - lastProgressAt > TIMEOUT_MS) {
                fail("20 s sem progresso na rodada " + lastRound + "\n"
                        + dump(ana.authoritative(), a, b));
                return;
            }
            sleep(1);
        }
    }

    private void requestRematches(NetworkMatchSession ana, NetworkMatchSession beto,
                                  GameSession authoritative) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        boolean anaAsked = false;
        boolean betoAsked = false;
        while (System.currentTimeMillis() < deadline) {
            drainEvents(ana, beto);
            if (!anaAsked) {
                ana.requestRematch();
                anaAsked = true;
            }
            if (!betoAsked) {
                beto.requestRematch();
                betoAsked = true;
            }
            GameSnapshot a = ana.snapshot();
            GameSnapshot b = beto.snapshot();
            if (a != null && b != null && a.phase() == GamePhase.BUILD && b.phase() == GamePhase.BUILD
                    && a.round() == 1) {
                return;
            }
            sleep(2);
        }
        fail("a revanche não começou\n" + dump(authoritative, ana.snapshot(), beto.snapshot()));
    }

    private void verifyConsistency(NetworkMatchSession ana, NetworkMatchSession beto,
                                   GameSession session) {
        verifyView(ana, 1, session);
        verifyView(beto, 2, session);
    }

    private void verifyView(NetworkMatchSession client, int viewer, GameSession session) {
        GameSnapshot clientSnapshot = client.snapshot();
        if (clientSnapshot == null) {
            return;
        }
        GameSnapshot serverSnapshot = session.snapshotFor(viewer);
        if (serverSnapshot == null) {
            return;
        }
        if (serverSnapshot.version() < clientSnapshot.version()) {
            problems.add("cliente " + viewer + " à frente do servidor: cliente v"
                    + clientSnapshot.version() + " servidor v" + serverSnapshot.version());
            return;
        }
        assertCodecRoundTrip(clientSnapshot, "cliente " + viewer);
        if (serverSnapshot.version() == clientSnapshot.version()
                && !roundTrip(serverSnapshot).equals(clientSnapshot)) {
            problems.add("instantâneo divergente para o visualizador " + viewer + " v"
                    + serverSnapshot.version());
        }
    }

    private void assertCodecRoundTrip(GameSnapshot snapshot, String owner) {
        if (!roundTrip(snapshot).equals(snapshot)) {
            problems.add("ida e volta JSON divergente (" + owner + " v"
                    + snapshot.version() + ")");
        }
        if (snapshot.lastOutcome() != null) {
            RoundResultPayload payload = new RoundResultPayload(snapshot.lastOutcome(),
                    snapshot.player1().hp(), snapshot.player2().hp());
            Envelope envelope = codec.message(MessageType.ROUND_RESULT, snapshot.sessionId(),
                    1, payload);
            RoundResultPayload back = codec.payload(codec.decode(codec.encode(envelope)),
                    RoundResultPayload.class);
            if (!payload.equals(back)) {
                problems.add("ROUND_RESULT não sobrevive à ida e volta JSON (" + owner + ")");
            }
        }
    }

    private GameSnapshot roundTrip(GameSnapshot snapshot) {
        Envelope envelope = codec.message(MessageType.GAME_STATE_UPDATE, snapshot.sessionId(),
                snapshot.player1().id(), new StatePayload(snapshot));
        return codec.payload(codec.decode(codec.encode(envelope)), StatePayload.class).snapshot();
    }

    private void drainEvents(NetworkMatchSession ana, NetworkMatchSession beto) {
        for (NetworkMatchSession session : List.of(ana, beto)) {
            ClientEvent event;
            while ((event = session.pollEvent()) != null) {
                handleEvent(session, event);
            }
        }
    }

    private void handleEvent(NetworkMatchSession session, ClientEvent event) {
        switch (event) {
            case ClientEvent.ErrorReceived error ->
                    problems.add("erro de protocolo: " + error.code() + ": " + error.message());
            case ClientEvent.ConnectionLost lost ->
                    problems.add("conexão perdida: " + lost.reason());
            case ClientEvent.Reconnecting reconnecting ->
                    problems.add("reconectando (" + reconnecting.attempt() + ")");
            case ClientEvent.ReconnectFailed failed ->
                    problems.add("reconexão falhou: " + failed.reason());
            default -> {
            }
        }
    }

    private void awaitSnapshot(NetworkMatchSession session, Predicate<GameSnapshot> predicate,
                               String what, GameSession authoritative, int viewer) {
        long deadline = System.currentTimeMillis() + 5_000L;
        GameSnapshot last = null;
        while (System.currentTimeMillis() < deadline) {
            last = session.snapshot();
            if (last != null && predicate.test(last)) {
                return;
            }
            sleep(5);
        }
        fail("tempo esgotado aguardando " + what + "; último: "
                + (last == null ? "nenhum" : last.phase() + " v" + last.version())
                + "\n" + dump(authoritative, session.snapshot(), session.snapshot()));
    }

    private GameClient newClient() throws Exception {
        GameClient client = new GameClient();
        clients.add(client);
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client;
    }

    private void fail(String message) {
        problems.add(message);
    }

    private static String dump(GameSession session, GameSnapshot a, GameSnapshot b) {
        return "servidor(1)=" + summary(session == null ? null : session.snapshotFor(1))
                + "\ncliente1=" + summary(a) + "\ncliente2=" + summary(b);
    }

    private static String summary(GameSnapshot snapshot) {
        if (snapshot == null) {
            return "nenhum";
        }
        return snapshot.phase() + " rodada=" + snapshot.round() + " turno="
                + snapshot.currentPlayer() + " v=" + snapshot.version() + " hp1="
                + snapshot.player1().hp() + " hp2=" + snapshot.player2().hp();
    }

    private static String describe(List<Throwable> throwables) {
        StringBuilder text = new StringBuilder();
        for (Throwable throwable : throwables) {
            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            text.append(writer).append('\n');
        }
        return text.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<Throwable> throwables = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getThrown() != null) {
                throwables.add(record.getThrown());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private final class BotDriver {
        private final NetworkMatchSession session;
        private final BotBrain brain;
        private final GameSession authoritative;
        private long actedVersion = -1;

        BotDriver(NetworkMatchSession session, BotBrain brain, GameSession authoritative) {
            this.session = session;
            this.brain = brain;
            this.authoritative = authoritative;
        }

        NetworkMatchSession session() {
            return session;
        }

        GameSession authoritative() {
            return authoritative;
        }

        void step() {
            GameSnapshot state = session.snapshot();
            if (state == null || state.version() == actedVersion) {
                return;
            }
            int me = session.myPlayerId();
            PlayerView view = state.player(me);
            switch (state.phase()) {
                case BUILD -> {
                    if (state.currentPlayer() == me && !view.submitted()) {
                        actedVersion = state.version();
                        List<Card> play = brain.choosePlay(view.hand(),
                                state.globalModifierId());
                        session.submitPlay(play);
                    }
                }
                case SABOTAGE -> {
                    if (!view.powersLocked()) {
                        actedVersion = state.version();
                        session.lockPowers(brain.choosePowers(state, me));
                    }
                }
                case REVEAL -> {
                    if (!view.ready()) {
                        actedVersion = state.version();
                        session.advance();
                    }
                }
                default -> {
                }
            }
        }
    }
}
