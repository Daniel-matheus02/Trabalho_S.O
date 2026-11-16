package com.game.scoring;

import com.game.core.model.Card;
import com.game.core.model.Deck;
import com.game.core.scoring.ScoreModifiers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoreStep;
import com.game.core.scoring.ScoringEngine;
import com.game.core.scoring.StepKind;
import com.game.net.protocol.MessageCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica que os passos nunca divergem do placar real em 500 mãos aleatórias. */
class ScoreStepsTest {

    private final ScoringEngine scoring = new ScoringEngine();
    private final MessageCodec codec = new MessageCodec();

    @Test
    void fiveHundredRandomHandsKeepStepsAndFinalScoreConsistent() {
        Random random = new Random(20261115L);
        List<String> jokerPool = com.game.core.scoring.Jokers.ids();
        List<String> sabotagePool = com.game.core.sabotage.Sabotages.ids();

        for (int draw = 0; draw < 500; draw++) {
            List<Card> hand = new ArrayList<>(Deck.shuffled(new Random(draw * 37L + 11L))
                    .subList(0, 5));
            List<String> jokers = shuffledSlice(jokerPool, random.nextInt(3), random);
            List<String> sabotages = shuffledSlice(sabotagePool, random.nextInt(3), random);
            ScoreModifiers modifiers = modifiersFor(sabotages);

            ScoreResult result = scoring.score(hand, jokers, null, modifiers, sabotages);
            List<ScoreStep> steps = result.steps();
            assertFalse(steps.isEmpty(), "draw " + draw + " sem passos");
            ScoreStep first = steps.get(0);
            ScoreStep last = steps.get(steps.size() - 1);
            assertEquals(StepKind.HAND_BASE, first.kind());
            assertEquals(result.chips(), last.chipsAfter(), "draw " + draw);
            assertEquals(result.mult(), last.multAfter(), "draw " + draw);
            assertEquals((int) Math.round((double) result.chips() * result.mult()),
                    result.finalScore(), "draw " + draw);
            long cardSteps = steps.stream().filter(step -> step.kind() == StepKind.CARD).count();
            assertEquals(hand.size(), cardSteps, "draw " + draw);
            assertEquals(jokers.size(),
                    steps.stream().filter(step -> step.kind() == StepKind.JOKER).count());
            assertEquals(sabotages.size(),
                    steps.stream().filter(step -> step.kind() == StepKind.SABOTAGE).count());
        }
    }

    @Test
    void stepsRoundTripThroughJackson() throws Exception {
        List<Card> hand = List.of(Card.fromCode("AE"), Card.fromCode("AC"),
                Card.fromCode("KO"), Card.fromCode("7E"), Card.fromCode("2C"));
        ScoreResult result = scoring.score(hand, List.of("par_mult", "naipe_afiado"),
                "mesa_rica", ScoreModifiers.NONE.withPairTax(), List.of("imposto_par"));

        String json = codec.mapper().writeValueAsString(result);
        ScoreResult restored = codec.mapper().readValue(json, ScoreResult.class);

        assertEquals(result, restored);
        assertEquals(result.steps(), restored.steps());
        assertTrue(restored.steps().stream().allMatch(step -> step.label() != null));
    }

    private static List<String> shuffledSlice(List<String> pool, int count, Random random) {
        List<String> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return List.copyOf(copy.subList(0, Math.min(count, copy.size())));
    }

    private static ScoreModifiers modifiersFor(List<String> sabotages) {
        ScoreModifiers modifiers = ScoreModifiers.NONE;
        for (String id : sabotages) {
            switch (id) {
                case "anti_flush" -> modifiers = modifiers.withBlockFlush();
                case "peso_chumbo" -> modifiers = modifiers.withMultPenalty(2);
                case "imposto_par" -> modifiers = modifiers.withPairTax();
                case "nevoa" -> modifiers = modifiers.withNullifyJokers();
                default -> {
                }
            }
        }
        return modifiers;
    }
}
