package com.game.core.rules;

import com.game.core.model.Card;
import com.game.core.scoring.ScoreResult;

import java.util.List;

/**
 * Resultado de uma rodada de combate: ambas as jogadas, suas pontuações e o dano causado ao
 * perdedor.
 */
public record RoundOutcome(
        int round,
        ScoreResult player1Score,
        ScoreResult player2Score,
        List<Card> player1Cards,
        List<Card> player2Cards,
        int winner,
        int loser,
        int damage
) {
    public RoundOutcome {
        player1Cards = List.copyOf(player1Cards);
        player2Cards = List.copyOf(player2Cards);
    }

    public boolean isTie() {
        return winner == 0;
    }
}
