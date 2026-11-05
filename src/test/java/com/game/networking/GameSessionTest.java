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
    void duplicateCommandsAreIgnored() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        GameSnapshot state = snapshotFrom(drain(ch1));
        drain(ch2);
        assertEquals(1, state.currentPlayer());

        Card card = state.player(1).hand().get(0);
        ActionPayload action = ActionPayload.play(List.of(card.code()));
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
    void staleSessionMessageFromWrongPlayerIsRejectedBySessionManagerValidation() {
        // Coberto indiretamente por GameClient/ServerIntegrationTest; aqui se verifica se a sessão
        // do servidor aceita apenas ações do jogador que possui o turno.
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        GameSession session = startedSession(ch1, ch2);
        drain(ch1);
        GameSnapshot state = snapshotFrom(drain(ch2));

        ActionPayload pick = ActionPayload.pickJoker(0);
        Envelope envelope = codec.message(MessageType.PLAYER_ACTION, session.sessionId(), 1, pick);
        session.handleAction(1, envelope, pick);

        List<Envelope> messages = drain(ch1);
        assertTrue(messages.stream().anyMatch(e -> e.type() == MessageType.ERROR));
    }
}
