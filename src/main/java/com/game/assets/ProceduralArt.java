package com.game.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.game.core.model.Suit;
import com.game.rendering.Palette;

import java.util.Random;

/**
 * Gera todos os assets gráficos em código: feltro, painéis, faces de carta, botões e ícones.
 * Manter a arte procedural torna o projeto autossuficiente e garante uma arte original
 * (sem sprites ou marcas copiados).
 */
public final class ProceduralArt {

    private ProceduralArt() {
    }

    public static Pixmap roundedRect(int width, int height, int radius, Color fill,
                                     Color border, int borderWidth) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0);
        pixmap.fill();
        if (borderWidth > 0 && border.a > 0) {
            fillRoundRect(pixmap, 0, 0, width, height, radius, border);
            fillRoundRect(pixmap, borderWidth, borderWidth, width - borderWidth * 2,
                    height - borderWidth * 2, Math.max(0, radius - borderWidth), fill);
        } else {
            fillRoundRect(pixmap, 0, 0, width, height, radius, fill);
        }
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        return pixmap;
    }

    public static void fillRoundRect(Pixmap pixmap, int x, int y, int width, int height,
                                     int radius, Color color) {
        pixmap.setColor(color);
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            pixmap.fillRectangle(x, y, width, height);
            return;
        }
        pixmap.fillRectangle(x + r, y, width - 2 * r, height);
        pixmap.fillRectangle(x, y + r, width, height - 2 * r);
        pixmap.fillCircle(x + r, y + r, r);
        pixmap.fillCircle(x + width - r - 1, y + r, r);
        pixmap.fillCircle(x + r, y + height - r - 1, r);
        pixmap.fillCircle(x + width - r - 1, y + height - r - 1, r);
    }

    /** Carta de baralho em tom creme, com borda em bisel e uma linha interna gravada sutil. */
    public static Pixmap cardFace(int width, int height) {
        Pixmap pixmap = roundedRect(width, height, 12, Palette.CARD, Palette.CARD_EDGE, 3);
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        // linha interna
        strokeRoundRect(pixmap, 7, 7, width - 14, height - 14, 8, Palette.rgba(0xc9bd9f, 0.3f));
        // brilho superior
        pixmap.setColor(Palette.rgba(0xffffff, 0.28f));
        pixmap.fillRectangle(10, 4, width - 20, 2);
        return pixmap;
    }

    /** Verso escuro da carta com padrão de losangos trançados e borda dourada. */
    public static Pixmap cardBack(int width, int height) {
        Pixmap pixmap = roundedRect(width, height, 12, Palette.rgb(0x14382a), Palette.GOLD, 2);
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        for (int y = 8; y < height - 8; y++) {
            for (int x = 8; x < width - 8; x++) {
                if (((x + y) / 4) % 2 == 0 && ((x - y + width) / 4) % 2 == 0) {
                    pixmap.setColor(Palette.rgba(0x1f5240, 0.65f));
                    pixmap.drawPixel(x, y);
                }
            }
        }
        strokeRoundRect(pixmap, 4, 4, width - 8, height - 8, 9, Palette.rgba(0xe6b64c, 0.45f));
        return pixmap;
    }

    /** Brilho radial suave, usado atrás das cartas selecionadas e nos menus. */
    public static Pixmap glow(int size) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float center = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.max(0f, 1f - distance);
                alpha = alpha * alpha * 0.9f;
                pixmap.setColor(1f, 1f, 1f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        return pixmap;
    }

    /** Vinheta de escurecimento para a mesa e os menus. */
    public static Pixmap vignette(int size) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float center = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.max(0f, Math.min(0.72f, (distance - 0.45f) * 1.3f));
                pixmap.setColor(0f, 0f, 0f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        return pixmap;
    }

    /** Feltro repetível (tileable) com ruído sutil de fibras. */
    public static Pixmap felt(int size, long seed) {
        Random random = new Random(seed);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int noise = random.nextInt(9) - 4;
                float fibre = (float) (Math.sin((x + y * 0.6) * 0.18) * 2.0);
                int r = clamp(0x12 + noise + (int) fibre);
                int g = clamp(0x3a + noise + (int) fibre);
                int b = clamp(0x2b + noise);
                pixmap.setColor(r / 255f, g / 255f, b / 255f, 1f);
                pixmap.drawPixel(x, y);
            }
        }
        return pixmap;
    }

    /** Pictogramas de naipe como máscaras brancas 9x9, tingidos no momento do desenho. */
    public static Pixmap suitIcon(Suit suit) {
        String[] mask = switch (suit) {
            case SPADES -> new String[]{
                    "....#....",
                    "...###...",
                    "..#####..",
                    ".#######.",
                    "#########",
                    "....#....",
                    "....#....",
                    "...#.#...",
                    "..##.##.."
            };
            case HEARTS -> new String[]{
                    ".##...##.",
                    "#########",
                    "#########",
                    "#########",
                    ".#######.",
                    "..#####..",
                    "...###...",
                    "....#....",
                    "........."
            };
            case DIAMONDS -> new String[]{
                    "....#....",
                    "...###...",
                    "..#####..",
                    ".#######.",
                    "#########",
                    ".#######.",
                    "..#####..",
                    "...###...",
                    "....#...."
            };
            case CLUBS -> new String[]{
                    "...###...",
                    "...###...",
                    "....#....",
                    ".#.###.#.",
                    "#########",
                    "#########",
                    "...#.#...",
                    "..##.##..",
                    "........."
            };
        };
        return mask(mask, Palette.WHITE);
    }

    /** Emblema de estrela usado para os curingas. */
    public static Pixmap starIcon() {
        return mask(new String[]{
                "....#....",
                "....#....",
                "...###...",
                "#########",
                ".#######.",
                "..#####..",
                ".##...##.",
                ".#.....#.",
                "........."
        }, Palette.WHITE);
    }

    public static Pixmap crownIcon() {
        return mask(new String[]{
                "#...#...#",
                "#...#...#",
                "#..#.#..#",
                "##.#.#.##",
                "#########",
                "#########",
                "#.......#",
                "#.......#",
                "........."
        }, Palette.WHITE);
    }

    private static Pixmap mask(String[] rows, Color color) {
        int size = rows.length;
        Pixmap pixmap = new Pixmap(9, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0);
        pixmap.fill();
        pixmap.setColor(color);
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') {
                    pixmap.drawPixel(x, y);
                }
            }
        }
        return pixmap;
    }

    private static void strokeRoundRect(Pixmap pixmap, int x, int y, int width, int height,
                                        int radius, Color color) {
        pixmap.setColor(color);
        pixmap.drawCircle(x + radius, y + radius, radius);
        pixmap.drawCircle(x + width - radius - 1, y + radius, radius);
        pixmap.drawCircle(x + radius, y + height - radius - 1, radius);
        pixmap.drawCircle(x + width - radius - 1, y + height - radius - 1, radius);
        pixmap.drawLine(x + radius, y, x + width - radius, y);
        pixmap.drawLine(x + radius, y + height - 1, x + width - radius, y + height - 1);
        pixmap.drawLine(x, y + radius, x, y + height - radius);
        pixmap.drawLine(x + width - 1, y + radius, x + width - 1, y + height - radius);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
