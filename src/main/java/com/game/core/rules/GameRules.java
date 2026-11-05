package com.game.core.rules;

/** Regras ajustáveis do duelo. Os valores padrão espelham exatamente o protótipo de referência. */
public record GameRules(int startingHp, int handSize, int maxPlay, int damageDivisor, int rerollsPerMatch) {

    public static final GameRules DEFAULT = new GameRules(300, 8, 5, 3, 2);

    public GameRules {
        if (startingHp <= 0) {
            throw new IllegalArgumentException("startingHp deve ser positivo");
        }
        if (handSize <= 0 || handSize > 26) {
            throw new IllegalArgumentException("handSize fora do intervalo");
        }
        if (maxPlay <= 0 || maxPlay > handSize) {
            throw new IllegalArgumentException("maxPlay deve estar entre 1 e handSize");
        }
        if (damageDivisor <= 0) {
            throw new IllegalArgumentException("damageDivisor deve ser positivo");
        }
        if (rerollsPerMatch < 0) {
            throw new IllegalArgumentException("rerollsPerMatch não pode ser negativo");
        }
    }
}
