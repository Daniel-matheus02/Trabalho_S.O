package com.game.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.game.rendering.Palette;

/** Constrói o skin do Scene2D inteiramente a partir da arte gerada e da fonte pixel. */
public final class SkinFactory {

    private SkinFactory() {
    }

    public static Skin build(GameAssets assets) {
        Skin skin = new Skin();
        addLabelStyles(skin, assets);
        addButtonStyles(skin, assets);
        addTextFieldStyle(skin, assets);
        addScrollPaneStyle(skin, assets);
        return skin;
    }

    private static void addLabelStyles(Skin skin, GameAssets assets) {
        add(skin, "title", assets.font(7), Palette.GOLD);
        add(skin, "title-ink", assets.font(6), Palette.INK);
        add(skin, "h1", assets.font(4), Palette.GOLD);
        add(skin, "h2", assets.font(3), Palette.INK);
        add(skin, "h3", assets.font(2), Palette.GOLD);
        add(skin, "subtitle", assets.font(2), Palette.INK_DIM);
        add(skin, "body", assets.font(2), Palette.INK);
        add(skin, "body-dim", assets.font(2), Palette.INK_DIM);
        add(skin, "small", assets.font(1), Palette.INK_DIM);
        add(skin, "tiny", assets.font(1), Palette.INK);
        add(skin, "gold", assets.font(2), Palette.GOLD);
        add(skin, "gold-small", assets.font(1), Palette.GOLD);
        add(skin, "chips", assets.font(2), Palette.CHIPS);
        add(skin, "mult", assets.font(2), Palette.MULT);
        add(skin, "good", assets.font(2), Palette.GOOD);
        add(skin, "bad", assets.font(2), Palette.BAD);
        add(skin, "p1", assets.font(2), Palette.P1);
        add(skin, "p2", assets.font(2), Palette.P2);
        add(skin, "h2-p1", assets.font(3), Palette.P1);
        add(skin, "h2-p2", assets.font(3), Palette.P2);
        add(skin, "big-chips", assets.font(3), Palette.CHIPS);
        add(skin, "big-mult", assets.font(3), Palette.MULT);
        add(skin, "final", assets.font(4), Palette.GOLD);
        add(skin, "hp-number", assets.font(3), Palette.INK);
        add(skin, "damage", assets.font(5), Palette.BAD);
        add(skin, "winner", assets.font(5), Palette.GOLD);
        add(skin, "log", assets.font(1), Color.valueOf("bfe0cf"));
        add(skin, "log-gold", assets.font(1), Palette.GOLD);
        add(skin, "log-bad", assets.font(1), Palette.BAD);
        add(skin, "log-draft", assets.font(1), Color.valueOf("c7a7e0"));
        add(skin, "line-plus", assets.font(1), Palette.GOOD);
        add(skin, "line-mult", assets.font(1), Palette.MULT);
        add(skin, "line-normal", assets.font(1), Palette.INK_DIM);
        add(skin, "unit", assets.font(1), Palette.INK);
        add(skin, "white-small", assets.font(1), Palette.WHITE);
        add(skin, "chips-small", assets.font(1), Palette.CHIPS);
        add(skin, "line-plus-body", assets.font(2), Palette.GOOD);
        add(skin, "line-mult-body", assets.font(2), Palette.MULT);
        add(skin, "line-normal-body", assets.font(2), Palette.INK_DIM);
    }

    private static void add(Skin skin, String name, BitmapFont font, Color color) {
        skin.add(name, new Label.LabelStyle(font, color), Label.LabelStyle.class);
    }

    private static void addButtonStyles(Skin skin, GameAssets assets) {
        BitmapFont body = assets.font(2);
        Color darkInk = Palette.rgb(0x06241a);

        TextButton.TextButtonStyle gold = new TextButton.TextButtonStyle();
        gold.up = drawable(assets, "buttonGold");
        gold.over = drawable(assets, "buttonGold").tint(Palette.tint(Palette.GOLD, 0.08f));
        gold.down = drawable(assets, "buttonGold").tint(Palette.GOLD_DARK);
        gold.font = body;
        gold.fontColor = darkInk;
        gold.overFontColor = darkInk;
        gold.downFontColor = Palette.rgb(0x0d2d21);
        gold.pressedOffsetY = -2;
        skin.add("gold", gold, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle green = new TextButton.TextButtonStyle();
        green.up = drawable(assets, "buttonGreen");
        green.over = drawable(assets, "buttonGreen").tint(Palette.rgb(0x2a6b53));
        green.down = drawable(assets, "buttonGreen").tint(Palette.rgb(0x0d2d21));
        green.font = body;
        green.fontColor = Palette.INK;
        green.overFontColor = Palette.WHITE;
        green.pressedOffsetY = -2;
        skin.add("green", green, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle danger = new TextButton.TextButtonStyle();
        danger.up = drawable(assets, "buttonDanger");
        danger.over = drawable(assets, "buttonDanger").tint(Palette.rgb(0x9a392e));
        danger.down = drawable(assets, "buttonDanger").tint(Palette.rgb(0x4a1812));
        danger.font = body;
        danger.fontColor = Palette.INK;
        danger.pressedOffsetY = -2;
        skin.add("danger", danger, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle ghost = new TextButton.TextButtonStyle();
        ghost.up = drawable(assets, "buttonGhost");
        ghost.over = drawable(assets, "buttonGhost").tint(Palette.rgb(0x2a6b53));
        ghost.down = drawable(assets, "buttonGhost").tint(Palette.rgb(0x0d2d21));
        ghost.font = assets.font(2);
        ghost.fontColor = Palette.INK;
        ghost.overFontColor = Palette.INK;
        skin.add("ghost", ghost, TextButton.TextButtonStyle.class);
    }

    private static void addTextFieldStyle(Skin skin, GameAssets assets) {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        NinePatchDrawable background = new NinePatchDrawable(new NinePatch(assets.patch("panelDeep")));
        background.setLeftWidth(16);
        background.setRightWidth(16);
        background.setTopHeight(8);
        background.setBottomHeight(8);
        style.background = background;
        style.font = assets.font(2);
        style.fontColor = Palette.INK;
        style.messageFont = assets.font(2);
        style.messageFontColor = Palette.INK_DIM;
        style.cursor = new TextureRegionDrawable(assets.region("white")).tint(Palette.GOLD);
        style.selection = new TextureRegionDrawable(assets.region("white")).tint(Palette.rgba(0x4a9bd6, 0.5f));
        skin.add("default", style, TextField.TextFieldStyle.class);
    }

    private static void addScrollPaneStyle(Skin skin, GameAssets assets) {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        NinePatchDrawable knob = new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")));
        knob.setMinWidth(6);
        knob.setMinHeight(24);
        knob = (NinePatchDrawable) knob.tint(Palette.rgba(0x4a9bd6, 0.75f));
        style.vScrollKnob = knob;
        style.vScroll = new TextureRegionDrawable(assets.region("white")).tint(Palette.rgba(0x000000, 0.25f));
        style.hScrollKnob = knob;
        style.hScroll = style.vScroll;
        skin.add("default", style, ScrollPane.ScrollPaneStyle.class);
    }

    private static NinePatchDrawable drawable(GameAssets assets, String name) {
        NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(assets.patch(name)));
        drawable.setLeftWidth(18);
        drawable.setRightWidth(18);
        drawable.setTopHeight(10);
        drawable.setBottomHeight(10);
        return drawable;
    }
}
