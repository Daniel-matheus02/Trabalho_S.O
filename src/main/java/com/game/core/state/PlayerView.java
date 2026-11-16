package com.game.core.state;

import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.scoring.ScoreResult;

import java.util.List;

/**
 * Projeção de um jogador por visualizador. As cartas e os poderes só estão presentes quando o
 * visualizador pode vê-los; caso contrário, apenas a contagem é exposta, o que faz a informação
 * oculta funcionar tanto na rede quanto contra a IA.
 */
public record PlayerView(
        int id,
        String name,
        int hp,
        int maxHp,
        int handCount,
        List<Card> hand,
        boolean submitted,
        List<Card> playedCards,
        ScoreResult tableScore,
        List<PowerCard> powerHand,
        int powerCount,
        List<PowerCard> playedPowers,
        boolean powersLocked,
        boolean ready
) {
    public PlayerView {
        hand = List.copyOf(hand);
        playedCards = List.copyOf(playedCards);
        powerHand = List.copyOf(powerHand);
        playedPowers = List.copyOf(playedPowers);
    }
}
