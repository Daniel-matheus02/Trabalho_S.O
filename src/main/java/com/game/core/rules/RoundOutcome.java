package com.game.core.rules;

import com.game.core.model.Card;
import com.game.core.scoring.ScoreResult;

import java.util.List;

/**
 * Resultado de uma rodada de combate: ambas as jogadas, a mesa final e o dano causado ao
 * perdedor.
 */
public record RoundOutcome(
        int round,
        ScoreResult player1Score,
        ScoreResult player2Score,
        List<Card> player1Cards,
        List<Card> player2Cards,
        List<Card> community,
        int winner,
        int loser,
        int damage
) {
    public RoundOutcome {
        player1Cards = List.copyOf(player1Cards);
        player2Cards = List.copyOf(player2Cards);
        community = List.copyOf(community);
    }

    /** Construtor de compatibilidade para rodadas sem cartas comunitárias. */
    public RoundOutcome(int round, ScoreResult player1Score, ScoreResult player2Score,
                        List<Card> player1Cards, List<Card> player2Cards,
                        int winner, int loser, int damage) {
        this(round, player1Score, player2Score, player1Cards, player2Cards, List.of(),
                winner, loser, damage);
    }

    public boolean isTie() {
        return winner == 0;
    }
}
