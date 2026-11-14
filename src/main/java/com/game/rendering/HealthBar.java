package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.game.assets.GameAssets;

/**
 * Barra de vida com animação suave, rastro de dano, tremor, flash e uma explosão de brasas
 * quando a vida cai. O rastro fantasma mostra o trecho perdido antes de recolher.
 */
public final class HealthBar extends Actor {

    private final GameAssets assets;
    private final Color color;
    private final Color tmp = new Color();
    private final Particles particles;

    private int hp = 1;
    private int maxHp = 1;
    private float displayed = 1f;
    private float ghost = 1f;
    private float ghostDelay;
    private float flash;
    private float shake;
    private float pulseTime;
    private boolean animate = true;
    private boolean effectsEnabled = true;
    private boolean initialised;

    public HealthBar(GameAssets assets, Color color) {
        this.assets = assets;
        this.color = new Color(color);
        this.particles = new Particles(assets, false);
        setTouchable(Touchable.disabled);
    }

    /** Desliga tremor, rastro fantasma e partículas, mantendo apenas a transição da barra. */
    public void setEffectsEnabled(boolean enabled) {
        this.effectsEnabled = enabled;
        if (!enabled) {
            particles.clear();
            ghost = displayed;
            shake = 0f;
        }
    }

    public void setValues(int hp, int maxHp, boolean animate) {
        if (maxHp <= 0) {
            maxHp = 1;
        }
        if (!initialised) {
            initialised = true;
            this.hp = Math.max(0, hp);
            this.maxHp = maxHp;
            this.animate = animate;
            displayed = target();
            ghost = displayed;
            return;
        }
        if (this.hp != hp && hp < this.hp) {
            int lost = this.hp - hp;
            flash = 1f;
            ghostDelay = 0.3f;
            shake = effectsEnabled ? 1f : 0f;
            if (animate && effectsEnabled) {
                burst(lost);
            }
        }
        this.hp = Math.max(0, hp);
        this.maxHp = maxHp;
        this.animate = animate;
        if (!animate) {
            displayed = target();
            ghost = displayed;
            flash = 0f;
            shake = 0f;
            particles.clear();
        }
    }

    public void flash() {
        flash = 1f;
        shake = Math.max(shake, effectsEnabled ? 0.8f : 0f);
    }

    private float target() {
        return MathUtils.clamp(hp / (float) maxHp, 0f, 1f);
    }

    /** Explosão de brasas no bordo da vida restante, dimensionada pelo dano recebido. */
    private void burst(int lost) {
        if (getWidth() <= 0f) {
            return;
        }
        float edge = getX() + 1f + (getWidth() - 2f) * displayed;
        float centerY = getY() + getHeight() / 2f;
        int count = MathUtils.clamp(9 + lost / 3, 10, 42);
        float spread = Math.max(55f, getWidth() * 0.28f);
        particles.damage(edge, centerY, spread, count);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (animate) {
            displayed = MathUtils.lerp(displayed, target(), Math.min(1f, delta * 5.5f));
        } else {
            displayed = target();
        }
        if (ghost < displayed) {
            ghost = displayed;
        } else if (ghost > displayed) {
            if (!effectsEnabled) {
                ghost = displayed;
            } else if (ghostDelay > 0f) {
                ghostDelay -= delta;
            } else {
                ghost = MathUtils.lerp(ghost, displayed, Math.min(1f, delta * 4.2f));
                if (ghost - displayed < 0.002f) {
                    ghost = displayed;
                }
            }
        }
        flash = Math.max(0f, flash - delta * 2.2f);
        shake = Math.max(0f, shake - delta * 3.4f);
        pulseTime += delta;
        particles.update(delta);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float w = getWidth();
        float h = getHeight();
        float alpha = parentAlpha * getColor().a;
        float x = getX();
        float y = getY();
        if (shake > 0f) {
            x += MathUtils.sin(pulseTime * 62f) * shake * 2.6f;
            y += MathUtils.sin(pulseTime * 48f) * shake * 1.1f;
        }

        // fundo
        tmp.set(1f, 1f, 1f, alpha);
        Draws.patch(batch, assets.patch("barBg"), x, y, w, h, tmp);

        float fillWidth = Math.max(0f, (w - 2f) * displayed);
        float ghostWidth = Math.max(0f, (w - 2f) * ghost);

        // rastro fantasma do dano: mostra o trecho que acabou de ser perdido
        if (ghostWidth > fillWidth + 0.5f) {
            tmp.set(1f, 0.82f, 0.72f, 0.5f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 1f + fillWidth, y + 1f,
                    ghostWidth - fillWidth, h - 2f, tmp);
            tmp.set(1f, 1f, 1f, 0.55f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 1f + fillWidth, y + 1f,
                    Math.min(2.5f, ghostWidth - fillWidth), h - 2f, tmp);
        }

        if (fillWidth > 1f) {
            tmp.set(color.r, color.g, color.b, alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 1f, y + 1f, fillWidth, h - 2f, tmp);
            // brilho superior
            tmp.set(1f, 1f, 1f, 0.18f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 2f, y + h * 0.58f,
                    Math.max(0f, fillWidth - 3f), h * 0.28f, tmp);
            // sombra inferior interna
            tmp.set(0f, 0f, 0f, 0.14f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 2f, y + 2f,
                    Math.max(0f, fillWidth - 3f), Math.max(0f, h * 0.18f), tmp);
        }

        // marcas de segmentos
        batch.setColor(0f, 0f, 0f, 0.32f * alpha);
        float step = w / 5f;
        for (int i = 1; i < 5; i++) {
            batch.draw(assets.region("white"), x + step * i, y + 1f, 1f, h - 2f);
        }
        batch.setColor(Color.WHITE);

        // flash de impacto e clarão no bordo danificado
        if (flash > 0f) {
            tmp.set(1f, 1f, 1f, flash * 0.42f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x, y, w, h, tmp);
            float edge = x + 1f + fillWidth;
            tmp.set(1f, 0.92f, 0.72f, flash * 0.85f * alpha);
            batch.setColor(tmp);
            batch.draw(assets.region("white"), edge - 1.5f, y - 3f, 3f, h + 6f);
            batch.setColor(Color.WHITE);
        }

        // brilho de aviso de vida baixa
        if (displayed <= 0.25f) {
            float pulse = 0.12f + MathUtils.sin(pulseTime * 3.4f) * 0.07f;
            tmp.set(Palette.BAD.r, Palette.BAD.g, Palette.BAD.b, pulse * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x - 1f, y - 1f, w + 2f, h + 2f, tmp);
        }

        // contorno sutil para destacar a barra do painel
        tmp.set(1f, 1f, 1f, 0.08f * alpha);
        Draws.patch(batch, assets.patch("pillWhite"), x, y, w, 1f, tmp);

        particles.draw(batch, alpha);
    }
}
