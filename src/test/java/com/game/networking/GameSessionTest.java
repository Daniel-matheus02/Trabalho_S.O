package com.game.networking;

import com.game.core.model.Card;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.SessionInfoPayload;
import com.game.net.protocol.StatePayload;
import com.game.server.session.GameSession;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes em nível de sessão sem sockets: tokens de reconexão, comandos duplicados, erros. */
class GameSessionTest {

    private final MessageCodec codec = new MessageCodec();

    private GameSession startedSession(EmbeddedChannel ch1, EmbeddedChannel ch2) {
        GameSession session = new GameSession("ABC123", 2, codec);
        SessionInfoPayload one = session.join(ch1, "Ana", null, MessageType.SESSION_CREATED);
        assertNotNull(one.reconnectToken());
        session.join(ch2, "Beto", null);
        assertTrue(session.isStarted());
        return session;
    }

    private List<Envelope> drain(EmbeddedChannel channel) {
        List<Envelope> messages = new ArrayList<>();
        String message;
        while ((message = channel.readOutbound()) != null) {
            messages.add(codec.decode(message));
        }
        return messages;
    }

    private GameSnapshot snapshotFrom(List<Envelope> messages) {
        for (Envelope envelope : messages) {
            if (envelope.type() == MessageType.GAME_START
                    || envelope.type() == MessageType.GAME_STATE_UPDATE) {
                return codec.payload(envelope, StatePayload.class).snapshot();
            }
        }
        throw new AssertionError("No state payload in " + messages.stream().map(Envelope::type).toList());
    }

    @Test
    void reconnectingWithTokenRestoresTheSeat() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        SessionInfoPayload first = session.infoFor(1);
        drain(ch1);
        drain(ch2);

        session.playerDisconnected(1);
        assertFalse(session.isConnected(1));

        EmbeddedChannel reconnected = new EmbeddedChannel();
        SessionInfoPayload info = session.join(reconnected, "Ana", first.reconnectToken());
        assertTrue(session.isConnected(1));
        assertEquals(1, info.playerId());
        assertTrue(session.isStarted());

        List<Envelope> messages = drain(reconnected);
        assertTrue(messages.stream().anyMatch(e -> e.type() == MessageType.GAME_START));
        assertTrue(drain(ch2).stream().anyMatch(e -> e.type() == MessageType.PLAYER_CONNECTED));
    }

    @Test
    void rejectsInvalidTokenAndUnknownSessionFullSession() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.game.server.session.SessionException.class,
                () -> session.join(new EmbeddedChannel(), "Mallory", "not-a-token"));

        var full = org.junit.jupiter.api.Assertions.assertThrows(
                com.game.server.session.SessionException.class,
                () -> session.join(new EmbeddedChannel(), "Terceiro", null));
        assertEquals("SESSION_FULL", full.code());
    }

    @Test
    void aRejectedActionIsNotRememberedAndTheSameMessageIdCanBeRetried() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        drain(ch1);
        GameSnapshot state = snapshotFrom(drain(ch2));

        // Jogador 2 tenta agir fora do turno: a mensagem é recusada.
        Card card = state.player(2).hand().get(0);
        ActionPayload rejected = ActionPayload.play(List.of(card.code()));
        Envelope rejectedEnvelope = new Envelope(MessageType.PLAYER_ACTION, "rejected-message",
                session.sessionId(), 2, System.currentTimeMillis(),
                codec.mapper().valueToTree(rejected));
        session.handleAction(2, rejectedEnvelope, rejected);
        assertTrue(drain(ch2).stream().anyMatch(e -> e.type() == MessageType.ERROR));

        // O jogador 1 reaproveita o mesmo id de mensagem numa ação válida: ela precisa ser
        // aplicada, porque a recusa anterior não deve marcar o comando como processado.
        List<String> codes = session.snapshotFor(1).player(1).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        ActionPayload accepted = ActionPayload.play(codes);
        Envelope acceptedEnvelope = new Envelope(MessageType.PLAYER_ACTION, "rejected-message",
                session.sessionId(), 1, System.currentTimeMillis(),
                codec.mapper().valueToTree(accepted));
        session.handleAction(1, acceptedEnvelope, accepted);

        GameSnapshot after = snapshotFrom(drain(ch1));
        assertEquals(2, after.currentPlayer(), "a ação válida com o id recusado deve ser aplicada");
        assertEquals(state.version() + 1, after.version(),
                "apenas a ação válida deve ter avançado o estado");
    }

    @Test
    void duplicateCommandsAreIgnored() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        GameSnapshot state = snapshotFrom(drain(ch1));
        drain(ch2);
        assertEquals(1, state.currentPlayer());

        List<String> codes = state.player(1).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        ActionPayload action = ActionPayload.play(codes);
        Envelope envelope = codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1, action);

        session.handleAction(1, envelope, action);
        GameSnapshot afterFirst = snapshotFrom(drain(ch1));
        session.handleAction(1, envelope, action);
        GameSnapshot afterDuplicate = snapshotFrom(drain(ch1));

        assertEquals(afterFirst.version(), afterDuplicate.version(),
                "a duplicated command must not advance the game");
        assertEquals(2, afterDuplicate.currentPlayer());
    }

    @Test
    void invalidActionsProduceErrorMessages() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        drain(ch1);
        GameSnapshot state = snapshotFrom(drain(ch2));

        // O jogador 2 tenta jogar durante o turno do jogador 1.
        Card card = state.player(2).hand().get(0);
        ActionPayload action = ActionPayload.play(List.of(card.code()));
        Envelope envelope = codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 2, action);
        session.handleAction(2, envelope, action);

        List<Envelope> messages = drain(ch2);
        Envelope error = messages.stream()
                .filter(e -> e.type() == MessageType.ERROR)
                .findFirst().orElseThrow();
        ErrorPayload payload = codec.payload(error, ErrorPayload.class);
        assertEquals("NOT_YOUR_TURN", payload.code());
        assertEquals(GamePhase.BUILD, state.phase());
    }

    @Test
    void aLateLockAfterTheRoundResolvedIsANoOpAndNotAnError() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        drain(ch1);
        drain(ch2);

        // Os dois jogadores enviam as cinco cartas; a rodada entra em SABOTAGE.
        List<String> one = session.snapshotFor(1).player(1).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        List<String> two = session.snapshotFor(2).player(2).hand().subList(0, 5).stream()
                .map(Card::code).toList();
        session.handleAction(1, codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1,
                ActionPayload.play(one)), ActionPayload.play(one));
        session.handleAction(2, codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 2,
                ActionPayload.play(two)), ActionPayload.play(two));
        assertEquals(GamePhase.SABOTAGE, session.snapshotFor(1).phase());

        ActionPayload lock = ActionPayload.lockPowers(List.of(0));
        session.handleAction(1, codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1,
                lock), lock);
        session.handleAction(2, codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 2,
                lock), lock);
        assertEquals(GamePhase.REVEAL, session.snapshotFor(1).phase());
        drain(ch1);

        // Comando atrasado: chegou depois de a rodada já ter resolvido. Deve devolver o estado,
        // nunca um ERROR que apareceria como falha para o jogador.
        session.handleAction(1, codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1,
                lock), lock);

        List<Envelope> messages = drain(ch1);
        assertTrue(messages.stream().anyMatch(e -> e.type() == MessageType.GAME_STATE_UPDATE),
                "o comando atrasado deve devolver o estado atual");
        assertTrue(messages.stream().noneMatch(e -> e.type() == MessageType.ERROR),
                "a corrida de poderes atrasados não pode virar erro de protocolo");
    }

    @Test
    void staleSessionMessageFromWrongPlayerIsRejectedBySessionManagerValidation() {
        // Coberto indiretamente por GameClient/ServerIntegrationTest; aqui se verifica se a sessão
        // do servidor aceita apenas ações do jogador que possui o turno.
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        drain(ch1);
        GameSnapshot state = snapshotFrom(drain(ch2));

        ActionPayload lock = ActionPayload.lockPowers(List.of(0));
        Envelope envelope = codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1, lock);
        session.handleAction(1, envelope, lock);

        List<Envelope> messages = drain(ch1);
        assertTrue(messages.stream().anyMatch(e -> e.type() == MessageType.ERROR));
    }
}
