package com.game.core;

import com.game.core.model.Card;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRules;
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

    @Test
    void higherScoreWinsAndDamageIsCeilOfThird() {
        ScoreResult p1 = score("AE", "AC");    // 64
        ScoreResult p2 = score("7E");          // 5 + 7 = 12 (fichas)
        RoundOutcome outcome = CombatResolver.resolve(1, p1, p2, List.of(), List.of(), rules);
        assertEquals(1, outcome.winner());
        assertEquals(2, outcome.loser());
        assertEquals((int) Math.ceil((64 - 12) / 3.0), outcome.damage());
        assertTrue(outcome.damage() > 0);
    }

    @Test
    void lowerScoreLoses() {
        RoundOutcome outcome = CombatResolver.resolve(2, score("2E"), score("AE"), List.of(), List.of(), rules);
        assertEquals(2, outcome.winner());
        assertEquals(1, outcome.loser());
        assertEquals((int) Math.ceil((16 - 7) / 3.0), outcome.damage());
    }

    @Test
    void tiesDealNoDamage() {
        RoundOutcome outcome = CombatResolver.resolve(1, score("AE"), score("AC"), List.of(), List.of(), rules);
        assertTrue(outcome.isTie());
        assertEquals(0, outcome.damage());
        assertEquals(0, outcome.winner());
        assertEquals(0, outcome.loser());
    }

    @Test
    void onePointDifferenceDealsOneDamage() {
        RoundOutcome outcome = CombatResolver.resolve(1, score("3E"), score("2E"), List.of(), List.of(), rules);
        assertEquals(1, outcome.damage());
    }
}
