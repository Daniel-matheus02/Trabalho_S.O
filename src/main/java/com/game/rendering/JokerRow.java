package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.core.scoring.Jokers;

import java.util.ArrayList;
import java.util.List;

/** Linha compacta dos curingas adquiridos. */
public final class JokerRow extends Table {

    private final GameAssets assets;
    private final Skin skin;
    private List<String> shown = List.of();

    public JokerRow(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        left();
    }

    public void update(List<String> jokers) {
        List<String> next = jokers == null ? List.of() : jokers;
        if (next.equals(shown)) {
            return;
        }
        shown = new ArrayList<>(next);
        clearChildren();
        if (shown.isEmpty()) {
            add(Ui.label(skin, "SEM JOKERS AINDA", "small")).left();
            return;
        }
        for (String id : shown) {
            Table chip = new Table();
            chip.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDeep"))));
            Image star = Ui.image(assets, "star");
            star.setColor(Palette.GOLD);
            chip.add(star).size(10).padLeft(6).padRight(4);
            chip.add(Ui.label(skin, Jokers.nameOf(id), "gold-small")).padRight(8).padTop(3).padBottom(3);
            add(chip).padRight(4);
        }
    }
}
