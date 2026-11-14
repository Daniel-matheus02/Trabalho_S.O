package com.game.rendering;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Valida a métrica da fonte pixel: cobertura, linha de base e avanço dos acentos. */
class FontAtlasTest {

    private static final Path FONT_DIR = Path.of("src/main/resources/assets/fonts");
    private static final String REQUIRED =
            "ÁÀÂÃÄ ÉÈÊË ÍÌÎÏ ÓÒÔÕÖ ÚÙÛÜ Ç Ñ áàâãä éèêë íìîï óòôõö úùûü ç ñ ª º ° … “ ” ‘ ’ ×";

    private record Glyph(int x, int y, int width, int height, int xadvance) {
    }

    private static Map<Character, Glyph> glyphs() throws IOException {
        Map<Character, Glyph> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(FONT_DIR.resolve("pixel.fnt"), StandardCharsets.UTF_8)) {
            if (!line.startsWith("char id=")) {
                continue;
            }
            Map<String, Integer> values = new LinkedHashMap<>();
            for (String token : line.split(" ")) {
                int split = token.indexOf('=');
                if (split > 0) {
                    values.put(token.substring(0, split), Integer.parseInt(token.substring(split + 1)));
                }
            }
            map.put((char) (int) values.get("id"), new Glyph(values.get("x"), values.get("y"),
                    values.get("width"), values.get("height"), values.get("xadvance")));
        }
        return map;
    }

    private static BufferedImage image() throws IOException {
        return ImageIO.read(FONT_DIR.resolve("pixel.png").toFile());
    }

    private static int bottom(BufferedImage img, Glyph glyph) {
        for (int row = glyph.height() - 1; row >= 0; row--) {
            if (opaqueRow(img, glyph, row)) {
                return row;
            }
        }
        return -1;
    }

    private static int top(BufferedImage img, Glyph glyph) {
        for (int row = 0; row < glyph.height(); row++) {
            if (opaqueRow(img, glyph, row)) {
                return row;
            }
        }
        return -1;
    }

    private static boolean opaqueRow(BufferedImage img, Glyph glyph, int row) {
        int y = glyph.y() + row;
        for (int x = glyph.x(); x < glyph.x() + glyph.width(); x++) {
            if (((img.getRGB(x, y) >>> 24) & 0xff) != 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void requiredCharsetHasNoMissingGlyph() throws IOException {
        Map<Character, Glyph> glyphs = glyphs();
        StringBuilder missing = new StringBuilder();
        for (char c : REQUIRED.toCharArray()) {
            if (c != ' ' && !glyphs.containsKey(c)) {
                missing.append(c);
            }
        }
        System.out.println("[FontTest] charset=" + REQUIRED.replace(" ", "").length()
                + " glyphs=" + glyphs.size() + " missing=[" + missing + "]");
        assertTrue(missing.isEmpty(), "Glifos ausentes: " + missing);
    }

    @Test
    void accentedGlyphsUseTheSameBaselineAndAdvance() throws IOException {
        Map<Character, Glyph> glyphs = glyphs();
        BufferedImage img = image();
        String pairs = "AÁAÂAÃEÉEÊIÍOÓOÔOÕUÚaáaâaãeéeêiíoóoôoõuú";
        int mismatches = 0;
        for (int i = 0; i < pairs.length(); i += 2) {
            char plain = pairs.charAt(i);
            char accented = pairs.charAt(i + 1);
            Glyph p = glyphs.get(plain);
            Glyph a = glyphs.get(accented);
            int pb = bottom(img, p);
            int ab = bottom(img, a);
            boolean ok = pb == ab && p.xadvance() == a.xadvance();
            if (!ok) {
                mismatches++;
            }
            System.out.printf("[FontTest] (%c,%c) bottom=%d/%d advance=%d/%d %s%n",
                    plain, accented, pb, ab, p.xadvance(), a.xadvance(), ok ? "OK" : "FAIL");
            assertEquals(pb, ab, "Linha de base de " + accented + " difere de " + plain);
            assertEquals(p.xadvance(), a.xadvance(), "Avanço de " + accented + " difere de " + plain);
        }
        System.out.println("[FontTest] accent mismatches=" + mismatches);
        assertEquals(0, mismatches);
    }

    @Test
    void cedillaKeepsBodyTopAndOnlyDescendsLower() throws IOException {
        Map<Character, Glyph> glyphs = glyphs();
        BufferedImage img = image();
        assertEquals(top(img, glyphs.get('C')), top(img, glyphs.get('Ç')));
        assertEquals(top(img, glyphs.get('c')), top(img, glyphs.get('ç')));
        assertTrue(bottom(img, glyphs.get('Ç')) > bottom(img, glyphs.get('C')));
        System.out.println("[FontTest] C/Ç body top equal, cedilla descends below baseline");
    }

    @Test
    void lineHeightMatchesTheTypographyGrid() throws IOException {
        String common = Files.readAllLines(FONT_DIR.resolve("pixel.fnt"), StandardCharsets.UTF_8)
                .stream().filter(l -> l.startsWith("common ")).findFirst().orElseThrow();
        assertTrue(common.contains("lineHeight=12"), common);
        System.out.println("[FontTest] " + common);
    }
}
