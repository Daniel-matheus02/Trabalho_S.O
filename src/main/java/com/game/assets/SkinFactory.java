package com.game.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.game.rendering.Palette;
import com.game.rendering.Typography;

/** Constrói o skin do Scene2D inteiramente a partir da arte gerada e da fonte pixel. */
public final class SkinFactory {

    private SkinFactory() {
    }

    public static Skin build(GameAssets assets) {
        Skin skin = new Skin();
        Typography.registerStyles(skin, assets);
        addButtonStyles(skin, assets);
        addTextFieldStyle(skin, assets);
        addScrollPaneStyle(skin, assets);
        return skin;
    }

    private static void addButtonStyles(Skin skin, GameAssets assets) {
        BitmapFont body = Typography.font(assets, Typography.Level.HEADING);
        Color darkInk = Palette.rgb(0x06241a);

        TextButton.TextButtonStyle gold = new TextButton.TextButtonStyle();
        gold.up = drawable(assets, "buttonGold");
        gold.over = drawable(assets, "buttonGold").tint(Palette.tint(Palette.GOLD, 0.08f));
        gold.down = drawable(assets, "buttonGold").tint(Palette.GOLD_DARK);
        gold.font = body;
        gold.fontColor = darkInk;
        gold.overFontColor = darkInk;
        gold.downFontColor = Palette.rgb(0x0d2d21);
        gold.disabled = drawable(assets, "buttonGold").tint(Palette.rgba(0x6f7a6f, 0.5f));
        gold.disabledFontColor = Palette.rgba(0x06241a, 0.5f);
        gold.pressedOffsetY = -2;
        skin.add("gold", gold, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle green = new TextButton.TextButtonStyle();
        green.up = drawable(assets, "buttonGreen");
        green.over = drawable(assets, "buttonGreen").tint(Palette.rgb(0x2a6b53));
        green.down = drawable(assets, "buttonGreen").tint(Palette.rgb(0x0d2d21));
        green.font = body;
        green.fontColor = Palette.INK;
        green.overFontColor = Palette.WHITE;
        green.disabled = drawable(assets, "buttonGreen").tint(Palette.rgba(0x6f7a6f, 0.5f));
        green.disabledFontColor = Palette.rgba(0xeae6d8, 0.5f);
        green.pressedOffsetY = -2;
        skin.add("green", green, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle danger = new TextButton.TextButtonStyle();
        danger.up = drawable(assets, "buttonDanger");
        danger.over = drawable(assets, "buttonDanger").tint(Palette.rgb(0x9a392e));
        danger.down = drawable(assets, "buttonDanger").tint(Palette.rgb(0x4a1812));
        danger.font = body;
        danger.fontColor = Palette.INK;
        danger.disabled = drawable(assets, "buttonDanger").tint(Palette.rgba(0x6f7a6f, 0.5f));
        danger.disabledFontColor = Palette.rgba(0xeae6d8, 0.5f);
        danger.pressedOffsetY = -2;
        skin.add("danger", danger, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle ghost = new TextButton.TextButtonStyle();
        ghost.up = drawable(assets, "buttonGhost");
        ghost.over = drawable(assets, "buttonGhost").tint(Palette.rgb(0x2a6b53));
        ghost.down = drawable(assets, "buttonGhost").tint(Palette.rgb(0x0d2d21));
        ghost.font = Typography.font(assets, Typography.Level.HEADING);
        ghost.fontColor = Palette.INK;
        ghost.overFontColor = Palette.INK;
        ghost.disabled = drawable(assets, "buttonGhost").tint(Palette.rgba(0x6f7a6f, 0.4f));
        ghost.disabledFontColor = Palette.rgba(0xeae6d8, 0.45f);
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
        style.font = Typography.font(assets, Typography.Level.HEADING);
        style.fontColor = Palette.INK;
        style.messageFont = Typography.font(assets, Typography.Level.HEADING);
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
