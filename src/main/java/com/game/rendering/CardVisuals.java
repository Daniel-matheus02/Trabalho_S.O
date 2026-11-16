package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.game.assets.GameAssets;

/** Desenho compartilhado do verso de qualquer carta (baralho ou poder). */
final class CardVisuals {

    private CardVisuals() {
    }

    static void drawBack(Batch batch, GameAssets assets, float x, float y,
                         float w, float h, float alpha) {
        float centerX = x + w / 2f;
        float centerY = y + h / 2f;
        float emblem = Math.min(w * 0.34f, h * 0.24f);

        // Halo dourado atrás do emblema.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, 0.22f * alpha);
        float halo = emblem * 2.3f;
        batch.draw(assets.glow(), centerX - halo / 2f, centerY - halo / 2f, halo, halo);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, 0.85f * alpha);
        batch.draw(assets.region("star"), centerX - emblem / 2f, centerY - emblem / 2f,
                emblem, emblem);

        // Rebites em forma de losango, sem círculos.
        float stud = Math.max(3f, w * 0.055f);
        float inset = Math.max(4f, w * 0.10f);
        batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, 0.55f * alpha);
        drawDiamond(batch, assets, x + inset, y + h - inset - stud, stud);
        drawDiamond(batch, assets, x + w - inset - stud, y + inset, stud);
        batch.setColor(Color.WHITE);
    }

    private static void drawDiamond(Batch batch, GameAssets assets, float x, float y, float size) {
        batch.draw(assets.region("white"), x, y, size / 2f, size / 2f, size, size,
                1f, 1f, 45f);
    }
}
