package com.game.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Construção e embaralhamento do baralho. */
public final class Deck {

    private static final Rank[] RANKS = Rank.values();

    private Deck() {
    }

    /** Um baralho completo de 52 cartas em ordem de naipe e depois de valor, exatamente como o baralho de referência. */
    public static List<Card> ordered() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : RANKS) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    /** Embaralhamento Fisher-Yates, igual à implementação de referência. */
    public static List<Card> shuffled(Random random) {
        List<Card> deck = ordered();
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
        return deck;
    }
}
