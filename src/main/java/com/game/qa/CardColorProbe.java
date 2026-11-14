package com.game.qa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.game.assets.GameAssets;
import com.game.core.model.Card;
import com.game.rendering.CardActor;
import com.game.rendering.CardRenderer;
import com.game.rendering.Palette;

import java.util.ArrayList;
import java.util.List;

/**
 * Prova em pixels (fora da tela) que TODAS as partes de cada carta usam a cor do próprio naipe.
 * Desenha a sequência indicada no laudo e a sequência inversa no mesmo batch/fonte, para expor
 * qualquer vazamento de estado entre cartas.
 */
public final class CardColorProbe {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 220;
    private static final float CARD_X = 40f;
    private static final float CARD_Y = 55f;
    private static final float CARD_W = 80f;
    private static final float CARD_H = 110f;

    private CardColorProbe() {
    }

    public static void run(GameAssets assets) {
        List<String> sequence = new ArrayList<>(List.of("AP", "JO", "JE", "6E", "4P", "QE", "8P", "6O"));
        List<String> order = new ArrayList<>(sequence);
        for (int i = sequence.size() - 1; i >= 0; i--) {
            order.add(sequence.get(i));
        }

        FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        SpriteBatch batch = new SpriteBatch();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, WIDTH, HEIGHT);

        float pad = Math.max(5f, CARD_W * 0.085f);
        int mismatches = 0;
        System.out.println("[CardColorProbe] offscreen " + WIDTH + "x" + HEIGHT
                + " order=" + order);
        for (String code : order) {
            Card card = Card.fromCode(code);
            Color expected = CardRenderer.suitColor(card);

            framebuffer.begin();
            Gdx.gl.glClearColor(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            CardActor actor = new CardActor(assets, card, false, null);
            actor.showInstantly();
            actor.setBounds(CARD_X, CARD_Y, CARD_W, CARD_H);
            actor.act(1f); // conclui o fade de entrada
            actor.draw(batch, 1f);
            batch.end();
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, WIDTH, HEIGHT);
            framebuffer.end();

            // Retângulos dos índices (mesma geometria do CardRenderer).
            int tlX = Math.round(CARD_X + pad - 3f);
            int tlY = Math.round(CARD_Y + CARD_H - pad - 26f);
            int brX = Math.round(CARD_X + CARD_W - pad - 40f);
            int brY = Math.round(CARD_Y + pad - 4f);

            int[] tl = counts(pixels, tlX, tlY, 42, 28);
            int[] br = counts(pixels, brX, brY, 44, 28);
            int expectedIndex = index(card);
            int tlDominant = dominant(tl);
            int brDominant = dominant(br);
            boolean ok = tlDominant == expectedIndex && brDominant == expectedIndex
                    && tl[expectedIndex] > 0 && br[expectedIndex] > 0;
            if (!ok) {
                mismatches++;
            }
            System.out.printf("[CardColorProbe] %-2s expected=%s TL=%s(%d) BR=%s(%d) %s%n",
                    code, hex(expected), hex(colorAt(tlDominant)), tl[tlDominant],
                    hex(colorAt(brDominant)), br[brDominant], ok ? "OK" : "FAIL");
            pixels.dispose();
        }
        batch.dispose();
        framebuffer.dispose();
        System.out.println("[CardColorProbe] card color mismatches=" + mismatches);
    }

    /** Conta pixels próximos de cada uma das quatro cores de naipe. */
    private static int[] counts(Pixmap pixels, int x, int y, int width, int height) {
        int[] result = new int[4];
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                if (px < 0 || py < 0 || px >= pixels.getWidth() || py >= pixels.getHeight()) {
                    continue;
                }
                int rgba = pixels.getPixel(px, py);
                int r = (rgba >>> 24) & 0xff;
                int g = (rgba >>> 16) & 0xff;
                int b = (rgba >>> 8) & 0xff;
                int a = rgba & 0xff;
                if (a < 250) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    Color color = colorAt(i);
                    if (Math.abs(r - Math.round(color.r * 255)) <= 6
                            && Math.abs(g - Math.round(color.g * 255)) <= 6
                            && Math.abs(b - Math.round(color.b * 255)) <= 6) {
                        result[i]++;
                    }
                }
            }
        }
        return result;
    }

    private static int dominant(int[] counts) {
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int index(Card card) {
        return switch (card.suit()) {
            case SPADES -> 0;
            case HEARTS -> 1;
            case DIAMONDS -> 2;
            case CLUBS -> 3;
        };
    }

    private static Color colorAt(int index) {
        return switch (index) {
            case 0 -> Palette.SPADE;
            case 1 -> Palette.HEART;
            case 2 -> Palette.DIAMOND;
            default -> Palette.CLUB;
        };
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", Math.round(color.r * 255),
                Math.round(color.g * 255), Math.round(color.b * 255));
    }
}
