package com.game.core.model;

/**
 * Valores das cartas na ordem usada pelo protótipo. Os códigos seguem a especificação
 * original ({@code T} para dez), enquanto {@link #display()} é o que a UI mostra.
 */
public enum Rank {
    ACE("A", "A", 14, 11),
    TWO("2", "2", 2, 2),
    THREE("3", "3", 3, 3),
    FOUR("4", "4", 4, 4),
    FIVE("5", "5", 5, 5),
    SIX("6", "6", 6, 6),
    SEVEN("7", "7", 7, 7),
    EIGHT("8", "8", 8, 8),
    NINE("9", "9", 9, 9),
    TEN("T", "10", 10, 10),
    JACK("J", "J", 11, 10),
    QUEEN("Q", "Q", 12, 10),
    KING("K", "K", 13, 10);

    private final String code;
    private final String display;
    private final int value;
    private final int chipValue;

    Rank(String code, String display, int value, int chipValue) {
        this.code = code;
        this.display = display;
        this.value = value;
        this.chipValue = chipValue;
    }

    public String code() {
        return code;
    }

    public String display() {
        return display;
    }

    /** Valor de sequência/comparação (ás alto = 14). */
    public int value() {
        return value;
    }

    /** Fichas contribuídas por esta carta quando ela é jogada. */
    public int chipValue() {
        return chipValue;
    }

    public static Rank fromCode(String code) {
        for (Rank rank : values()) {
            if (rank.code.equals(code)) {
                return rank;
            }
        }
        throw new IllegalArgumentException("Código de valor desconhecido: " + code);
    }
}
