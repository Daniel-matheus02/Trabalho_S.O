package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

/**
 * Renderizador único da face de carta usado por mão, mesa, revelação, referência de combinações
 * e prévias.
 *
 * <p>Uma carta tem <b>uma</b> cor, derivada exclusivamente do próprio naipe. Essa cor é calculada
 * uma vez no topo e aplicada explicitamente antes de <b>cada</b> desenho (índice superior,
 * índice inferior, naipe do canto e naipe central).</p>
 *
 * <p>Importante: no libGDX, {@link GlyphLayout#setText(BitmapFont, CharSequence)} captura
 * {@code font.getColor()} no momento da chamada. Por isso {@code font.setColor(...)} precisa vir
 * ANTES de {@code setText}; definir a cor depois não tem efeito e deixava o índice superior com a
 * cor da carta anterior.</p>
 */
public final class CardRenderer {

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private CardRenderer() {
    }

    /** A única fonte de cor de uma carta: decide-se apenas pelo naipe. */
    public static Color suitColor(Card card) {
        return switch (card.suit()) {
            case SPADES -> Palette.SPADE;
            case HEARTS -> Palette.HEART;
            case DIAMONDS -> Palette.DIAMOND;
            case CLUBS -> Palette.CLUB;
        };
    }

    /** Escala inteira da fonte de índice: 2 (20 px) sempre que a carta comporta. */
    public static int rankScale(float cardWidth) {
        return cardWidth >= 52f ? 2 : 1;
    }

    public static void drawFace(Batch batch, GameAssets assets, Card card,
                                float x, float y, float w, float h, float alpha) {
        Color color = suitColor(card);
        String suitRegion = "suit-" + card.suit().code();
        BitmapFont font = assets.font(rankScale(w));
        float pad = Math.max(5f, w * 0.085f);
        float icon = Math.max(9f, w * 0.18f);
        String rank = card.rank().display();

        LAYOUT.setText(font, rank);
        float rankWidth = LAYOUT.width;
        float rankHeight = LAYOUT.height;

        // Índice superior esquerdo: sombra dura de 1 px + cor do naipe da PRÓPRIA carta.
        float topBaseline = y + h - pad;
        drawText(batch, font, rank, 0f, 0f, 0f, 0.5f * alpha,
                x + pad + 1f, topBaseline - 1f);
        drawText(batch, font, rank, color.r, color.g, color.b, alpha,
                x + pad, topBaseline);

        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(assets.region(suitRegion), x + pad,
                y + h - pad - rankHeight - icon - 2f, icon, icon);

        // Naipe central com halo e sombra.
        float big = Math.min(w * 0.42f, h * 0.30f);
        float bx = x + (w - big) / 2f;
        float by = y + (h - big) / 2f;
        float halo = big * 2.1f;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(color.r, color.g, color.b, 0.13f * alpha);
        batch.draw(assets.glow(), bx + big / 2f - halo / 2f, by + big / 2f - halo / 2f,
                halo, halo);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(0f, 0f, 0f, 0.10f * alpha);
        batch.draw(assets.region(suitRegion), bx + 1.5f, by - 1.5f, big, big);
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(assets.region(suitRegion), bx, by, big, big);

        // Índice espelhado no canto inferior direito.
        if (w >= 58f) {
            float bottomBaseline = y + pad + rankHeight;
            drawText(batch, font, rank, 0f, 0f, 0f, 0.5f * alpha,
                    x + w - pad - rankWidth + 1f, bottomBaseline - 1f);
            drawText(batch, font, rank, color.r, color.g, color.b, alpha,
                    x + w - pad - rankWidth, bottomBaseline);
            batch.setColor(color.r, color.g, color.b, alpha);
            batch.draw(assets.region(suitRegion), x + w - pad - icon,
                    y + pad + rankHeight + 3f, icon, icon);
        }

        // Nunca deixa cor/fonte vazarem para o próximo ator.
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        font.setColor(Color.WHITE);
    }

    private static void drawText(Batch batch, BitmapFont font, String text,
                                 float r, float g, float b, float a, float x, float y) {
        // setColor ANTES de setText: GlyphLayout copia a cor da fonte neste instante.
        font.setColor(r, g, b, a);
        LAYOUT.setText(font, text);
        font.draw(batch, LAYOUT, x, y);
    }
}
