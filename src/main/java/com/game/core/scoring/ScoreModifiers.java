package com.game.core.scoring;

/**
 * Modificadores de rodada aplicados pela sabotagem do oponente.
 *
 * <p>{@code nullifyJokers} desativa todos os jokers jogados (Névoa). A sabotagem que desativava um
 * único joker foi removida do catálogo.</p>
 */
public record ScoreModifiers(
        boolean blockFlush,
        int multPenalty,
        boolean pairTax,
        boolean nullifyJokers
) {

    public static final ScoreModifiers NONE = new ScoreModifiers(false, 0, false, false);

    public ScoreModifiers {
        if (multPenalty < 0) {
            throw new IllegalArgumentException("multPenalty não pode ser negativo");
        }
    }

    public ScoreModifiers withBlockFlush() {
        return new ScoreModifiers(true, multPenalty, pairTax, nullifyJokers);
    }

    public ScoreModifiers withMultPenalty(int amount) {
        return new ScoreModifiers(blockFlush, multPenalty + amount, pairTax, nullifyJokers);
    }

    public ScoreModifiers withPairTax() {
        return new ScoreModifiers(blockFlush, multPenalty, true, nullifyJokers);
    }

    public ScoreModifiers withNullifyJokers() {
        return new ScoreModifiers(blockFlush, multPenalty, pairTax, true);
    }
}
