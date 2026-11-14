package com.game.core.scoring;

/**
 * Modificadores de rodada aplicados pela sabotagem do oponente.
 *
 * <p>{@code disabledJokerIndex} desativa um único joker (índice na lista do jogador) e
 * {@code nullifyJokers} desativa todos (Névoa).</p>
 */
public record ScoreModifiers(
        boolean blockFlush,
        int multPenalty,
        boolean pairTax,
        boolean nullifyJokers,
        int disabledJokerIndex
) {

    public static final ScoreModifiers NONE = new ScoreModifiers(false, 0, false, false, -1);

    public ScoreModifiers {
        if (multPenalty < 0) {
            throw new IllegalArgumentException("multPenalty não pode ser negativo");
        }
    }

    public ScoreModifiers withBlockFlush() {
        return new ScoreModifiers(true, multPenalty, pairTax, nullifyJokers, disabledJokerIndex);
    }

    public ScoreModifiers withMultPenalty(int amount) {
        return new ScoreModifiers(blockFlush, multPenalty + amount, pairTax, nullifyJokers,
                disabledJokerIndex);
    }

    public ScoreModifiers withPairTax() {
        return new ScoreModifiers(blockFlush, multPenalty, true, nullifyJokers, disabledJokerIndex);
    }

    public ScoreModifiers withNullifyJokers() {
        return new ScoreModifiers(blockFlush, multPenalty, pairTax, true, disabledJokerIndex);
    }

    public ScoreModifiers withDisabledJoker(int index) {
        return new ScoreModifiers(blockFlush, multPenalty, pairTax, nullifyJokers, index);
    }
}
