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
        boolean ready,
        int discardsRemaining
) {
    public PlayerView {
        // Um campo nulo vindo de um instantâneo filtrado (ou de um JSON sem a lista) não pode
        // explodir na apresentação: listas ausentes viram listas vazias.
        hand = hand == null ? List.of() : List.copyOf(hand);
        playedCards = playedCards == null ? List.of() : List.copyOf(playedCards);
        powerHand = powerHand == null ? List.of() : List.copyOf(powerHand);
        playedPowers = playedPowers == null ? List.of() : List.copyOf(playedPowers);
    }
}
