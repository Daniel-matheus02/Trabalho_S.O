package com.game.networking;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prova de rede REAL: servidor autoritativo em porta efêmera e dois {@link GameClient} reais
 * dirigindo {@link NetworkMatchSession}. Trinta partidas completas com poderes, incluindo uma
 * revanche e uma queda de conexão com reconexão no meio da rodada.
 *
 * <p>Além de jogar, o teste compara cada instantâneo recebido com o instantâneo autoritativo do
 * servidor (via ida e volta JSON) e faz round-trip de todos os payloads de resultado.</p>
 */
class NetworkMatchStressTest {

    private static final int MATCHES = 30;
    private static final long MATCH_TIMEOUT_MS = 20_000L;
    private static final long STEP_TIMEOUT_MS = 5_000L;

    private final MessageCodec codec = new MessageCodec();
    private final List<GameClient> clients = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private boolean reconnectInFlight;
    private boolean reconnected;

    private GameServer server;
    private CapturingHandler logs;

    @BeforeEach
    void startServer() throws Exception {
        server = new GameServer(new ServerConfig("127.0.0.1", 0, 64, 2, 120, 60), 20261115L);
        server.start();
        logs = new CapturingHandler();
        Logger.getLogger("com.game").addHandler(logs);
    }

    @AfterEach
    void stopEverything() {
        Logger.getLogger("com.game").removeHandler(logs);
        for (GameClient client : clients) {
            client.close();
        }
        clients.clear();
        if (server != null) {
            server.close();
        }
        assertTrue(problems.isEmpty(), () -> "Falhas registradas:\n" + String.join("\n", problems));
        assertTrue(logs.throwables.isEmpty(),
                () -> "Exceções registradas nos logs:\n" + describe(logs.throwables));
    }

    @Test
    @DisplayName("trinta partidas completas por rede, com revanche e reconexão, sem exceção e com estado idêntico")
    void thirtySeededMatchesOverRealNetwork() throws Exception {
        for (int match = 0; match < MATCHES; match++) {
            // A primeira sessão também prova a revanche; a segunda prova uma queda de LAN no meio
            // da rodada com reconexão pelo token.
            playOneSession(match, match == 0, match == 1);
        }
    }

    private void playOneSession(int match, boolean withRematch, boolean withReconnect) throws Exception {
        GameClient anaClient = newClient();
        SessionInfoPayload info = anaClient.createSession("Ana" + match).get(5, TimeUnit.SECONDS);
        assertEquals(1, info.playerId());
        GameClient betoClient = newClient();
        SessionInfoPayload joined = betoClient.joinSession(info.sessionCode(), "Beto" + match, null)
                .get(5, TimeUnit.SECONDS);
        assertEquals(2, joined.playerId());

        NetworkMatchSession ana = new NetworkMatchSession(anaClient);
        NetworkMatchSession beto = new NetworkMatchSession(betoClient);
        GameSession authoritative = server.sessions().sessionByCode(info.sessionCode());
        assertNotNull(authoritative, "a sessão autoritativa deve existir no servidor");

        awaitSnapshot(ana, s -> s.phase() == GamePhase.BUILD, 5_000, "início do jogo (Ana)");
        awaitSnapshot(beto, s -> s.phase() == GamePhase.BUILD, 5_000, "início do jogo (Beto)");

        BotBrain anaBot = new BotBrain(new ScoringEngine(), 5);
        BotBrain betoBot = new BotBrain(new ScoringEngine(), 5);
        BotDriver anaDriver = new BotDriver(ana, anaBot, anaClient, authoritative);
        BotDriver betoDriver = new BotDriver(beto, betoBot, betoClient, authoritative);
        anaDriver.dropLinkOnRound = withReconnect ? 2 : -1;

        playGame(anaDriver, betoDriver);
        if (withRematch) {
            requestRematches(anaDriver, betoDriver);
            playGame(anaDriver, betoDriver);
        }
        if (withReconnect) {
            assertTrue(reconnected,
                    "a reconexão no meio da rodada precisa ter acontecido de fato");
        }
        assertTrue(ana.snapshot().phase() == GamePhase.GAME_OVER
                        && beto.snapshot().phase() == GamePhase.GAME_OVER,
                "as duas visões devem terminar em GAME_OVER");
        assertEquals(ana.snapshot().winner(), beto.snapshot().winner(),
                "os dois clientes devem concordar sobre o vencedor");
        verifyConsistency(ana, beto, authoritative);
        drainEvents(ana, beto);
    }

    /** Joga até GAME_OVER com os dois bots, validando o estado a cada mensagem consumida. */
    private void playGame(BotDriver ana, BotDriver beto) {
        long deadline = System.currentTimeMillis() + MATCH_TIMEOUT_MS;
        long lastProgressVersion = -1;
        long lastProgressAt = System.currentTimeMillis();
        int rounds = 0;

        while (true) {
            drainEvents(ana.session(), beto.session());
            dropLinkIfScheduled(ana, beto);
            ana.step();
            beto.step();
            verifyConsistency(ana.session(), beto.session(), ana.authoritative());

            GameSnapshot a = ana.session().snapshot();
            GameSnapshot b = beto.session().snapshot();
            if (a == null || b == null) {
                if (System.currentTimeMillis() > deadline) {
                    fail("Sem instantâneo inicial:\n" + dump(authoritative(ana), a, b));
                }
                sleep(5);
                continue;
            }
            long version = Math.max(a.version(), b.version());
            if (version != lastProgressVersion) {
                lastProgressVersion = version;
                lastProgressAt = System.currentTimeMillis();
                rounds = Math.max(rounds, a.round());
            }
            if (a.phase() == GamePhase.GAME_OVER && b.phase() == GamePhase.GAME_OVER) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastProgressAt > MATCH_TIMEOUT_MS) {
                fail("20 s sem progresso na partida (rodada " + rounds + "):\n"
                        + dump(authoritative(ana), a, b));
            }
            if (now > deadline) {
                deadline = now + MATCH_TIMEOUT_MS;
            }
            sleep(1);
        }
    }

    /** Derruba de propósito o link de um dos jogadores, uma única vez, para provar a reconexão. */
    private void dropLinkIfScheduled(BotDriver ana, BotDriver beto) {
        for (BotDriver driver : List.of(ana, beto)) {
            if (driver.dropLinkOnRound < 0) {
                continue;
            }
            GameSnapshot state = driver.session.snapshot();
            if (state == null || state.round() < driver.dropLinkOnRound
                    || state.phase() == GamePhase.GAME_OVER) {
                continue;
            }
            driver.dropLinkOnRound = -1;
            reconnectInFlight = true;
            reconnected = false;
            driver.client.dropConnectionForTest();
            return;
        }
    }

    private void requestRematches(BotDriver ana, BotDriver beto) {
        long deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS;
        boolean anaAsked = false;
        boolean betoAsked = false;
        while (System.currentTimeMillis() < deadline) {
            drainEvents(ana.session(), beto.session());
            if (!anaAsked) {
                ana.session().requestRematch();
                anaAsked = true;
            }
            if (!betoAsked) {
                beto.session().requestRematch();
                betoAsked = true;
            }
            GameSnapshot a = ana.session().snapshot();
            GameSnapshot b = beto.session().snapshot();
            if (a != null && b != null && a.phase() == GamePhase.BUILD
                    && b.phase() == GamePhase.BUILD && a.round() == 1) {
                return;
            }
            sleep(2);
        }
        fail("A revanche não começou:\n" + dump(authoritative(ana),
                ana.session().snapshot(), beto.session().snapshot()));
    }

    private GameSession authoritative(BotDriver driver) {
        return driver.authoritative();
    }

    // ---------------------------------------------------------------- consistência

    /**
     * Compara cada instantâneo do cliente com o instantâneo autoritativo do servidor para aquele
     * visualizador, ambos depois de uma ida e volta JSON. Versões diferentes são permitidas (a
     * mensagem ainda pode estar em trânsito); versões iguais precisam ser idênticas.
     */
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
            problems.add("cliente " + viewer + " está à frente do servidor: cliente v"
                    + clientSnapshot.version() + " servidor v" + serverSnapshot.version());
            return;
        }
        assertCodecRoundTrip(clientSnapshot, "cliente " + viewer);
        if (serverSnapshot.version() != clientSnapshot.version()) {
            return;
        }
        GameSnapshot serverRoundTrip = roundTrip(serverSnapshot);
        assertEquals(serverRoundTrip, clientSnapshot,
                () -> "instantâneo divergente para o visualizador " + viewer + " v"
                        + serverSnapshot.version());
    }

    private void assertCodecRoundTrip(GameSnapshot snapshot, String owner) {
        GameSnapshot restored = roundTrip(snapshot);
        assertEquals(snapshot, restored, "ida e volta JSON divergente (" + owner + " v"
                + snapshot.version() + ")");
        if (snapshot.lastOutcome() != null) {
            RoundResultPayload payload = new RoundResultPayload(snapshot.lastOutcome(),
                    snapshot.player1().hp(), snapshot.player2().hp());
            Envelope envelope = codec.message(MessageType.ROUND_RESULT, snapshot.sessionId(),
                    1, payload);
            RoundResultPayload back = codec.payload(codec.decode(codec.encode(envelope)),
                    RoundResultPayload.class);
            assertEquals(payload, back, "RoundResultPayload não sobrevive à ida e volta JSON");
            assertEquals(snapshot.lastOutcome().powerEvents(), back.outcome().powerEvents());
            assertEquals(snapshot.lastOutcome().player1Score().steps(),
                    back.outcome().player1Score().steps());
            assertEquals(snapshot.lastOutcome().player2Score().steps(),
                    back.outcome().player2Score().steps());
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
            case ClientEvent.ConnectionLost lost -> {
                if (!reconnectInFlight) {
                    problems.add("conexão perdida: " + lost.reason());
                }
            }
            case ClientEvent.Reconnecting reconnecting -> {
                if (!reconnectInFlight) {
                    problems.add("reconectando (" + reconnecting.attempt() + ")");
                }
            }
            case ClientEvent.Reconnected ok -> {
                reconnected = true;
                reconnectInFlight = false;
            }
            case ClientEvent.ReconnectFailed failed ->
                    problems.add("reconexão falhou: " + failed.reason());
            case ClientEvent.RoundResolved resolved -> {
                RoundResultPayload payload = resolved.payload();
                Envelope envelope = codec.message(MessageType.ROUND_RESULT,
                        session.snapshot() == null ? null : session.snapshot().sessionId(),
                        session.myPlayerId(), payload);
                RoundResultPayload restored = codec.payload(codec.decode(codec.encode(envelope)),
                        RoundResultPayload.class);
                assertEquals(payload, restored, "ROUND_RESULT corrompido na ida e volta JSON");
            }
            default -> {
                // Demais eventos não carregam estado persistido.
            }
        }
    }

    // ---------------------------------------------------------------- bot

    private final class BotDriver {
        private final NetworkMatchSession session;
        private final BotBrain brain;
        private final GameClient client;
        private final GameSession authoritative;
        private long actedVersion = -1;
        /** Rodada em que o link cai de propósito (-1 = nunca). */
        private int dropLinkOnRound = -1;

        BotDriver(NetworkMatchSession session, BotBrain brain, GameClient client,
                  GameSession authoritative) {
            this.session = session;
            this.brain = brain;
            this.client = client;
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
            // Durante a queda de link o bot não age: o servidor segue autoritativo e o jogador
            // volta a jogar quando a reconexão entrega o estado.
            if (!client.isConnected()) {
                return;
            }
            int me = session.myPlayerId();
            PlayerView view = state.player(me);
            switch (state.phase()) {
                case BUILD -> {
                    if (state.currentPlayer() == me && !view.submitted()) {
                        actedVersion = state.version();
                        List<Card> play = brain.choosePlay(view.hand(), state.globalModifierId());
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
                case GAME_OVER -> {
                    // A revanche é pedida explicitamente pelo teste, não pelo driver.
                }
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- utilidades

    private GameClient newClient() throws Exception {
        GameClient client = new GameClient();
        clients.add(client);
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client;
    }

    private static void awaitSnapshot(NetworkMatchSession session, Predicate<GameSnapshot> predicate,
                                      long timeoutMs, String what) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        GameSnapshot last = null;
        while (System.currentTimeMillis() < deadline) {
            last = session.snapshot();
            if (last != null && predicate.test(last)) {
                return;
            }
            sleep(5);
        }
        fail("Tempo esgotado aguardando " + what + "; último: "
                + (last == null ? "nenhum" : last.phase() + " v" + last.version()));
    }

    private static String dump(GameSession session, GameSnapshot a, GameSnapshot b) {
        GameSnapshot serverA = session == null ? null : session.snapshotFor(1);
        return "servidor(1)=" + summary(serverA) + "\ncliente1=" + summary(a)
                + "\ncliente2=" + summary(b);
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
            java.io.StringWriter writer = new java.io.StringWriter();
            throwable.printStackTrace(new java.io.PrintWriter(writer));
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

    /** Captura qualquer exceção registrada pelo cliente ou pelo servidor durante o teste. */
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
}
