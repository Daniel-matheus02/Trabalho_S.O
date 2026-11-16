package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.game.assets.GameAssets;

import java.text.Normalizer;

/**
 * Escala tipográfica central do aplicativo. Todos os tamanhos de texto do jogo vêm daqui; não há
 * {@code deriveFont}, {@code setFontScale} nem tamanho ad hoc nas telas.
 *
 * <p>A fonte pixel é desenhada em uma grade de 5×7 px com células de 10 px e altura de linha de
 * 12 px. Por isso cada nível é um múltiplo inteiro da célula: nenhum texto usa tamanho fracionário
 * e todos os monitores/zoom recebem as mesmas proporções.</p>
 */
public final class Typography {

    /** Altura de linha da fonte base (pixel grid), em px lógicos. */
    public static final int BASE_LINE_HEIGHT = 12;

    /** Níveis permitidos em toda a aplicação. */
    public enum Level {
        TITLE(4),
        HEADER(2),
        HEADING(2),
        BODY(2),
        CAPTION(1);

        private final int scale;

        Level(int scale) {
            this.scale = scale;
        }

        /** Múltiplo inteiro da grade da fonte (1 = 12 px de linha). */
        public int scale() {
            return scale;
        }

        /** Tamanho lógico em px (múltiplo inteiro da grade). */
        public int px() {
            return scale * BASE_LINE_HEIGHT;
        }
    }

    /** Nomes de estilo centralizados (usados pelo skin e pelas telas). */
    public static final String TITLE = "title";
    public static final String HEADER = "header";
    public static final String CAPTION = "caption";

    /** Conjunto que o app exibe e precisa estar presente na fonte. */
    private static final String REQUIRED_CHARSET =
            "ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑáàâãäéèêëíìîïóòôõöúùûüçñªº°…“”‘’×";

    private Typography() {
    }

    /** Diagnóstico de cobertura da fonte, registrado uma vez na inicialização. */
    public static void audit(GameAssets assets) {
        com.badlogic.gdx.graphics.g2d.BitmapFont font = assets.font(1);
        StringBuilder missing = new StringBuilder();
        for (char c : REQUIRED_CHARSET.toCharArray()) {
            if (!font.getData().hasGlyph(c)) {
                missing.append(c);
            }
        }
        com.badlogic.gdx.Gdx.app.log("Font", "face=" + font.getData().name
                + " file=assets/fonts/pixel.fnt file=assets/fonts/pixel.png"
                + " required=" + REQUIRED_CHARSET.length()
                + " missing=[" + missing + "]"
                + " lineHeight=" + font.getData().lineHeight + "px");
        System.out.println("[Font] face=" + font.getData().name
                + " required=" + REQUIRED_CHARSET.length()
                + " missing=[" + missing + "]");
    }

    public static BitmapFont font(GameAssets assets, Level level) {
        return assets.font(level.scale());
    }

    public static int px(Level level) {
        return level.px();
    }

    /**
     * Normaliza qualquer texto exibido para NFC, inclusive nomes digitados e mensagens recebidas.
     * Assim “A” + combining acute nunca chega separado à fonte e não mistura métricas.
     */
    public static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /** Registra todos os estilos de Label a partir da escala central. */
    public static void registerStyles(Skin skin, GameAssets assets) {
        // TITLE: somente o título do jogo e destaques numéricos de fim de partida.
        add(skin, assets, "title", Level.TITLE, Palette.GOLD);
        add(skin, assets, "h1", Level.TITLE, Palette.GOLD);
        add(skin, assets, "winner", Level.TITLE, Palette.GOLD);
        add(skin, assets, "damage", Level.TITLE, Palette.BAD);

        // HEADER: tudo na barra superior (pílulas de rodada/turno e status).
        add(skin, assets, HEADER, Level.HEADER, Palette.INK);

        // HEADING: títulos de painel, botões e números de destaque.
        add(skin, assets, "h2", Level.HEADING, Palette.INK);
        add(skin, assets, "h2-p1", Level.HEADING, Palette.P1);
        add(skin, assets, "h2-p2", Level.HEADING, Palette.P2);
        add(skin, assets, "h3", Level.HEADING, Palette.GOLD);
        add(skin, assets, "gold", Level.HEADING, Palette.GOLD);
        add(skin, assets, "chips", Level.HEADING, Palette.CHIPS);
        add(skin, assets, "mult", Level.HEADING, Palette.MULT);
        add(skin, assets, "good", Level.HEADING, Palette.GOOD);
        add(skin, assets, "bad", Level.HEADING, Palette.BAD);
        add(skin, assets, "p1", Level.HEADING, Palette.P1);
        add(skin, assets, "p2", Level.HEADING, Palette.P2);
        add(skin, assets, "final", Level.HEADING, Palette.GOLD);
        add(skin, assets, "hp-number", Level.HEADING, Palette.INK);

        // BODY: parágrafos, dicas, descrições e linhas de log.
        add(skin, assets, "body", Level.BODY, Palette.INK);
        add(skin, assets, "body-dim", Level.BODY, Palette.INK_DIM);
        add(skin, assets, "subtitle", Level.BODY, Palette.INK_DIM);
        add(skin, assets, "line-plus-body", Level.BODY, Palette.GOOD);
        add(skin, assets, "line-mult-body", Level.BODY, Palette.MULT);
        add(skin, assets, "line-normal-body", Level.BODY, Palette.INK_DIM);

        // CAPTION: menor texto permitido (12 px lógicos).
        add(skin, assets, CAPTION, Level.CAPTION, Palette.INK_DIM);
        add(skin, assets, "small", Level.CAPTION, Palette.INK_DIM);
        add(skin, assets, "gold-small", Level.CAPTION, Palette.GOLD);
        add(skin, assets, "white-small", Level.CAPTION, Palette.WHITE);
        add(skin, assets, "log", Level.CAPTION, Palette.rgb(0xbfe0cf));
        add(skin, assets, "log-gold", Level.CAPTION, Palette.GOLD);
        add(skin, assets, "log-bad", Level.CAPTION, Palette.BAD);
        add(skin, assets, "log-system", Level.CAPTION, Palette.rgb(0xc7a7e0));
        add(skin, assets, "line-normal", Level.CAPTION, Palette.INK_DIM);

        // Texto que recebe cor por naipe: fonte branca para a cor do ator ser exata.
        add(skin, assets, "suit", Level.BODY, Palette.WHITE);
    }

    private static void add(Skin skin, GameAssets assets, String name, Level level,
                            com.badlogic.gdx.graphics.Color color) {
        skin.add(name, new Label.LabelStyle(assets.font(level.scale()), color),
                Label.LabelStyle.class);
    }
}
