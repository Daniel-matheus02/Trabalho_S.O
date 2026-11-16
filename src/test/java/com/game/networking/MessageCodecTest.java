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
        assertEquals(snapshot.player1().playedCards(), restored.player1().playedCards());
        assertEquals(snapshot.player1().powerCount(), restored.player1().powerCount());
        assertEquals(snapshot.player1().powerHand(), restored.player1().powerHand());
        assertEquals(snapshot.player1().playedPowers(), restored.player1().playedPowers());
        assertEquals(snapshot.globalModifierId(), restored.globalModifierId());
        assertEquals(snapshot.rules(), restored.rules());
        assertEquals(8, restored.player1().hand().size());
    }

    @Test
    void roundTripsTableScores() {
        GameEngine engine = new GameEngine("table", "Ana", "Beto");
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 5));
        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        GameSnapshot snapshot = engine.snapshotFor(1);

        Envelope envelope = codec.message(MessageType.GAME_STATE_UPDATE, "table", 1,
                new StatePayload(snapshot));
        GameSnapshot restored = codec.payload(codec.decode(codec.encode(envelope)),
                StatePayload.class).snapshot();

        assertNotNull(restored.player1().tableScore());
        assertEquals(snapshot.player1().tableScore(), restored.player1().tableScore());
        assertEquals(snapshot.player2().tableScore(), restored.player2().tableScore());
        assertEquals(snapshot.player2().playedCards(), restored.player2().playedCards());
    }

    @Test
    void roundTripsActionsAndErrors() {
        Envelope play = codec.message(MessageType.PLAYER_ACTION, "s", 2,
                ActionPayload.play(List.of("AE", "TC")));
        ActionPayload action = codec.payload(codec.decode(codec.encode(play)), ActionPayload.class);
        assertEquals(com.game.net.protocol.ActionType.SUBMIT_PLAY, action.action());
        assertEquals(List.of("AE", "TC"), action.cards());

        Envelope powers = codec.message(MessageType.PLAYER_ACTION, "s", 2,
                ActionPayload.lockPowers(List.of(0, 2)));
        ActionPayload locked = codec.payload(codec.decode(codec.encode(powers)), ActionPayload.class);
        assertEquals(com.game.net.protocol.ActionType.LOCK_POWERS, locked.action());
        assertEquals(List.of(0, 2), locked.powerIndices());
        assertTrue(locked.cards().isEmpty());

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
    void powerEventsWithCardChangesRoundTrip() {
        com.game.core.rules.RoundOutcome outcome = null;
        for (int seed = 0; seed < 300 && outcome == null; seed++) {
            GameEngine engine = new GameEngine("ev-" + seed, "A", "B",
                    new java.util.Random(seed), com.game.core.rules.GameRules.DEFAULT,
                    new ScoringEngine());
            engine.start();
            engine.submitPlay(1, engine.handOf(1).subList(0, 5));
            engine.submitPlay(2, engine.handOf(2).subList(0, 5));
            List<com.game.core.model.PowerCard> hand = engine.powerHandOf(1);
            int index = -1;
            for (int i = 0; i < hand.size(); i++) {
                String id = hand.get(i).id();
                if (id.equals("virada_naipes") || id.equals("troca_carta")) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                continue;
            }
            engine.lockPowers(1, List.of(index));
            engine.lockPowers(2, List.of());
            outcome = engine.snapshot().lastOutcome();
        }
        assertNotNull(outcome, "uma semente deve oferecer sabotagem de mesa");
        Envelope envelope = codec.message(MessageType.ROUND_RESULT, "s", 1,
                new com.game.net.protocol.RoundResultPayload(outcome, 300, 300));
        var payload = codec.payload(codec.decode(codec.encode(envelope)),
                com.game.net.protocol.RoundResultPayload.class);
        assertEquals(1, payload.outcome().powerEvents().size());
        var originalEvent = outcome.powerEvents().get(0);
        var restoredEvent = payload.outcome().powerEvents().get(0);
        assertEquals(originalEvent.kind(), restoredEvent.kind());
        assertEquals(originalEvent.id(), restoredEvent.id());
        assertEquals(originalEvent.targetPlayer(), restoredEvent.targetPlayer());
        assertEquals(originalEvent.cardsBefore(), restoredEvent.cardsBefore());
        assertEquals(originalEvent.cardsAfter(), restoredEvent.cardsAfter());
    }

    @Test
    void scoringEngineIsSerializableThroughRuntimeSnapshots() {
        GameEngine engine = new GameEngine("s", "A", "B", new java.util.Random(5),
                com.game.core.rules.GameRules.DEFAULT, new ScoringEngine());
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 5));
        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());
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
