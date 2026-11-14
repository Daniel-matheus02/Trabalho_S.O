package com.game.core.rules;

import com.game.core.model.Card;
import com.game.core.scoring.ScoreResult;

import java.util.List;

/** Compara duas jogadas e calcula o dano exatamente como as regras de referência fazem. */
public final class CombatResolver {

    private CombatResolver() {
    }

    public static RoundOutcome resolve(int round, ScoreResult player1, ScoreResult player2,
                                       List<Card> player1Cards, List<Card> player2Cards,
                                       List<Card> community, GameRules rules) {
        int winner = 0;
        int loser = 0;
        if (player1.finalScore() > player2.finalScore()) {
            winner = 1;
            loser = 2;
        } else if (player2.finalScore() > player1.finalScore()) {
            winner = 2;
            loser = 1;
        }

        int damage = 0;
        if (winner != 0) {
            int difference = Math.abs(player1.finalScore() - player2.finalScore());
            damage = (int) Math.ceil(difference / (double) rules.damageDivisor());
        }
        return new RoundOutcome(round, player1, player2, player1Cards, player2Cards,
                community, winner, loser, damage);
    }

    /** Compatibilidade para combates sem cartas comunitárias. */
    public static RoundOutcome resolve(int round, ScoreResult player1, ScoreResult player2,
                                       List<Card> player1Cards, List<Card> player2Cards,
                                       GameRules rules) {
        return resolve(round, player1, player2, player1Cards, player2Cards, List.of(), rules);
    }
}
