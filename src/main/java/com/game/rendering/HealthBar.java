package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.game.assets.GameAssets;

/** Barra de vida com animação suave, flash de dano e marcas de segmentos. */
public final class HealthBar extends Actor {

    private final GameAssets assets;
    private final Color color;
    private final Color tmp = new Color();

    private int hp = 1;
    private int maxHp = 1;
    private float displayed = 1f;
    private float flash;
    private boolean animate = true;

    public HealthBar(GameAssets assets, Color color) {
        this.assets = assets;
        this.color = new Color(color);
        setTouchable(Touchable.disabled);
    }

    public void setValues(int hp, int maxHp, boolean animate) {
        if (maxHp <= 0) {
            maxHp = 1;
        }
        if (this.hp != hp && hp < this.hp) {
            flash = 1f;
        }
        this.hp = Math.max(0, hp);
        this.maxHp = maxHp;
        this.animate = animate;
        if (!animate) {
            displayed = target();
        }
    }

    public void flash() {
        flash = 1f;
    }

    private float target() {
        return MathUtils.clamp(hp / (float) maxHp, 0f, 1f);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (animate) {
            displayed = MathUtils.lerp(displayed, target(), Math.min(1f, delta * 5.5f));
        } else {
            displayed = target();
        }
        flash = Math.max(0f, flash - delta * 2.2f);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float alpha = parentAlpha * getColor().a;

        tmp.set(1f, 1f, 1f, alpha);
        Draws.patch(batch, assets.patch("barBg"), x, y, w, h, tmp);

        float fillWidth = Math.max(0f, (w - 2f) * displayed);
        if (fillWidth > 1f) {
            tmp.set(color.r, color.g, color.b, alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 1f, y + 1f, fillWidth, h - 2f, tmp);
            // linha de realce sutil
            tmp.set(1f, 1f, 1f, 0.18f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 2f, y + h * 0.58f,
                    Math.max(0f, fillWidth - 3f), h * 0.28f, tmp);
        }

        // marcas de segmentos
        batch.setColor(0f, 0f, 0f, 0.35f * alpha);
        float step = w / 5f;
        for (int i = 1; i < 5; i++) {
            batch.draw(assets.region("white"), x + step * i, y + 1f, 1f, h - 2f);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);

        if (flash > 0f) {
            tmp.set(1f, 1f, 1f, flash * 0.45f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x, y, w, h, tmp);
        }

        // brilho de aviso de vida baixa
        if (displayed <= 0.25f) {
            float pulse = 0.12f + MathUtils.sin((flash + 1f) * 2f) * 0.05f;
            tmp.set(Palette.BAD.r, Palette.BAD.g, Palette.BAD.b, pulse * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x - 1f, y - 1f, w + 2f, h + 2f, tmp);
        }
    }
}
