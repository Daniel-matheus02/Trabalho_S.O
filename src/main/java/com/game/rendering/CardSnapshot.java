package com.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

/**
 * Fotografia de uma carta em um FrameBuffer RGBA8888 transparente. A carta é desenhada uma única
 * vez com os mesmos NinePatches/CardRenderer do actor; o shader de dissolução atua sobre esta
 * textura (com Y invertido), nunca sobre os vários draw calls originais.
 */
public final class CardSnapshot implements Disposable {

    private final FrameBuffer framebuffer;
    private final TextureRegion region;

    private CardSnapshot(FrameBuffer framebuffer, TextureRegion region) {
        this.framebuffer = framebuffer;
        this.region = region;
    }

    public static CardSnapshot capture(GameAssets assets, Card card, boolean faceDown,
                                       float width, float height) {
        int w = Math.max(1, Math.round(width));
        int h = Math.max(1, Math.round(height));
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        SpriteBatch batch = new SpriteBatch();
        try {
            buffer.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.getProjectionMatrix().setToOrtho2D(0f, 0f, w, h);
            batch.begin();
            batch.setColor(Color.WHITE);
            NinePatch patch = assets.patch(faceDown
                    ? (w < 46 ? "cardBackSmall" : "cardBack")
                    : (w < 46 ? "cardFaceSmall" : "cardFace"));
            patch.draw(batch, 0f, 0f, w, h);
            if (faceDown) {
                CardVisuals.drawBack(batch, assets, 0f, 0f, w, h, 1f);
            } else {
                CardRenderer.drawFace(batch, assets, card, 0f, 0f, w, h, 1f);
            }
            batch.end();
        } finally {
            buffer.end();
            batch.dispose();
            HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        TextureRegion region = new TextureRegion(buffer.getColorBufferTexture());
        region.flip(false, true);
        return new CardSnapshot(buffer, region);
    }

    public TextureRegion region() {
        return region;
    }

    @Override
    public void dispose() {
        framebuffer.dispose();
    }
}
