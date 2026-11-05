package com.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

/** Actor de carta desenhada à mão, com hover, elevação de seleção, brilho e tipografia pixelada. */
public final class CardActor extends Actor {

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private final GameAssets assets;
    private final Card card;
    private final boolean faceDown;
    private final Color suitColor;
    private final Runnable onClick;

    private boolean selected;
    private boolean interactive;
    private boolean disabled;
    private boolean hovered;
    private float lift;
    private float appear = 0.25f;
    private float glowPulse;

    private final Color tmp = new Color();

    public CardActor(GameAssets assets, Card card, boolean faceDown, Runnable onClick) {
        this.assets = assets;
        this.card = card;
        this.faceDown = faceDown || card == null;
        this.onClick = onClick;
        this.suitColor = card == null ? Palette.GOLD : colorFor(card);
        this.interactive = onClick != null;
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

    public static Color colorFor(Card card) {
        return switch (card.suit()) {
            case SPADES -> Palette.SPADE;
            case HEARTS -> Palette.HEART;
            case DIAMONDS -> Palette.DIAMOND;
            case CLUBS -> Palette.CLUB;
        };
    }

    public Card card() {
        return card;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setTouchable(interactive ? Touchable.enabled : Touchable.disabled);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float targetLift = selected ? 16f : (hovered && interactive && !disabled ? 7f : 0f);
        lift = MathUtils.lerp(lift, targetLift, Math.min(1f, delta * 14f));
        appear = MathUtils.lerp(appear, 1f, Math.min(1f, delta * 7f));
        glowPulse += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY() + lift;
        float w = getWidth();
        float h = getHeight();
        float alpha = parentAlpha * getColor().a * appear;
        if (alpha <= 0.01f) {
            return;
        }
        boolean small = w < 46f;
        NinePatch patch = assets.patch(faceDown
                ? (small ? "cardBackSmall" : "cardBack")
                : (small ? "cardFaceSmall" : "cardFace"));

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

        if (faceDown) {
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
        BitmapFont font = assets.font(w > 74 ? 2 : 1);
        float pad = Math.max(6f, w * 0.08f);
        tmp.set(suitColor.r, suitColor.g, suitColor.b, alpha);
        font.setColor(tmp);
        String rank = card.rank().display();
        LAYOUT.setText(font, rank);
        font.draw(batch, LAYOUT, x + pad, y + h - pad);

        float icon = Math.max(11f, w * 0.2f);
        batch.setColor(suitColor.r, suitColor.g, suitColor.b, alpha);
        batch.draw(assets.region("suit-" + card.suit().code()),
                x + pad, y + h - pad - LAYOUT.height - icon - 2f, icon, icon);

        float big = Math.min(w * 0.44f, h * 0.32f);
        batch.setColor(suitColor.r, suitColor.g, suitColor.b, alpha * 0.9f);
        batch.draw(assets.region("suit-" + card.suit().code()),
                x + (w - big) / 2f, y + h * 0.32f + (h * 0.36f - big) / 2f, big, big);
        batch.setColor(Color.WHITE);
    }

    private void drawBack(Batch batch, float x, float y, float w, float h, float alpha) {
        float emblem = Math.min(w * 0.4f, h * 0.28f);
        batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, alpha * 0.65f);
        batch.draw(assets.region("star"), x + (w - emblem) / 2f, y + (h - emblem) / 2f,
                emblem, emblem);
        // detalhes nos cantos superior e inferior
        float stud = Math.max(2f, w * 0.05f);
        float inset = Math.max(4f, w * 0.09f);
        batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, alpha * 0.5f);
        batch.draw(assets.region("white"), x + inset, y + h - inset - stud, stud, stud);
        batch.draw(assets.region("white"), x + w - inset - stud, y + inset, stud, stud);
        batch.setColor(Color.WHITE);
    }

    /** Fábrica de conveniência para cartas decorativas e não interativas. */
    public static CardActor decorative(GameAssets assets, Card card, boolean faceDown, float width) {
        CardActor actor = new CardActor(assets, card, faceDown, null);
        actor.setInteractive(false);
        actor.setSize(width, width * 1.38f);
        return actor;
    }
}
