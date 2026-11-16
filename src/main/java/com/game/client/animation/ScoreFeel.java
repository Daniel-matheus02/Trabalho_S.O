package com.game.client.animation;

import com.game.core.scoring.StepKind;

/**
 * Única fonte dos tempos e das intensidades do tremor da contagem. Os limiares foram calibrados
 * com partidas CPU-vs-CPU (mediana ≈150, p90 ≈528, p99 ≈1144).
 */
public final class ScoreFeel {

    public static final float HAND_BASE_BEAT = 0.01f;
    public static final float CARD_BEAT = 0.32f;
    public static final float EFFECT_BEAT = 0.45f;
    public static final float FINAL_PAUSE = 0.25f;
    public static final float CARD_POP = 0.25f;
    public static final float CARD_POP_UP = 0.05f;
    public static final float CARD_WOBBLE_DEGREES = 4f;
    public static final float CHIPS_ROLL = 0.2f;
    public static final float FINAL_COUNT = 0.6f;
    public static final float POPUP_RISE = 0.55f;

    public static final int SHAKE_TIER_2 = 200;
    public static final int SHAKE_TIER_3 = 600;
    public static final float[] SHAKE_AMPLITUDE = {0f, 4f, 8f, 14f};
    public static final float[] SHAKE_STEP_SECONDS = {0f, 0.04f, 0.06f, 0.08f};

    private ScoreFeel() {
    }

    public static float durationFor(StepKind kind) {
        if (kind == StepKind.HAND_BASE) {
            return HAND_BASE_BEAT;
        }
        return kind == StepKind.CARD ? CARD_BEAT : EFFECT_BEAT;
    }

    /** 0 = sem tremor; 1..3 = três faixas proporcionais à pontuação final. */
    public static int shakeLevelFor(int finalScore) {
        if (finalScore <= 0) {
            return 0;
        }
        if (finalScore < SHAKE_TIER_2) {
            return 1;
        }
        if (finalScore < SHAKE_TIER_3) {
            return 2;
        }
        return 3;
    }
}
