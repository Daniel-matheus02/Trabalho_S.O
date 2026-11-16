package com.game.networking;

import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.core.model.Card;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
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
 * rodada completa com poderes e segunda rodada, além da validação de comandos fora do turno.
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
    @DisplayName("dois clientes desktop jogam uma rodada, travam os poderes e iniciam a segunda rodada")
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

        // O jogador 1 envia cinco cartas; só ele vê a própria jogada na mesa.
        List<String> play1 = anaState.player(1).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        ana.sendAction(ActionPayload.play(play1));
        GameSnapshot afterPlay1 = await(ana, s -> s.currentPlayer() == 2
                && s.phase() == GamePhase.BUILD, 5_000);
        assertEquals(5, afterPlay1.player(1).playedCards().size());
        assertTrue(afterPlay1.player(2).playedCards().isEmpty());
        GameSnapshot betoView = await(beto, s -> s.currentPlayer() == 2
                && s.phase() == GamePhase.BUILD, 5_000);
        assertTrue(betoView.player(1).playedCards().isEmpty(),
                "a jogada do primeiro não vaza para o oponente antes da segunda");

        // O jogador 2 envia cinco cartas; a mesa aparece e cada lado recebe sua mão de poderes.
        GameSnapshot betoTurn = beto.latestSnapshot();
        List<String> play2 = betoTurn.player(2).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        beto.sendAction(ActionPayload.play(play2));

        GameSnapshot anaPowers = await(ana, s -> s.phase() == GamePhase.SABOTAGE, 5_000);
        GameSnapshot betoPowers = await(beto, s -> s.phase() == GamePhase.SABOTAGE, 5_000);
        assertEquals(6, anaPowers.player(1).powerHand().size());
        assertTrue(anaPowers.player(2).powerHand().isEmpty(),
                "os poderes do oponente continuam ocultos");
        assertEquals(6, anaPowers.player(2).powerCount());
        assertEquals(6, betoPowers.player(2).powerHand().size());
        assertTrue(betoPowers.player(1).powerHand().isEmpty());

        ana.sendAction(ActionPayload.lockPowers(List.of(0, 1)));
        beto.sendAction(ActionPayload.lockPowers(List.of()));

        GameSnapshot anaReveal = await(ana, s -> s.phase() == GamePhase.REVEAL, 5_000);
        GameSnapshot betoReveal = await(beto, s -> s.phase() == GamePhase.REVEAL, 5_000);
        assertNotNull(anaReveal.lastOutcome());
        assertEquals(5, anaReveal.lastOutcome().player1Cards().size());
        assertEquals(5, anaReveal.lastOutcome().player2Cards().size());
        assertEquals(5, anaReveal.player(1).playedCards().size());
        assertEquals(5, anaReveal.player(2).playedCards().size());
        assertEquals(anaReveal.player(1).playedCards(), betoReveal.player(1).playedCards(),
                "as duas mãos ficam visíveis para os dois jogadores");
        assertEquals(2, anaReveal.lastOutcome().player1Powers().size());
        assertTrue(anaReveal.lastOutcome().player2Powers().isEmpty());
        assertEquals(2, anaReveal.player(1).playedPowers().size());
        assertTrue(anaReveal.player(2).playedPowers().isEmpty());
        assertEquals(anaReveal.lastOutcome().player1Score(), betoReveal.lastOutcome().player1Score());

        int expectedWinner = anaReveal.lastOutcome().winner();
        int damage = anaReveal.lastOutcome().damage();
        if (expectedWinner == 1) {
            assertEquals(300 - damage, anaReveal.player(2).hp());
            assertEquals(300, anaReveal.player(1).hp());
        } else if (expectedWinner == 2) {
            assertEquals(300 - damage, anaReveal.player(1).hp());
        }

        // Ambos confirmam a revelação e vão direto à próxima rodada.
        ana.sendAction(ActionPayload.advance());
        beto.sendAction(ActionPayload.advance());

        GameSnapshot roundTwo = await(ana, s -> s.phase() == GamePhase.BUILD && s.round() == 2, 5_000);
        assertEquals(2, roundTwo.round());
        assertEquals(1, roundTwo.currentPlayer());
        assertEquals(8, roundTwo.player(1).hand().size());
        assertEquals(2, roundTwo.firstResolver());
        assertEquals(0, roundTwo.player(1).powerCount());
        assertTrue(roundTwo.player(1).playedPowers().isEmpty());
        assertFalse(roundTwo.player(1).powersLocked());

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
        List<String> cards = recovered.player(1).hand().subList(0, 5).stream().map(Card::code).toList();
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
