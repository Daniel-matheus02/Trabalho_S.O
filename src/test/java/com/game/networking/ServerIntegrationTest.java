package com.game.networking;

import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.core.model.Card;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.server.ServerConfig;
import com.game.server.network.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Teste de ponta a ponta sobre sockets TCP reais: dois clientes, um servidor autoritativo, uma
 * rodada completa com draft e segunda rodada, além da validação de comandos fora do turno.
 */
class ServerIntegrationTest {

    private GameServer server;
    private final List<GameClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = new GameServer(new ServerConfig("127.0.0.1", 0, 4, 2, 30, 30));
        server.start();
    }

    @AfterEach
    void stopEverything() {
        for (GameClient client : clients) {
            client.close();
        }
        clients.clear();
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("dois clientes desktop jogam uma rodada, fazem o draft de um curinga e iniciam a segunda rodada")
    void fullNetworkRound() throws Exception {
        GameClient ana = newClient();
        SessionInfoPayload info = ana.createSession("Ana").get(5, TimeUnit.SECONDS);
        assertEquals(1, info.playerId());
        assertNotNull(info.sessionCode());
        assertEquals(6, info.sessionCode().length());

        GameClient beto = newClient();
        SessionInfoPayload joined = beto.joinSession(info.sessionCode(), "Beto", null)
                .get(5, TimeUnit.SECONDS);
        assertEquals(2, joined.playerId());

        GameSnapshot anaState = await(ana, s -> s.phase() == GamePhase.BUILD, 5_000);
        GameSnapshot betoState = await(beto, s -> s.phase() == GamePhase.BUILD, 5_000);

        // Informação oculta: cada cliente recebe apenas as próprias cartas.
        assertEquals(8, anaState.player(1).hand().size());
        assertTrue(anaState.player(2).hand().isEmpty());
        assertEquals(8, anaState.player(2).handCount());
        assertEquals(8, betoState.player(2).hand().size());
        assertTrue(betoState.player(1).hand().isEmpty());
        assertEquals("Ana", anaState.player(1).name());
        assertEquals("Beto", anaState.player(2).name());

        // Agir fora do turno é rejeitado pelo servidor.
        String illegal = betoState.player(2).hand().get(0).code();
        beto.sendAction(ActionPayload.play(List.of(illegal)));
        ClientEvent.ErrorReceived error = awaitError(beto, "NOT_YOUR_TURN", 5_000);
        assertEquals("NOT_YOUR_TURN", error.code());

        // O jogador 1 envia três cartas.
        List<String> play1 = anaState.player(1).hand().subList(0, 3).stream()
                .map(Card::code).toList();
        ana.sendAction(ActionPayload.play(play1));
        await(ana, s -> s.currentPlayer() == 2 && s.phase() == GamePhase.BUILD, 5_000);
        await(beto, s -> s.currentPlayer() == 2 && s.phase() == GamePhase.BUILD, 5_000);

        // O jogador 2 envia duas cartas.
        GameSnapshot betoTurn = beto.latestSnapshot();
        List<String> play2 = betoTurn.player(2).hand().subList(0, 2).stream()
                .map(Card::code).toList();
        beto.sendAction(ActionPayload.play(play2));

        GameSnapshot anaReveal = await(ana, s -> s.phase() == GamePhase.REVEAL, 5_000);
        GameSnapshot betoReveal = await(beto, s -> s.phase() == GamePhase.REVEAL, 5_000);
        assertNotNull(anaReveal.lastOutcome());
        assertEquals(3, anaReveal.lastOutcome().player1Cards().size());
        assertEquals(2, anaReveal.lastOutcome().player2Cards().size());
        assertEquals(anaReveal.lastOutcome().player1Score(), betoReveal.lastOutcome().player1Score());

        int expectedWinner = anaReveal.lastOutcome().winner();
        int damage = anaReveal.lastOutcome().damage();
        if (expectedWinner == 1) {
            assertEquals(300 - damage, anaReveal.player(2).hp());
            assertEquals(300, anaReveal.player(1).hp());
        } else if (expectedWinner == 2) {
            assertEquals(300 - damage, anaReveal.player(1).hp());
        }

        // Ambos confirmam a revelação e depois vão para o draft.
        ana.sendAction(ActionPayload.advance());
        beto.sendAction(ActionPayload.advance());

        GameSnapshot anaDraft = await(ana, s -> s.phase() == GamePhase.DRAFT, 5_000);
        GameSnapshot betoDraft = await(beto, s -> s.phase() == GamePhase.DRAFT, 5_000);
        assertEquals(3, anaDraft.player(1).offers().size());
        assertEquals(anaDraft.player(1).offers(), betoDraft.player(2).offers());

        ana.sendAction(ActionPayload.pickJoker(0));
        await(beto, s -> s.phase() == GamePhase.DRAFT && s.currentPlayer() == 2, 5_000);
        beto.sendAction(ActionPayload.pickJoker(1));

        GameSnapshot roundTwo = await(ana, s -> s.phase() == GamePhase.BUILD && s.round() == 2, 5_000);
        assertEquals(2, roundTwo.round());
        assertEquals(1, roundTwo.currentPlayer());
        assertEquals(1, roundTwo.player(1).jokers().size());
        assertEquals(1, roundTwo.player(2).jokers().size());
        assertEquals(8, roundTwo.player(1).hand().size());

        assertTrue(server.sessions().activeSessions() >= 1);
    }

    @Test
    @DisplayName("um jogador consegue reconectar com o token e recuperar a partida")
    void reconnectWithToken() throws Exception {
        GameClient ana = newClient();
        SessionInfoPayload info = ana.createSession("Ana").get(5, TimeUnit.SECONDS);
        GameClient beto = newClient();
        beto.joinSession(info.sessionCode(), "Beto", null).get(5, TimeUnit.SECONDS);
        await(ana, s -> s.phase() == GamePhase.BUILD, 5_000);

        // Derruba a conexão de Ana e reconecta usando o token.
        ana.close();
        clients.remove(ana);
        awaitCondition(() -> !server.sessions().sessionByCode(info.sessionCode()).isConnected(1), 5_000);

        GameClient reconnected = newClient();
        SessionInfoPayload again = reconnected.joinSession(info.sessionCode(), "Ana",
                info.reconnectToken()).get(5, TimeUnit.SECONDS);
        assertEquals(1, again.playerId());
        assertTrue(again.started());
        GameSnapshot recovered = await(reconnected, s -> s.phase() == GamePhase.BUILD, 5_000);
        assertEquals(8, recovered.player(1).hand().size());
        assertTrue(recovered.player(2).hand().isEmpty());

        // O oponente continua jogando contra o mesmo estado autoritativo.
        List<String> cards = recovered.player(1).hand().subList(0, 2).stream().map(Card::code).toList();
        reconnected.sendAction(ActionPayload.play(cards));
        await(beto, s -> s.currentPlayer() == 2 && s.phase() == GamePhase.BUILD, 5_000);
    }

    private GameClient newClient() throws Exception {
        GameClient client = new GameClient();
        clients.add(client);
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client;
    }

    private static GameSnapshot await(GameClient client, Predicate<GameSnapshot> predicate, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        GameSnapshot last = null;
        while (System.currentTimeMillis() < deadline) {
            last = client.latestSnapshot();
            if (last != null && predicate.test(last)) {
                return last;
            }
            sleep(15);
        }
        fail("Tempo esgotado aguardando o estado do jogo; último instantâneo: " + (last == null ? "nenhum" : last.phase()
                + " rodada " + last.round() + " turno " + last.currentPlayer()
                + " versão " + last.version()));
        return null;
    }

    private static ClientEvent.ErrorReceived awaitError(GameClient client, String code, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ClientEvent event = client.pollEvent();
            if (event instanceof ClientEvent.ErrorReceived error) {
                if (code.equals(error.code())) {
                    return error;
                }
            }
            sleep(10);
        }
        fail("Tempo esgotado aguardando o erro " + code);
        return null;
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(20);
        }
        fail("Condição não atendida a tempo");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
