import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera a fonte bitmap de pixel original usada pelo cliente desktop.
 *
 * <p>Saída: {@code pixel.fnt} (formato texto AngelCode) e {@code pixel.png}.
 * Execute a partir da raiz do projeto com: {@code java tools/FontGen.java}</p>
 */
public final class FontGen {

    /** Glifos de pixel 5x7. Minúsculas mapeiam para o glifo maiúsculo (visual retrô em caixa alta). */
    private static final Map<Character, String[]> GLYPHS = new LinkedHashMap<>();

    static {
        add('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        add('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        add('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        add('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        add('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        add('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        add('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        add('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        add('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        add('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        add('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        add('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        add('M', "#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#");
        add('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        add('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        add('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        add('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        add('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        add('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        add('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        add('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        add('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        add('W', "#...#", "#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#");
        add('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        add('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        add('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");

        add('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        add('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        add('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        add('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
        add('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        add('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        add('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        add('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        add('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        add('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");

        add('.', ".....", ".....", ".....", ".....", ".....", "..#..", "..#..");
        add(',', ".....", ".....", ".....", ".....", ".....", "..#..", ".#...");
        add(':', ".....", "..#..", "..#..", ".....", "..#..", "..#..", ".....");
        add(';', ".....", "..#..", "..#..", ".....", "..#..", "..#..", ".#...");
        add('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        add('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        add('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        add('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        add('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....");
        add('\u00d7', ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "....."); // × (multiplicação)
        add('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        add('\\', "#....", "#....", ".#...", "..#..", "...#.", "....#", "....#");
        add('(', "...#.", "..#..", "..#..", "..#..", "..#..", "..#..", "...#.");
        add(')', ".#...", "..#..", "..#..", "..#..", "..#..", "..#..", ".#...");
        add('[', "..###", "..#..", "..#..", "..#..", "..#..", "..#..", "..###");
        add(']', "###..", "..#..", "..#..", "..#..", "..#..", "..#..", "###..");
        add('{', "...##", "..#..", "..#..", ".#...", "..#..", "..#..", "...##");
        add('}', "##...", "..#..", "..#..", "...#.", "..#..", "..#..", "##...");
        add('\'', "..#..", "..#..", ".....", ".....", ".....", ".....", ".....");
        add('"', ".#.#.", ".#.#.", ".....", ".....", ".....", ".....", ".....");
        add('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##");
        add('&', ".##..", "#..#.", ".##..", ".##.#", "#..#.", "#..#.", ".##.#");
        add('#', ".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#.");
        add('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
        add('|', "..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        add('*', ".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", ".....");
        add('$', "..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#..");
        add('<', "...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#.");
        add('>', ".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#...");
        add('\u2192', ".....", "..#..", "...#.", "#####", "...#.", "..#..", "....."); // → (seta direita)
        add('\u2190', ".....", "..#..", ".#...", "#####", ".#...", "..#..", "....."); // ← (seta esquerda)
        add('\u00b7', ".....", ".....", ".....", "..#..", ".....", ".....", "....."); // ponto mediano
        add('\u2014', ".....", ".....", ".....", "#####", ".....", ".....", "....."); // travessão (em dash)
        add('\u2013', ".....", ".....", ".....", ".###.", ".....", ".....", "....."); // meia-risca (en dash)
        add('\u2212', ".....", ".....", ".....", "#####", ".....", ".....", "....."); // sinal de menos
        add('\u2022', ".....", ".....", ".....", ".##..", ".##..", ".....", "....."); // marcador (bullet)
        add('\u2026', ".....", ".....", ".....", ".....", ".....", ".....", "#.#.#"); // reticências
        add('\u2605', ".....", "..#..", ".###.", "#####", ".###.", "#...#", "....."); // estrela
        add('\u25cf', "..###..", ".#####.", "#######", "#######", "#######", ".#####.", "..###.."); // círculo preenchido
        add('\u25cb', "..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.."); // círculo vazado
    }

    /** Máscaras de acento desenhadas nas duas linhas acima do corpo da letra. */
    private static final Map<Character, String[]> ACCENTS = Map.of(
            '\u0301', new String[]{"00100", "01000"}, // agudo
            '\u0300', new String[]{"01000", "00100"}, // grave
            '\u0302', new String[]{"00100", "01010"}, // circunflexo
            '\u0303', new String[]{"01001", "10110"}, // til
            '\u0308', new String[]{"01010", "00000"}  // trema
    );

    private static final String ACCENTED_UPPER = "\u00c1\u00c0\u00c2\u00c3\u00c4\u00c9\u00c8\u00ca\u00cb\u00cd\u00cc\u00ce\u00cf"
            + "\u00d3\u00d2\u00d4\u00d5\u00d6\u00da\u00d9\u00db\u00dc\u00c7\u00d1";

    private static final int CELL_W = 8;
    private static final int CELL_H = 12;
    private static final int COLS = 16;

    private FontGen() {
    }

    private static void add(char c, String... rows) {
        if (rows.length != 7) {
            throw new IllegalArgumentException("o glifo " + c + " deve ter 7 linhas");
        }
        GLYPHS.put(c, rows);
    }

    public static void main(String[] args) throws IOException {
        // Monta a tabela completa de glifos: ASCII + maiúsculas acentuadas (minúsculas reutilizam as maiúsculas).
        Map<Character, String[]> glyphs = new LinkedHashMap<>();
        for (char c = 32; c <= 126; c++) {
            char key = Character.toUpperCase(c);
            String[] rows = GLYPHS.get(c);
            if (rows == null) {
                rows = GLYPHS.get(key);
            }
            if (rows == null) {
                continue;
            }
            if (c == ' ') {
                rows = new String[]{".....", ".....", ".....", ".....", ".....", ".....", "....."};
            }
            glyphs.put(c, rows);
        }

        // Símbolos extras que não são ASCII (setas, travessões, círculos, estrela, ...).
        for (Map.Entry<Character, String[]> entry : GLYPHS.entrySet()) {
            glyphs.putIfAbsent(entry.getKey(), entry.getValue());
        }

        for (int i = 0; i < ACCENTED_UPPER.length(); i++) {
            char accented = ACCENTED_UPPER.charAt(i);
            String decomposed = java.text.Normalizer.normalize(String.valueOf(accented), java.text.Normalizer.Form.NFD);
            char base = decomposed.charAt(0);
            char mark = decomposed.length() > 1 ? decomposed.charAt(1) : 0;
            String[] rows = GLYPHS.get(base);
            String[] accent = ACCENTS.get(mark);
            if (rows == null) {
                continue;
            }
            glyphs.put(accented, withAccent(rows, accent, false));
            glyphs.put(Character.toLowerCase(accented), withAccent(rows, accent, false));
        }
        glyphs.put('\u00e7', withAccent(GLYPHS.get('C'), null, true));
        glyphs.put('\u00c7', withAccent(GLYPHS.get('C'), null, true));

        // Normaliza cada glifo para uma célula 5x10: duas linhas de acento, sete linhas da letra e uma
        // linha de descendente. A linha de base fica na linha 9, então todos os glifos se alinham.
        Map<Character, String[]> cells = new LinkedHashMap<>();
        for (Map.Entry<Character, String[]> e : glyphs.entrySet()) {
            String[] mask = e.getValue();
            String[] cell = new String[10];
            java.util.Arrays.fill(cell, ".....");
            for (int i = 0; i < mask.length; i++) {
                cell[i] = mask[i];
            }
            cells.put(e.getKey(), cell);
        }

        List<Character> ordered = new ArrayList<>(cells.keySet());
        int rowsCount = (ordered.size() + COLS - 1) / COLS;
        int width = COLS * CELL_W;
        int height = rowsCount * CELL_H;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        StringBuilder chars = new StringBuilder();
        int count = 0;
        for (int i = 0; i < ordered.size(); i++) {
            char c = ordered.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cellX = col * CELL_W;
            int cellY = row * CELL_H;
            String[] body = cells.get(c);
            for (int gy = 0; gy < body.length; gy++) {
                for (int gx = 0; gx < 5; gx++) {
                    if (body[gy].charAt(gx) == '#') {
                        image.setRGB(cellX + 1 + gx, cellY + 1 + gy, 0xFFFFFFFF);
                    }
                }
            }
            int xadvance = c == ' ' ? 4 : 6;
            chars.append("char id=").append((int) c)
                    .append(" x=").append(cellX + 1)
                    .append(" y=").append(cellY + 1)
                    .append(" width=5 height=10 xoffset=0 yoffset=0 xadvance=").append(xadvance)
                    .append(" page=0 chnl=15\n");
            count++;
        }

        Path outDir = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/resources/assets/fonts");
        Files.createDirectories(outDir);
        ImageIO.write(image, "png", outDir.resolve("pixel.png").toFile());

        String fnt = "info face=\"DueloPixel\" size=8 bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n"
                + "common lineHeight=11 base=9 scaleW=" + width + " scaleH=" + height + " pages=1 packed=0\n"
                + "page id=0 file=\"pixel.png\"\n"
                + "chars count=" + count + "\n"
                + chars;
        try (Writer w = Files.newBufferedWriter(outDir.resolve("pixel.fnt"), StandardCharsets.UTF_8)) {
            w.write(fnt);
        }

        System.out.println("Gravados " + count + " glifos em " + outDir.toAbsolutePath() + " (" + width + "x" + height + ")");
    }

    /**
     * Monta uma célula 5x10: linhas de acento (0-1), o corpo da letra com 7 linhas (2-8) e, para
     * letras com cedilha, a linha do descendente (9).
     */
    private static String[] withAccent(String[] base, String[] accent, boolean cedilla) {
        String[] out = new String[10];
        java.util.Arrays.fill(out, ".....");
        for (int i = 0; i < 7; i++) {
            out[i + 2] = base[i];
        }
        if (accent != null) {
            out[0] = accent[0].replace('1', '#').replace('0', '.');
            out[1] = accent[1].replace('1', '#').replace('0', '.');
        }
        if (cedilla) {
            out[9] = "..#..";
        }
        return out;
    }
}
