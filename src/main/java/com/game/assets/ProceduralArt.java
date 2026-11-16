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

    /**
     * Carta de baralho em tom creme: borda em bisel, gradiente de papel sutil, moldura interna
     * desenhada como um contorno real (sem os círculos que apareciam nos cantos) e fibra de papel.
     */
    public static Pixmap cardFace(int width, int height) {
        int radius = Math.max(6, Math.round(width * 0.15f));
        int border = Math.max(2, Math.round(width / 32f));
        Pixmap pixmap = roundedRect(width, height, radius, Palette.CARD, Palette.CARD_EDGE, border);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        // Papel: topo iluminado, base levemente amarelada.
        float invH = 1f / Math.max(1, height - 1);
        for (int y = 0; y < height; y++) {
            float t = y * invH;
            float light = (1f - t) * 0.085f;
            float shade = t * 0.05f;
            for (int x = 0; x < width; x++) {
                int rgba = pixmap.getPixel(x, y);
                if ((rgba & 0xff) == 0) {
                    continue;
                }
                pixmap.drawPixel(x, y, mixPixel(rgba, 0xffffff, light, 0xc0b598, shade));
            }
        }

        // Fibra de papel: pontos quase invisíveis que tiram o aspecto chapado.
        Random random = new Random(0x5EEDF1B ^ (width * 31L + height));
        pixmap.setColor(Palette.rgba(0x8d8163, 0.10f));
        int grain = Math.max(12, width * height / 24);
        for (int i = 0; i < grain; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            if ((pixmap.getPixel(x, y) & 0xff) != 0) {
                pixmap.drawPixel(x, y);
            }
        }

        // Moldura interna gravada, com espessura constante e cantos limpos.
        strokeRoundRect(pixmap, border + 2, border + 2, width - (border + 2) * 2,
                height - (border + 2) * 2, Math.max(3, radius - border - 1), 1,
                Palette.rgba(0xc9bd9f, 0.55f));
        // Realce de bisel: linha clara no topo e sombra suave na base.
        pixmap.setColor(Palette.rgba(0xffffff, 0.35f));
        pixmap.fillRectangle(radius + 2, border + 1, width - (radius + 2) * 2, 1);
        pixmap.setColor(Palette.rgba(0x8d8163, 0.16f));
        pixmap.fillRectangle(radius + 2, height - border - 2, width - (radius + 2) * 2, 1);
        return pixmap;
    }

    /**
     * Verso da carta: fundo esmeralda profundo, moldura e treliça dourada e um losango central
     * vazado para receber o emblema. Todo o contorno é traçado sem "bolinhas" nos cantos.
     */
    public static Pixmap cardBack(int width, int height) {
        int radius = Math.max(6, Math.round(width * 0.15f));
        int border = Math.max(2, Math.round(width / 36f));
        Pixmap pixmap = roundedRect(width, height, radius, Palette.rgb(0x0e2f24), Palette.GOLD, border);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        int inset = Math.max(4, Math.round(width * 0.07f));
        int innerW = width - inset * 2;
        int innerH = height - inset * 2;
        strokeRoundRect(pixmap, inset, inset, innerW, innerH, Math.max(2, radius - inset), 1,
                Palette.rgba(0xe6b64c, 0.7f));

        // Treliça diagonal de fios dourados.
        int stride = Math.max(5, Math.round(width * 0.09f));
        pixmap.setColor(Palette.rgba(0xe6b64c, 0.14f));
        for (int y = inset + 3; y < height - inset - 3; y++) {
            for (int x = inset + 3; x < width - inset - 3; x++) {
                if ((x + y) % stride == 0 || (x - y + width * 8) % stride == 0) {
                    pixmap.drawPixel(x, y);
                }
            }
        }

        // Losango central vazado para o emblema.
        int cx = width / 2;
        int cy = height / 2;
        int diamond = Math.max(8, Math.round(Math.min(width, height) * 0.30f));
        for (int y = cy - diamond; y <= cy + diamond; y++) {
            for (int x = cx - diamond; x <= cx + diamond; x++) {
                int distance = Math.abs(x - cx) + Math.abs(y - cy);
                if (distance <= diamond - 2) {
                    pixmap.setColor(Palette.rgba(0x0a2419, 0.85f));
                    pixmap.drawPixel(x, y);
                } else if (distance <= diamond) {
                    pixmap.setColor(Palette.rgba(0xe6b64c, 0.55f));
                    pixmap.drawPixel(x, y);
                }
            }
        }

        // Realce de bisel e vinheta interna.
        pixmap.setColor(Palette.rgba(0xffffff, 0.14f));
        pixmap.fillRectangle(radius + 2, border, width - (radius + 2) * 2, 1);
        strokeRoundRect(pixmap, 2, 2, width - 4, height - 4, Math.max(2, radius - 1), 1,
                Palette.rgba(0x000000, 0.25f));
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

    /** Caveira pixelada das cartas de sabotagem. */
    public static Pixmap skullIcon() {
        return mask(new String[]{
                "..#####..",
                ".#######.",
                "##.#.#.##",
                "###.#.###",
                "#########",
                ".#######.",
                ".##.#.##.",
                ".#..#..#.",
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

    /**
     * Traça o contorno de um retângulo arredondado com espessura constante usando a distância
     * assinada até a borda. Evita os arcos desenhados com {@code drawCircle}, que deixavam
     * círculos soltos nos cantos do cartão.
     */
    private static void strokeRoundRect(Pixmap pixmap, int x, int y, int width, int height,
                                        int radius, int thickness, Color color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        pixmap.setColor(color);
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        float halfW = width / 2f;
        float halfH = height / 2f;
        float centerX = x + halfW - 0.5f;
        float centerY = y + halfH - 0.5f;
        float straightW = Math.max(0f, halfW - r);
        float straightH = Math.max(0f, halfH - r);
        float limit = thickness + 0.5f;
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                float qx = Math.abs(px - centerX) - straightW;
                float qy = Math.abs(py - centerY) - straightH;
                float outside = (float) Math.sqrt(Math.max(qx, 0f) * Math.max(qx, 0f)
                        + Math.max(qy, 0f) * Math.max(qy, 0f));
                float signed = outside + Math.min(Math.max(qx, qy), 0f) - r;
                if (Math.abs(signed) <= limit) {
                    pixmap.drawPixel(px, py);
                }
            }
        }
    }

    /** Mistura um pixel RGBA opaco com branco (clarear) e com um tom de papel (sombrear). */
    private static int mixPixel(int rgba, int lightHex, float lightAmount,
                                int shadeHex, float shadeAmount) {
        int r = (rgba >>> 24) & 0xff;
        int g = (rgba >>> 16) & 0xff;
        int b = (rgba >>> 8) & 0xff;
        int a = rgba & 0xff;
        int lr = (lightHex >>> 16) & 0xff;
        int lg = (lightHex >>> 8) & 0xff;
        int lb = lightHex & 0xff;
        int sr = (shadeHex >>> 16) & 0xff;
        int sg = (shadeHex >>> 8) & 0xff;
        int sb = shadeHex & 0xff;
        r = clamp(Math.round(r + (lr - r) * lightAmount + (sr - r) * shadeAmount));
        g = clamp(Math.round(g + (lg - g) * lightAmount + (sg - g) * shadeAmount));
        b = clamp(Math.round(b + (lb - b) * lightAmount + (sb - b) * shadeAmount));
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
