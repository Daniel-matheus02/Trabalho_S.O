package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;

/** Auxiliares de desenho para nine-patches tingidos (o libGDX não tem sobrecarga de cor em NinePatch). */
public final class Draws {

    private Draws() {
    }

    public static void patch(Batch batch, NinePatch patch, float x, float y, float width,
                             float height, Color color) {
        batch.setColor(color);
        patch.draw(batch, x, y, width, height);
        batch.setColor(Color.WHITE);
    }
}
