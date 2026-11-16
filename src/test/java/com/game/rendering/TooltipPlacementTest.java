package com.game.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipPlacementTest {

    private static final float MARGIN = 8f;
    private static final float GAP = 10f;
    private static final float INSET = 18f;

    @Test
    void prefersAboveWhenThereIsRoom() {
        TooltipPlacement.Placement p = TooltipPlacement.place(
                100f, 100f, 50f, 70f, 200f, 100f, 800f, 600f, MARGIN, GAP, INSET);
        assertTrue(p.above());
        assertEquals(180f, p.y());
        assertEquals(25f, p.x());
        assertEquals(100f, p.pointerX());
    }

    @Test
    void flipsBelowWhenTheTopWouldOverflow() {
        TooltipPlacement.Placement p = TooltipPlacement.place(
                100f, 560f, 50f, 70f, 200f, 100f, 800f, 600f, MARGIN, GAP, INSET);
        assertFalse(p.above());
        assertEquals(450f, p.y());
    }

    @Test
    void clampsHorizontallyAndKeepsThePointerInside() {
        TooltipPlacement.Placement p = TooltipPlacement.place(
                700f, 100f, 50f, 70f, 200f, 100f, 800f, 600f, MARGIN, GAP, INSET);
        assertEquals(592f, p.x());
        assertEquals(133f, p.pointerX());
    }

    @Test
    void clampsToTheLeftMargin() {
        TooltipPlacement.Placement p = TooltipPlacement.place(
                0f, 100f, 50f, 70f, 200f, 100f, 800f, 600f, MARGIN, GAP, INSET);
        assertEquals(MARGIN, p.x());
        assertEquals(INSET, p.pointerX());
    }

    @Test
    void tinyPanelCentersThePointer() {
        TooltipPlacement.Placement p = TooltipPlacement.place(
                100f, 100f, 50f, 70f, 20f, 100f, 800f, 600f, MARGIN, GAP, INSET);
        assertEquals(10f, p.pointerX());
    }
}
