package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.Jokers;

/**
 * Carta de poder: joker desenhado a partir do PNG pixel-art (letra-caixa, sem esticar) ou
 * sabotagem procedural vermelha com nome e ícone. Também desenha o verso usado na mão oculta do
 * oponente.
 */
public final class PowerCardActor extends HandCardActor {

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private final PowerCard power;
    private final Color tmp = new Color();
    private final Matrix4 storedTransform = new Matrix4();
    private final Matrix4 hoverTransform = new Matrix4();

    private CardTooltip tooltip;
    private float hoverScale = 1f;
    private float hoverTilt;
    private boolean shadow = true;

    public PowerCardActor(GameAssets assets, PowerCard power, boolean faceDown, Runnable onClick) {
        super(assets, faceDown || power == null, onClick);
        this.power = power;
    }

    public PowerCard power() {
        return power;
    }

    /** Desliga a sombra projetada (usado pela prova de pixel do joker). */
    public void setShadowEnabled(boolean shadow) {
        this.shadow = shadow;
    }

    /** Liga o tooltip global; habilita hover mesmo em cartas não clicáveis dos slots. */
    public void setTooltip(CardTooltip tooltip) {
        this.tooltip = tooltip;
        if (tooltip == null) {
            setHoverable(false);
            setHoverListener(null);
            return;
        }
        setHoverable(true);
        setHoverListener(new HoverListener() {
            @Override
            public void onHoverEnter(HandCardActor actor) {
                tooltip.show(PowerCardActor.this, tooltipTitle(), tooltipDescription());
            }

            @Override
            public void onHoverExit(HandCardActor actor) {
                tooltip.hide(PowerCardActor.this);
            }
        });
    }

    private String tooltipTitle() {
        if (power == null) {
            return "";
        }
        return power.kind() == PowerKind.JOKER
                ? Jokers.nameOf(power.id()) : Sabotages.nameOf(power.id());
    }

    private String tooltipDescription() {
        if (power == null) {
            return "";
        }
        return power.kind() == PowerKind.JOKER
                ? Jokers.descriptionOf(power.id()) : Sabotages.descriptionOf(power.id());
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        boolean hovering = hovered && isHoverable();
        float targetScale = hovering ? 1.06f : 1f;
        float targetTilt = 0f;
        if (hovering && getWidth() > 0f) {
            float normalized = (hoverLocalX - getWidth() / 2f) / (getWidth() / 2f);
            targetTilt = MathUtils.clamp(normalized, -1f, 1f) * 3f;
        }
        hoverScale = MathUtils.lerp(hoverScale, targetScale, Math.min(1f, delta * 16f));
        hoverTilt = MathUtils.lerp(hoverTilt, targetTilt, Math.min(1f, delta * 16f));
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
        String patchName;
        if (back) {
            patchName = w < 46f ? "cardBackSmall" : "cardBack";
        } else if (power.kind() == PowerKind.JOKER) {
            patchName = w < 46f ? "cardFaceSmall" : "cardFace";
        } else {
            patchName = "cardRed";
        }
        NinePatch patch = assets.patch(patchName);

        float hop = dealMotion && reveal < 1f ? MathUtils.sin(reveal * MathUtils.PI) * 6f : 0f;
        float drop = dealMotion && reveal < 1f ? (1f - reveal) * 26f : 0f;
        float x = getX();
        float y = getY() + lift + hop - drop - dealRise;
        float centerX = x + w / 2f;
        float centerY = y + h / 2f;
        boolean transformed = Math.abs(hoverScale - 1f) > 0.001f
                || Math.abs(hoverTilt) > 0.001f;
        if (transformed) {
            storedTransform.set(batch.getTransformMatrix());
            hoverTransform.set(storedTransform);
            hoverTransform.translate(centerX, centerY, 0f);
            hoverTransform.rotate(0f, 0f, 1f, hoverTilt);
            hoverTransform.scale(hoverScale, hoverScale, 1f);
            hoverTransform.translate(-centerX, -centerY, 0f);
            batch.setTransformMatrix(hoverTransform);
        }

        // Sombra projetada.
        if (shadow) {
            tmp.set(0f, 0f, 0f, 0.35f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x + 2, y - 6, w, h, tmp);
        }
        boolean jokerFace = !back && power.kind() == PowerKind.JOKER;

        // Brilho de seleção.
        if (selected) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float glow = 0.5f + MathUtils.sin(glowPulse * 3f) * 0.1f;
            batch.setColor(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, glow * alpha);
            batch.draw(assets.glow(), x - 14, y - 14, w + 28, h + 28);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(Color.WHITE);
            if (!jokerFace) {
                tmp.set(Palette.GOLD.r, Palette.GOLD.g, Palette.GOLD.b, 0.95f * alpha);
                Draws.patch(batch, patch, x - 3, y - 3, w + 6, h + 6, tmp);
            }
        }

        if (!jokerFace) {
            tmp.set(1f, 1f, 1f, alpha);
            Draws.patch(batch, patch, x, y, w, h, tmp);
        }

        if (back) {
            CardVisuals.drawBack(batch, assets, x, y, w, h, alpha);
        } else if (jokerFace) {
            drawJoker(batch, x, y, w, h, alpha);
        } else {
            drawSabotage(batch, x, y, w, h, alpha);
        }

        if (disabled && !jokerFace) {
            tmp.set(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b, 0.35f * alpha);
            Draws.patch(batch, patch, x, y, w, h, tmp);
        }
        if (hovered && isHoverable() && !jokerFace) {
            tmp.set(1f, 1f, 1f, 0.05f * alpha);
            Draws.patch(batch, assets.patch("pillWhite"), x, y, w, h, tmp);
        }

        if (transformed) {
            batch.setTransformMatrix(storedTransform);
        }
    }

    private void drawJoker(Batch batch, float x, float y, float w, float h, float alpha) {
        TextureRegion image = assets.jokerImage(power.id());
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(image, x, y, w, h);
    }

    private void drawSabotage(Batch batch, float x, float y, float w, float h, float alpha) {
        // Sabotagem não tem arte: continua um cartão vermelho procedural, com o nome legível como
        // única identificação (e o tooltip como o resto da explicação).
        tmp.set(0.42f, 0.10f, 0.08f, 0.97f * alpha);
        Draws.patch(batch, assets.patch("cardRed"), x, y, w, h, tmp);
        float icon = Math.min(w * 0.40f, h * 0.22f);
        batch.setColor(1f, 0.93f, 0.84f, 0.92f * alpha);
        batch.draw(assets.region("skull"), x + (w - icon) / 2f, y + h * 0.56f, icon, icon);
        batch.setColor(Color.WHITE);
        // Faixa escura atrás do nome: contraste garantido no tamanho novo da carta.
        tmp.set(0.07f, 0.02f, 0.02f, 0.78f * alpha);
        Draws.patch(batch, assets.patch("pillWhite"), x + w * 0.06f, y + h * 0.06f,
                w * 0.88f, h * 0.24f, tmp);
        drawName(batch, Sabotages.nameOf(power.id()), x, y + h * 0.02f, w, h * 0.28f,
                alpha, Palette.WHITE);
    }

    private void drawName(Batch batch, String text, float x, float y, float w, float h,
                          float alpha, Color color) {
        if (text == null || text.isBlank() || w < 30f) {
            return;
        }
        BitmapFont font = assets.font(1);
        tmp.set(color.r, color.g, color.b, alpha);
        LAYOUT.setText(font, text, tmp, Math.max(12f, w * 0.86f), Align.center, true);
        float textX = x + (w - LAYOUT.width) / 2f;
        // O topo do texto é ancorado de modo que a última linha fique DENTRO da carta.
        float textY = y + 5f + LAYOUT.height;
        font.draw(batch, LAYOUT, textX, textY);
        batch.setColor(Color.WHITE);
    }
}
