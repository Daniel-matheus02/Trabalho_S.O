package com.game.networking;

import com.fasterxml.jackson.databind.JsonNode;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GameEngine;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.StatePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void roundTripsSnapshotEnvelope() {
        GameEngine engine = new GameEngine("s-1", "Ana", "Beto");
        engine.start();
        GameSnapshot snapshot = engine.snapshotFor(1);

        Envelope original = codec.message(MessageType.GAME_STATE_UPDATE, "s-1", 1,
                new StatePayload(snapshot));
        String json = codec.encode(original);
        Envelope decoded = codec.decode(json);

        assertEquals(MessageType.GAME_STATE_UPDATE, decoded.type());
        assertEquals("s-1", decoded.sessionId());
        assertEquals(1, decoded.playerId());
        assertEquals(original.messageId(), decoded.messageId());

        GameSnapshot restored = codec.payload(decoded, StatePayload.class).snapshot();
        assertEquals(snapshot.phase(), restored.phase());
        assertEquals(snapshot.round(), restored.round());
        assertEquals(snapshot.player1().hand(), restored.player1().hand());
        assertEquals(snapshot.player1().jokers(), restored.player1().jokers());
        assertEquals(snapshot.globalModifierId(), restored.globalModifierId());
        assertEquals(snapshot.rules(), restored.rules());
        assertEquals(8, restored.player1().hand().size());
    }

    @Test
    void roundTripsActionsAndErrors() {
        Envelope play = codec.message(MessageType.PLAYER_ACTION, "s", 2,
                ActionPayload.play(List.of("AE", "TC")));
        ActionPayload action = codec.payload(codec.decode(codec.encode(play)), ActionPayload.class);
        assertEquals(com.game.net.protocol.ActionType.SUBMIT_PLAY, action.action());
        assertEquals(List.of("AE", "TC"), action.cards());

        Envelope error = codec.error("s", 0, "BAD", "broken");
        ErrorPayload payload = codec.payload(codec.decode(codec.encode(error)), ErrorPayload.class);
        assertEquals("BAD", payload.code());
        assertEquals("broken", payload.message());
    }

    @Test
    void unknownFieldsAreIgnoredForForwardCompatibility() {
        String json = "{\"type\":\"PING\",\"messageId\":\"m1\",\"sessionId\":null,"
                + "\"playerId\":0,\"timestamp\":1,\"futureField\":true,"
                + "\"payload\":{\"clientTime\":123,\"extra\":\"x\"}}";
        Envelope envelope = codec.decode(json);
        assertEquals(MessageType.PING, envelope.type());
        assertNotNull(envelope.payload());
        assertTrue(envelope.payload().has("clientTime"));
    }

    @Test
    void rejectsMalformedMessages() {
        assertThrows(com.game.net.protocol.ProtocolException.class, () -> codec.decode(""));
        assertThrows(com.game.net.protocol.ProtocolException.class, () -> codec.decode("not-json"));
        assertThrows(com.game.net.protocol.ProtocolException.class,
                () -> codec.decode("{\"type\":\"PING\"}"));
    }

    @Test
    void rejectsMissingPayload() {
        Envelope envelope = codec.message(MessageType.PING, null, 0, null);
        assertThrows(com.game.net.protocol.ProtocolException.class,
                () -> codec.payload(envelope, com.game.net.protocol.PingPayload.class));
    }

    @Test
    void payloadContainsNoHiddenOpponentHand() {
        GameEngine engine = new GameEngine("s", "A", "B");
        engine.start();
        Envelope envelope = codec.message(MessageType.GAME_STATE_UPDATE, "s", 1,
                new StatePayload(engine.snapshotFor(1)));
        JsonNode json = codec.mapper().valueToTree(envelope);
        JsonNode player2 = json.get("payload").get("snapshot").get("player2");
        assertEquals(0, player2.get("hand").size());
        assertTrue(player2.get("hand").isArray());
        assertEquals(8, player2.get("handCount").asInt());
    }

    @Test
    void scoringEngineIsSerializableThroughRuntimeSnapshots() {
        GameEngine engine = new GameEngine("s", "A", "B", new java.util.Random(5),
                com.game.core.rules.GameRules.DEFAULT, new ScoringEngine());
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 2));
        engine.submitPlay(2, engine.handOf(2).subList(0, 2));
        Envelope envelope = codec.message(MessageType.ROUND_RESULT, "s", 1,
                new com.game.net.protocol.RoundResultPayload(engine.snapshot().lastOutcome(), 300, 300));
        var payload = codec.payload(codec.decode(codec.encode(envelope)),
                com.game.net.protocol.RoundResultPayload.class);
        assertEquals(engine.snapshot().lastOutcome().winner(), payload.outcome().winner());
        assertNotNull(payload.outcome().player1Score().type());
        assertEquals(engine.snapshot().lastOutcome().player1Score().finalScore(),
                payload.outcome().player1Score().finalScore());
    }
}
