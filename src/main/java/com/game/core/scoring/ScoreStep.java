package com.game.core.scoring;

/**
 * Um passo atômico da contagem, com os totais acumulados DEPOIS do passo. Os deltas são os
 * valores brutos aplicados; {@code chipsAfter}/{@code multAfter} nunca ficam negativos, então o
 * último passo sempre coincide com os totais finais de {@link ScoreResult}.
 *
 * @param cardIndex  índice da carta na jogada (somente para {@link StepKind#CARD}), senão -1
 * @param sourceId   id do joker/sabotagem/modificador global, ou o código da carta
 */
public record ScoreStep(
        StepKind kind,
        int cardIndex,
        String sourceId,
        int chipsDelta,
        int multDelta,
        double multFactor,
        int chipsAfter,
        int multAfter,
        String label
) {
    public ScoreStep {
        label = label == null ? "" : label;
    }
}
