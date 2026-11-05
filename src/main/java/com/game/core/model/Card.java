package com.game.core.model;

import java.util.Objects;

/** Uma única carta de baralho imutável. */
public record Card(Rank rank, Suit suit) implements Comparable<Card> {

    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    /** Representação compacta para rede, ex.: {@code AE} (ás de espadas) ou {@code TC} (dez de copas). */
    public String code() {
        return rank.code() + suit.code();
    }

    public static Card fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("O código da carta é nulo");
        }
        String trimmed = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (trimmed.length() != 2) {
            throw new IllegalArgumentException("Código de carta inválido: " + code);
        }
        return new Card(Rank.fromCode(trimmed.substring(0, 1)), Suit.fromCode(trimmed.substring(1, 2)));
    }

    @Override
    public int compareTo(Card other) {
        int byValue = Integer.compare(rank.value(), other.rank.value());
        return byValue != 0 ? byValue : suit.compareTo(other.suit);
    }

    @Override
    public String toString() {
        return rank.display() + suit.symbol();
    }
}
