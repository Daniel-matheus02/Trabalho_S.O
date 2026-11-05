package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.core.scoring.JokerDef;
import com.game.core.scoring.Jokers;

import java.util.List;
import java.util.function.IntConsumer;

/** Três curingas oferecidos; clicar em um deles o escolhe. */
public final class DraftPanel extends Table {

    private final GameAssets assets;
    private final Skin skin;

    public DraftPanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        top().left();
    }

    public void update(List<String> offers, boolean myTurn, boolean ready, IntConsumer onPick) {
        clearChildren();
        add(Ui.label(skin, "ESCOLHA 1 JOKER", "h3")).left().row();
        Label hint = Ui.wrapLabel(skin,
                "AS MESMAS 3 OP\u00c7\u00d5ES S\u00c3O OFERECIDAS AOS DOIS. "
                        + "O JOKER ESCOLHIDO \u00c9 S\u00d3 SEU E ACUMULA PARA AS PR\u00d3XIMAS RODADAS.",
                "small");
        add(hint).width(800).left().padTop(2).row();

        Table grid = new Table();
        for (int i = 0; i < offers.size(); i++) {
            grid.add(offerCard(offers.get(i), i, myTurn, onPick)).width(250).height(132).padRight(10);
        }
        add(grid).left().padTop(12).row();

        if (!myTurn) {
            add(Ui.label(skin, "AGUARDANDO O OPONENTE ESCOLHER...", "body-dim")).left().padTop(10).row();
        } else if (ready) {
            add(Ui.label(skin, "ESCOLHA REGISTRADA. AGUARDANDO O OPONENTE...", "good"))
                    .left().padTop(10).row();
        }
    }

    private Table offerCard(String jokerId, int index, boolean enabled, IntConsumer onPick) {
        JokerDef def = Jokers.byId(jokerId);
        Table card = new Table();
        NinePatchDrawable base = new NinePatchDrawable(new NinePatch(assets.patch("panelGold")));
        card.setBackground(base);
        card.pad(12).top().left();

        card.add(Ui.label(skin, def == null ? jokerId : def.name(), "h3")).left().row();
        Label description = Ui.wrapLabel(skin, def == null ? "" : def.description(), "body-dim");
        card.add(description).width(220).left().padTop(6).row();

        if (enabled) {
            card.setTouchable(Touchable.enabled);
            card.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onPick.accept(index);
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer,
                                  com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                    card.setBackground(base.tint(Palette.rgb(0x3b2f1a)));
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer,
                                 com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                    card.setBackground(base);
                }
            });
        } else {
            card.setTouchable(Touchable.disabled);
            card.setColor(1f, 1f, 1f, 0.8f);
        }
        return card;
    }
}
