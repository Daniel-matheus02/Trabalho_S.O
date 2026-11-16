package com.game.ai;

import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.rules.GameRules;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotBrainTest {

    private static final GameRules POWERS = new GameRules(300, 8, 5, 3, 4, 3, 2);

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
        List<Card> play = brain.choosePlay(hand, null);

        assertEquals(5, play.size());
        assertTrue(hand.containsAll(play));

        int best = 0;
        for (List<Card> combo : BotBrain.combinations(hand, 5)) {
            best = Math.max(best, scoring.score(combo, List.of(), null).finalScore());
        }
        assertEquals(best, scoring.score(play, List.of(), null).finalScore());
    }

    @Test
    void choosesTheBestPlayAmongOneToMaxPlayCards() {
        List<Card> hand = hand("AE", "AC", "KO", "7E", "2C", "9P", "3E", "4O");
        List<Card> play = brain.choosePlay(hand, null);
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
    void enumeratesEverySubsetOfSizeZeroToTwo() {
        List<List<Integer>> subsets = BotBrain.subsets(7, 2);
        assertEquals(7 + 21, subsets.size(), "28 subconjuntos não vazios; o vazio é a base");
        for (List<Integer> subset : subsets) {
            assertTrue(subset.size() <= 2);
            assertEquals(subset.size(), subset.stream().distinct().count());
        }
    }

    @Test
    void choosesTheJokerThatImprovesTheScore() {
        GameSnapshot state = snapshotWith(power(PowerKind.JOKER, "par_mult"),
                power(PowerKind.JOKER, "quintuplus"));
        assertEquals(List.of(0), brain.choosePowers(state, 1));
    }

    @Test
    void choosesTheSabotageThatLowersTheOpponent() {
        GameSnapshot state = snapshotWith(power(PowerKind.SABOTAGE, "peso_chumbo"),
                power(PowerKind.JOKER, "quintuplus"));
        assertEquals(List.of(0), brain.choosePowers(state, 1));
    }

    @Test
    void choosesNoneWhenNothingImprovesTheDifference() {
        GameSnapshot state = snapshotWith(power(PowerKind.JOKER, "quintuplus"));
        assertTrue(brain.choosePowers(state, 1).isEmpty());
    }

    @Test
    void handlesMissingOrEmptySnapshots() {
        assertTrue(brain.choosePowers(null, 1).isEmpty());
        assertTrue(brain.choosePowers(snapshotWith(), 1).isEmpty());
    }

    @Test
    void emptyHandIsHandled() {
        assertTrue(brain.choosePlay(List.of(), null).isEmpty());
    }

    private static PowerCard power(PowerKind kind, String id) {
        return new PowerCard(kind, id);
    }

    /**
     * Instantâneo mínimo na fase SABOTAGE: mão própria de par de ases, oponente com par de reis.
     * A mão de poderes é entregue como se o servidor já a tivesse filtrado para o visualizador.
     */
    private static GameSnapshot snapshotWith(PowerCard... powers) {
        List<PowerCard> powerHand = List.of(powers);
        List<Card> own = hand("AE", "AC");
        List<Card> opponent = hand("KE", "KC");
        PlayerView me = new PlayerView(
                1, "CPU", 300, 300, 3, List.of(), true, own, null,
                powerHand, powerHand.size(), List.of(), false, false, 2);
        PlayerView rival = new PlayerView(
                2, "Humano", 300, 300, 3, List.of(), true, opponent, null,
                List.of(), 0, List.of(), false, false, 2);
        return new GameSnapshot("bot", 1, GamePhase.SABOTAGE, 1, 0, POWERS,
                null, "", "", 1, me, rival, null, 0, List.of());
    }
}
