package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

/**
 * Actor de carta desenhado à mão: índice nos dois cantos, naipe central com halo, hover,
 * elevação de seleção, giro de revelação (verso → frente) e fagulhas ao ser mostrada.
 */
public final class CardActor extends Actor {

    private final GameAssets assets;
    private final Card card;
    private final boolean faceDown;
    private final Runnable onClick;
    private final Particles sparkles;
    private final Matrix4 storedTransform = new Matrix4();
    private final Matrix4 flipTransform = new Matrix4();

    private boolean selected;
    private boolean interactive;
    private boolean disabled;
    private boolean hovered;
    private float lift;
    private float appear = 0.25f;
    private float glowPulse;
    private float reveal = 1f;
    private float revealDelay;
    private float revealSpeed = 3f;
    private boolean revealing;
    private boolean sparkled = true;

    private final Color tmp = new Color();

    public CardActor(GameAssets assets, Card card, boolean faceDown, Runnable onClick) {
        this.assets = assets;
        this.card = card;
        this.faceDown = faceDown || card == null;
        this.onClick = onClick;
        this.interactive = onClick != null;
        this.sparkles = new Particles(assets, true);
        setTouchable(interactive ? Touchable.enabled : Touchable.disabled);
        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (interactive && !disabled) {
                    hovered = true;
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                hovered = false;
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (interactive && !disabled && onClick != null) {
                    onClick.run();
                }
            }
        });
    }

    /** Delega para o renderizador central: a cor vem sempre do naipe da própria carta. */
    public static Color colorFor(Card card) {
        return CardRenderer.suitColor(card);
    }

    public Card card() {
        return card;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        boolean becameSelected = selected && !this.selected;
        this.selected = selected;
        if (becameSelected && getWidth() > 0f) {
            sparkles.sparkles(getX() + getWidth() / 2f, getY() + getHeight() * 0.58f,
                    getWidth() * 0.55f, 9);
        }
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setTouchable(interactive ? Touchable.enabled : Touchable.disabled);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /** Inicia a revelação da carta (verso → frente), com atraso e duração em segundos. */
    public void startDeal(float delay, float duration) {
        reveal = 0f;
        revealDelay = Math.max(0f, delay);
        revealSpeed = 1f / Math.max(0.08f, duration);
        revealing = true;
        sparkled = false;
    }

    /** Mostra a carta sem animação de distribuição. */
    public void showInstantly() {
        reveal = 1f;
        revealDelay = 0f;
        revealing = false;
        sparkled = true;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float targetLift = selected ? 16f : (hovered && interactive && !disabled ? 7f : 0f);
        lift = MathUtils.lerp(lift, targetLift, Math.min(1f, delta * 14f));
        appear = MathUtils.lerp(appear, 1f, Math.min(1f, delta * 7f));
        glowPulse += delta;

        if (revealing) {
            if (revealDelay > 0f) {
                revealDelay -= delta;
            } else {
                reveal = Math.min(1f, reveal + delta * revealSpeed);
                if (reveal >= 1f) {
                    revealing = false;
                }
            }
            if (!sparkled && reveal >= 0.55f && getWidth() > 0f) {
                sparkled = true;
                int count = MathUtils.clamp((int) (getWidth() / 11f), 5, 12);
                sparkles.sparkles(getX() + getWidth() / 2f, getY() + getHeight() * 0.55f,
                        getWidth() * 0.5f, count);
            }
        }
        sparkles.update(delta);
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
        float hop = reveal < 1f ? MathUtils.sin(reveal * MathUtils.PI) * 6f : 0f;
        float drop = reveal < 1f ? (1f - reveal) * 26f : 0f;
        float y = getY() + lift + hop - drop;
        float centerX = getX() + w / 2f;
        float centerY = y + h / 2f;

        boolean squished = flip < 0.999f;
        if (squished) {
            storedTransform.set(batch.getTransformMatrix());
            flipTransform.set(storedTransform);
            flipTransform.translate(centerX, centerY, 0f);
            flipTransform.scale(flip, 1f, 1f);
            flipTransform.translate(-centerX, -centerY, 0f);
            batch.setTransformMatrix(flipTransform);
        }

        if (flip > 0.02f) {
            drawBody(batch, patch, back, w, h, y, alpha);
        }

        if (squished) {
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
            drawBack(batch, x, y, w, h, alpha);
        } else {
            drawFace(batch, x, y, w, h, alpha);
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

    private void drawFace(Batch batch, float x, float y, float w, float h, float alpha) {
        CardRenderer.drawFace(batch, assets, card, x, y, w, h, alpha);
    }

    private void drawBack(Batch batch, float x, float y, float w, float h, float alpha) {
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
        drawDiamond(batch, x + inset, y + h - inset - stud, stud);
        drawDiamond(batch, x + w - inset - stud, y + inset, stud);
        batch.setColor(Color.WHITE);
    }

    private void drawDiamond(Batch batch, float x, float y, float size) {
        batch.draw(assets.region("white"), x, y, size / 2f, size / 2f, size, size,
                1f, 1f, 45f);
    }

    /** Fábrica de conveniência para cartas decorativas e não interativas. */
    public static CardActor decorative(GameAssets assets, Card card, boolean faceDown, float width) {
        CardActor actor = new CardActor(assets, card, faceDown, null);
        actor.setInteractive(false);
        actor.setSize(width, width * 1.38f);
        return actor;
    }
}
