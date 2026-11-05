package com.game.ai;

import com.game.core.model.Card;
import com.game.core.scoring.ScoringEngine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotBrainTest {

    private final ScoringEngine scoring = new ScoringEngine();
    private final BotBrain brain = new BotBrain(scoring, 5);

    private static List<Card> hand(String... codes) {
        return Arrays.stream(codes).map(Card::fromCode).toList();
    }

    @Test
    void considersEveryLegalCombination() {
        List<Card> hand = hand("AE", "AC", "AO", "KE", "KC", "2O", "3P", "7E");
        int expected = 8 + 28 + 56 + 70 + 56;
        assertEquals(expected, BotBrain.combinations(hand, 1).size()
                + BotBrain.combinations(hand, 2).size()
                + BotBrain.combinations(hand, 3).size()
                + BotBrain.combinations(hand, 4).size()
                + BotBrain.combinations(hand, 5).size());
    }

    @Test
    void choosesTheHighestScoringPlay() {
        List<Card> hand = hand("AE", "AC", "AO", "KE", "KC", "2O", "3P", "7E");
        List<Card> play = brain.choosePlay(hand, List.of(), null);

        assertTrue(play.size() >= 1 && play.size() <= 5);
        assertTrue(hand.containsAll(play));

        int best = 0;
        for (int size = 1; size <= 5; size++) {
            for (List<Card> combo : BotBrain.combinations(hand, size)) {
                best = Math.max(best, scoring.score(combo, List.of(), null).finalScore());
            }
        }
        assertEquals(best, scoring.score(play, List.of(), null).finalScore());
    }

    @Test
    void respectsJokersWhenEvaluating() {
        // Naipe Afiado recompensa espadas; o bot deve explorar isso com o mesmo motor.
        List<Card> hand = hand("AE", "2E", "3E", "4E", "5E", "9C", "9O", "9P");
        List<Card> play = brain.choosePlay(hand, List.of("naipe_afiado"), null);
        assertEquals(5, play.size());
        assertEquals(5, play.stream().filter(card -> card.suit().code().equals("E")).count());
    }

    @Test
    void picksTheJokerWithTheBestMarginalValue() {
        List<Card> spades = hand("AE", "2E", "3E", "5E");
        List<String> offers = List.of("combo5", "naipe_afiado", "alto_risco");
        int index = brain.chooseJoker(offers, spades, List.of(), null);
        assertEquals("naipe_afiado", offers.get(index));
    }

    @Test
    void validOfferIndexesAndRerollHeuristics() {
        List<Card> hand = hand("2E", "7C", "9O", "JP", "3E", "8C", "KO", "4P");
        List<String> offers = List.of("combo5", "rio", "escada");
        int index = brain.chooseJoker(offers, hand, List.of(), null);
        assertTrue(index >= 0 && index < offers.size());

        assertFalse(brain.shouldReroll(offers, hand, List.of(), null, 0));
        assertEquals(-1, brain.chooseJoker(List.of(), hand, List.of(), null));
    }

    @Test
    void emptyHandIsHandled() {
        assertTrue(brain.choosePlay(List.of(), List.of(), null).isEmpty());
    }
}
