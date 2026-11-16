package com.game.core;

import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRules;
import com.game.core.rules.PowerEvent;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatResolverTest {

    private final ScoringEngine scoring = new ScoringEngine();
    private final GameRules rules = GameRules.DEFAULT;

    private ScoreResult score(String... codes) {
        return scoring.score(java.util.Arrays.stream(codes).map(Card::fromCode).toList(), List.of(), null);
    }

    private RoundOutcome resolve(ScoreResult p1, ScoreResult p2) {
        return CombatResolver.resolve(1, p1, p2, List.of(), List.of(),
                List.of(), List.of(), List.of(), rules);
    }

    @Test
    void higherScoreWinsAndDamageIsCeilOfThird() {
        ScoreResult p1 = score("AE", "AC");    // 64
        ScoreResult p2 = score("7E");          // 5 + 7 = 12 (fichas)
        RoundOutcome outcome = resolve(p1, p2);
        assertEquals(1, outcome.winner());
        assertEquals(2, outcome.loser());
        assertEquals((int) Math.ceil((64 - 12) / 3.0), outcome.damage());
        assertTrue(outcome.damage() > 0);
    }

    @Test
    void lowerScoreLoses() {
        RoundOutcome outcome = resolve(score("2E"), score("AE"));
        assertEquals(2, outcome.winner());
        assertEquals(1, outcome.loser());
        assertEquals((int) Math.ceil((16 - 7) / 3.0), outcome.damage());
    }

    @Test
    void tiesDealNoDamage() {
        RoundOutcome outcome = resolve(score("AE"), score("AC"));
        assertTrue(outcome.isTie());
        assertEquals(0, outcome.damage());
        assertEquals(0, outcome.winner());
        assertEquals(0, outcome.loser());
    }

    @Test
    void onePointDifferenceDealsOneDamage() {
        RoundOutcome outcome = resolve(score("3E"), score("2E"));
        assertEquals(1, outcome.damage());
    }

    @Test
    void outcomeCarriesFinalCardsAndPlayedPowers() {
        Card ace = Card.fromCode("AE");
        Card king = Card.fromCode("KE");
        PowerCard joker = new PowerCard(PowerKind.JOKER, "par_mult");
        PowerCard sabotage = new PowerCard(PowerKind.SABOTAGE, "peso_chumbo");
        PowerEvent event = new PowerEvent(1, PowerKind.SABOTAGE, "peso_chumbo", 2,
                null, null);
        RoundOutcome outcome = CombatResolver.resolve(3,
                score("AE", "AC"), score("KE"),
                List.of(ace, king), List.of(king),
                List.of(joker, sabotage), List.of(),
                List.of(event), rules);
        assertEquals(3, outcome.round());
        assertEquals(List.of(ace, king), outcome.player1Cards());
        assertEquals(List.of(king), outcome.player2Cards());
        assertEquals(List.of(joker, sabotage), outcome.player1Powers());
        assertEquals(List.of(event), outcome.powerEvents());
        assertTrue(outcome.player2Powers().isEmpty());
    }
}
