package com.game.core.model;

/**
 * Categorias de mão de pôquer com os valores exatos de fichas/multiplicadores base do jogo de
 * referência. A ordem de declaração é a ordem de força do protótipo e é usada por
 * {@link #atLeast(HandType)} nas condições de curinga.
 */
public enum HandType {
    HIGH_CARD("Carta Alta", 5, 1),
    PAIR("Par", 10, 2),
    TWO_PAIR("Dois Pares", 20, 2),
    THREE_OF_A_KIND("Trinca", 30, 3),
    STRAIGHT("Sequ\u00eancia", 30, 4),
    FLUSH("Flush", 35, 4),
    FULL_HOUSE("Full House", 40, 4),
    FOUR_OF_A_KIND("Quadra", 60, 7),
    STRAIGHT_FLUSH("Straight Flush", 100, 8);

    private final String displayName;
    private final int baseChips;
    private final int baseMultiplier;

    HandType(String displayName, int baseChips, int baseMultiplier) {
        this.displayName = displayName;
        this.baseChips = baseChips;
        this.baseMultiplier = baseMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public int baseChips() {
        return baseChips;
    }

    public int baseMultiplier() {
        return baseMultiplier;
    }

    /** Verdadeiro quando este tipo de mão é pelo menos tão forte quanto {@code other} na ordem do protótipo. */
    public boolean atLeast(HandType other) {
        return ordinal() >= other.ordinal();
    }

    public boolean isFlushFamily() {
        return this == FLUSH || this == STRAIGHT_FLUSH;
    }

    public boolean isStraightFamily() {
        return this == STRAIGHT || this == STRAIGHT_FLUSH;
    }
}
