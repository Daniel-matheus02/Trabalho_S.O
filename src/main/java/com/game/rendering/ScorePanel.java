package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.core.scoring.LineStyle;
import com.game.core.scoring.ScoreLine;
import com.game.core.scoring.ScoreResult;

/** Prévia ao vivo de fichas x multiplicador com o detalhamento completo da pontuação. */
public final class ScorePanel extends Table {

    private final GameAssets assets;
    private final Skin skin;

    public ScorePanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelGreen"))));
        pad(14);
        top().left();
    }

    public void showMessage(String title, String hint) {
        clearChildren();
        add(Ui.label(skin, title, "h3")).left().row();
        Label text = Ui.wrapLabel(skin, hint, "body-dim");
        add(text).left().growX().padTop(8).row();
        add().expandY().row();
    }

    public void update(ScoreResult result, boolean hasSelection) {
        clearChildren();
        if (!hasSelection || result == null) {
            add(Ui.label(skin, "PR\u00c9VIA DA JOGADA", "h3")).left().row();
            Label prompt = Ui.label(skin, "SELECIONE DE 1 A 5 CARTAS", "body-dim");
            prompt.setWrap(true);
            add(prompt).left().growX().padTop(8).row();
            add(Ui.label(skin, "FICHAS \u00d7 MULT = PONTOS", "small")).left().padTop(14).row();
            return;
        }

        add(Ui.label(skin, result.type().displayName(), "h3")).left().row();

        Table scoreRow = new Table();
        scoreRow.add(badge(Integer.toString(result.chips()), "FICHAS", Palette.CHIPS))
                .width(92).height(54);
        scoreRow.add(Ui.label(skin, "\u00d7", "h2")).padLeft(6).padRight(6);
        scoreRow.add(badge(Integer.toString(result.mult()), "MULT", Palette.MULT))
                .width(80).height(54);
        scoreRow.add(Ui.label(skin, "=", "h2")).padLeft(6).padRight(6);
        Label finalLabel = Ui.label(skin, Integer.toString(result.finalScore()), "final");
        scoreRow.add(finalLabel).left();
        add(scoreRow).left().padTop(8).row();

        Table lines = new Table();
        lines.left().top();
        for (ScoreLine line : result.lines()) {
            String style = switch (line.style()) {
                case PLUS -> "line-plus-body";
                case MULT -> "line-mult-body";
                case NORMAL -> "line-normal-body";
            };
            Label label = Ui.label(skin, line.text(), style);
            label.setWrap(true);
            label.setAlignment(Align.left);
            lines.add(label).left().growX().row();
        }
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
                new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(lines, skin);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        add(scroll).left().grow().padTop(8).expandY().top().row();
    }

    private Table badge(String value, String unit, Color color) {
        Table table = new Table();
        NinePatchDrawable background = new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")));
        table.setBackground(background.tint(color));
        table.add(Ui.label(skin, value, "h2")).padTop(4).row();
        table.add(Ui.label(skin, unit, "white-small")).padBottom(3);
        return table;
    }
}
