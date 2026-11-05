package com.game.core.model;

/**
 * Naipes das cartas. Os códigos correspondem ao protótipo (E = espadas, C = copas, O = ouros,
 * P = paus) para que as mensagens de rede permaneçam compatíveis com a notação de referência.
 */
public enum Suit {
    SPADES("E", "\u2660", "Espadas"),
    HEARTS("C", "\u2665", "Copas"),
    DIAMONDS("O", "\u2666", "Ouros"),
    CLUBS("P", "\u2663", "Paus");

    private final String code;
    private final String symbol;
    private final String displayName;

    Suit(String code, String symbol, String displayName) {
        this.code = code;
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String symbol() {
        return symbol;
    }

    public String displayName() {
        return displayName;
    }

    public static Suit fromCode(String code) {
        for (Suit suit : values()) {
            if (suit.code.equals(code)) {
                return suit;
            }
        }
        throw new IllegalArgumentException("Código de naipe desconhecido: " + code);
    }
}
