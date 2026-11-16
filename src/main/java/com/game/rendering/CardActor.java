package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

/**
 * Actor de carta desenhado à mão: índice nos dois cantos, naipe central com halo, hover,
 * elevação de seleção, giro de revelação (verso → frente) e fagulhas ao ser mostrada.
 */
public final class CardActor extends HandCardActor {

    private final Card card;
    private final Matrix4 storedTransform = new Matrix4();
    private final Matrix4 flipTransform = new Matrix4();

    private final Color tmp = new Color();

    public CardActor(GameAssets assets, Card card, boolean faceDown, Runnable onClick) {
        super(assets, faceDown || card == null, onClick);
        this.card = card;
    }

    public Card card() {
        return card;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float w = getWidth();
        float h = getHeight();
        float alpha = parentAlpha * getColor().a * appear;
        if (alpha <= 0.01f || w <= 0f || h <= 0f) {
            return;
        }
        boolean back = faceDown || reveal < 0.5f;
        boolean small = w < 46f;
        NinePatch patch = assets.patch(back
                ? (small ? "cardBackSmall" : "cardBack")
                : (small ? "cardFaceSmall" : "cardFace"));

        float flip = reveal >= 1f ? 1f : Math.abs(MathUtils.cos(reveal * MathUtils.PI));
        flip *= Math.abs(getScaleX());
        float hop = dealMotion && reveal < 1f ? MathUtils.sin(reveal * MathUtils.PI) * 6f : 0f;
        float drop = dealMotion && reveal < 1f ? (1f - reveal) * 26f : 0f;
        float y = getY() + lift + hop - drop;
        float centerX = getX() + w / 2f;
        float centerY = y + h / 2f;

        boolean squished = flip < 0.999f;
        float rotation = getRotation();
        boolean transformed = squished || rotation != 0f;
        if (transformed) {
            storedTransform.set(batch.getTransformMatrix());
            flipTransform.set(storedTransform);
            flipTransform.translate(centerX, centerY, 0f);
            if (rotation != 0f) {
                flipTransform.rotate(0f, 0f, 1f, rotation);
            }
            flipTransform.scale(flip, 1f, 1f);
            flipTransform.translate(-centerX, -centerY, 0f);
            batch.setTransformMatrix(flipTransform);
        }

        if (flip > 0.02f) {
            drawBody(batch, patch, back, w, h, y, alpha);
        }

        if (transformed) {
            batch.setTransformMatrix(storedTransform);
        }

        sparkles.draw(batch, alpha);
    }

    private void drawBody(Batch batch, NinePatch patch, boolean back,
                          float w, float h, float y, float alpha) {
        float x = getX();

        // Sombra projetada
        tmp.set(0f, 0f, 0f, 0.35f * alpha);
        Draws.patch(batch, assets.patch("pillWhite"), x + 2, y - 6, w, h, tmp);

        if (selected) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float glow = 0.5f + MathUtils.sin(glowPulse * 3f) * 0.1f;
            batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, glow * alpha);
            batch.draw(assets.glow(), x - 14, y - 14, w + 28, h + 28);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(Color.WHITE);
            tmp.set(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, 0.95f * alpha);
            Draws.patch(batch, patch, x - 3, y - 3, w + 6, h + 6, tmp);
        }

        tmp.set(1f, 1f, 1f, alpha);
        Draws.patch(batch, patch, x, y, w, h, tmp);

        if (back) {
            CardVisuals.drawBack(batch, assets, x, y, w, h, alpha);
        } else {
            CardRenderer.drawFace(batch, assets, card, x, y, w, h, alpha);
        }

        if (disabled) {
            tmp.set(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b, 0.35f * alpha);
            Draws.patch(batch, patch, x, y, w, h, tmp);
        }
        if (hovered && interactive && !disabled) {
            tmp.set(1f, 1f, 1f, 0.05f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x, y, w, h, tmp);
        }
    }

    /** Fábrica de conveniência para cartas decorativas e não interativas. */
    public static CardActor decorative(GameAssets assets, Card card, boolean faceDown, float width) {
        CardActor actor = new CardActor(assets, card, faceDown, null);
        actor.setInteractive(false);
        actor.setSize(width, width * 1.38f);
        return actor;
    }
}
