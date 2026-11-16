package com.game.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometria do leque compartilhada pelas mãos de cartas e de poderes. */
class HandFanRotationTest {

    @Test
    void singleCardIsStraightAndEmptyHandsHaveNoTilt() {
        assertEquals(0f, HandCardActor.fanAngleAt(0, 1));
        assertEquals(0f, HandCardActor.fanAngleAt(0, 0));
        assertEquals(0f, HandCardActor.maxFanTilt(1));
    }

    @Test
    void fanIsSymmetricAndTiltsOutwards() {
        int count = 8;
        float left = HandCardActor.fanAngleAt(0, count);
        float right = HandCardActor.fanAngleAt(count - 1, count);
        // No palco do libGDX a rotação positiva é anti-horária: a carta da esquerda abre para
        // fora com ângulo positivo e a da direita com o mesmo ângulo negativo.
        assertTrue(left > 0f, "a carta da esquerda deve abrir para a esquerda");
        assertEquals(-right, left, 1e-4f);
        assertEquals(HandCardActor.maxFanTilt(count), left, 1e-4f);
        assertEquals(0f, HandCardActor.fanAngleAt(count / 2 - 1, count)
                + HandCardActor.fanAngleAt(count / 2, count), 1e-4f,
                "as duas cartas centrais apontam para lados opostos");
    }

    @Test
    void largeHandsClampTheEndCards() {
        int count = 20;
        assertEquals(HandCardActor.FAN_MAX_TILT, HandCardActor.maxFanTilt(count), 1e-4f);
        assertEquals(HandCardActor.FAN_MAX_TILT, HandCardActor.fanAngleAt(0, count), 1e-4f);
        assertEquals(-HandCardActor.FAN_MAX_TILT,
                HandCardActor.fanAngleAt(count - 1, count), 1e-4f);
    }
}
