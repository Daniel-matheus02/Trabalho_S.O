package com.game.core.rules;

import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.scoring.ScoreResult;

import java.util.List;

/**
 * Resultado de uma rodada de combate: as jogadas finais, os poderes jogados por cada lado, os
 * eventos de poder na ordem de resolução e o dano causado ao perdedor.
 */
public record RoundOutcome(
        int round,
        ScoreResult player1Score,
        ScoreResult player2Score,
        List<Card> player1Cards,
        List<Card> player2Cards,
        List<PowerCard> player1Powers,
        List<PowerCard> player2Powers,
        List<PowerEvent> powerEvents,
        int winner,
        int loser,
        int damage
) {
    public RoundOutcome {
        player1Cards = List.copyOf(player1Cards);
        player2Cards = List.copyOf(player2Cards);
        player1Powers = List.copyOf(player1Powers);
        player2Powers = List.copyOf(player2Powers);
        powerEvents = List.copyOf(powerEvents);
    }

    public boolean isTie() {
        return winner == 0;
    }
}
