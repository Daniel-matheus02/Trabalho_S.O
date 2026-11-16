package com.game.client.animation;

import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoreStep;
import com.game.core.scoring.StepKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte a decomposição de uma pontuação em batidas de animação. Java puro: nenhuma dependência
 * de libGDX. Cada lado recebe uma linha do tempo independente; o último beat é o fechamento
 * {@code fichas × mult = pontos}, com a mesma pontuação de {@link ScoreResult}.
 */
public final class ScoreCountTimeline {

    /**
     * @param timeOffset segundos desde o início da contagem
     * @param step       passo que originou a batida (no beat final, repete o último passo)
     * @param popupText  texto flutuante
     * @param colorKey   "chips", "mult" ou "neutral"
     * @param shakeLevel 0..3 (somente o beat final usa o nível calculado)
     */
    public record Beat(float timeOffset, ScoreStep step, String popupText, String colorKey,
                       int shakeLevel) {
    }

    private ScoreCountTimeline() {
    }

    public static List<Beat> build(ScoreResult result) {
        if (result == null || result.steps().isEmpty()) {
            return List.of();
        }
        List<Beat> beats = new ArrayList<>();
        float offset = 0f;
        for (ScoreStep step : result.steps()) {
            beats.add(new Beat(offset, step, popupFor(step), colorFor(step), 0));
            offset += ScoreFeel.durationFor(step.kind());
        }
        ScoreStep last = result.steps().get(result.steps().size() - 1);
        float finalOffset = offset + ScoreFeel.FINAL_PAUSE;
        beats.add(new Beat(finalOffset, last, Integer.toString(result.finalScore()), "neutral",
                ScoreFeel.shakeLevelFor(result.finalScore())));
        return List.copyOf(beats);
    }

    /** Duração total até o fim da contagem do placar (0,6 s após o beat final). */
    public static float totalDuration(ScoreResult result) {
        List<Beat> beats = build(result);
        if (beats.isEmpty()) {
            return 0f;
        }
        return beats.get(beats.size() - 1).timeOffset() + ScoreFeel.FINAL_COUNT;
    }

    private static String popupFor(ScoreStep step) {
        if (step.kind() == StepKind.HAND_BASE) {
            return step.label();
        }
        if (step.chipsDelta() != 0) {
            return signed(step.chipsDelta());
        }
        if (step.multDelta() != 0) {
            return signed(step.multDelta()) + " MULT";
        }
        if (step.multFactor() != 1.0) {
            return "×" + formatFactor(step.multFactor());
        }
        return step.kind() == StepKind.SABOTAGE ? "\u2212" : "0";
    }

    private static String colorFor(ScoreStep step) {
        if (step.kind() == StepKind.SABOTAGE) {
            return step.chipsDelta() != 0 ? "chips" : "mult";
        }
        if (step.chipsDelta() != 0) {
            return "chips";
        }
        if (step.multDelta() != 0 || step.multFactor() != 1.0) {
            return "mult";
        }
        return "neutral";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String formatFactor(double factor) {
        if (factor == Math.rint(factor)) {
            return Integer.toString((int) factor);
        }
        return Double.toString(factor);
    }
}
