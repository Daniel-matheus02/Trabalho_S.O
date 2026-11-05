package com.game.rendering;

import com.badlogic.gdx.graphics.Color;

/** Paleta de cores central: inspirada em Balatro, mas com identidade própria de feltro/ouro/tinta. */
public final class Palette {

    public static final Color FELT_DARK = rgb(0x0d2d21);
    public static final Color FELT = rgb(0x123a2b);
    public static final Color FELT_LIGHT = rgb(0x1a4d38);
    public static final Color LINE = rgb(0x1f5240);
    public static final Color PANEL = rgba(0x0b241b, 0.92f);
    public static final Color PANEL_LIGHT = rgba(0x0e2a20, 0.95f);
    public static final Color CARD = rgb(0xf3ecdb);
    public static final Color CARD_EDGE = rgb(0xd8cdb2);
    public static final Color INK = rgb(0xeae6d8);
    public static final Color INK_DIM = rgb(0x9cb3a7);
    public static final Color CHIPS = rgb(0x4a9bd6);
    public static final Color MULT = rgb(0xe0573f);
    public static final Color GOLD = rgb(0xe6b64c);
    public static final Color GOLD_DARK = rgb(0xa87f1f);
    public static final Color P1 = rgb(0x56a7e0);
    public static final Color P2 = rgb(0xe68a4e);
    public static final Color GOOD = rgb(0x64c184);
    public static final Color BAD = rgb(0xe0573f);
    public static final Color SPADE = rgb(0x20201e);
    public static final Color HEART = rgb(0xc8402f);
    public static final Color DIAMOND = rgb(0x2f6fc8);
    public static final Color CLUB = rgb(0x2f9152);
    public static final Color WHITE = rgb(0xffffff);
    public static final Color BLACK = rgb(0x000000);
    public static final Color SHADOW = rgba(0x000000, 0.45f);

    private Palette() {
    }

    public static Color rgb(int hex) {
        return new Color((hex << 8) | 0xff);
    }

    public static Color rgba(int hex, float alpha) {
        Color color = new Color((hex << 8) | 0xff);
        color.a = alpha;
        return color;
    }

    public static Color tint(Color color, float amount) {
        return new Color(
                Math.min(1f, color.r + amount),
                Math.min(1f, color.g + amount),
                Math.min(1f, color.b + amount),
                color.a);
    }
}
