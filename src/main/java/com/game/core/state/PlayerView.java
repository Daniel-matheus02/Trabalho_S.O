package com.game.core.state;

import com.game.core.model.Card;

import java.util.List;

/**
 * Projeção de um jogador por visualizador. As cartas só estão presentes quando o visualizador pode vê-las;
 * caso contrário, apenas a contagem é exposta, o que faz a informação oculta
 * funcionar tanto na rede quanto contra a IA.
 */
public record PlayerView(
        int id,
        String name,
        int hp,
        int maxHp,
        int handCount,
        boolean handVisible,
        List<Card> hand,
        List<Card> playedCards,
        boolean submitted,
        List<String> jokers,
        List<String> offers,
        int rerolls,
        boolean ready,
        boolean rematchWanted
) {
    public PlayerView {
        hand = List.copyOf(hand);
        playedCards = List.copyOf(playedCards);
        jokers = List.copyOf(jokers);
        offers = List.copyOf(offers);
    }
}
