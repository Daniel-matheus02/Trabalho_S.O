package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.rendering.Typography;
import com.game.core.sabotage.SabotageDef;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.JokerDef;
import com.game.core.scoring.Jokers;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Loja entre rodadas: três ofertas de joker (com reroll grátis ou pago) e três sabotagens
 * sorteadas para comprar com moedas.
 */
public final class ShopPanel extends Table {

    private final GameAssets assets;
    private final Skin skin;

    public ShopPanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        top().left();
    }

    public void update(GameSnapshot state, PlayerView me, boolean canAct,
                       IntConsumer onPickJoker, IntConsumer onBuySabotage) {
        clearChildren();
        add(Ui.label(skin, "LOJA DA RODADA " + state.round(), "h3")).left().row();
        Label hint = Ui.wrapLabel(skin,
                "ESCOLHA 1 JOKER POR RODADA. AS SABOTAGENS SÃO DE USO ÚNICO E FICAM GUARDADAS ATÉ VOCÊ USAR "
                        + "(MÁXIMO " + state.rules().maxSabotages() + ").",
                "small");
        add(hint).growX().left().padTop(2).row();

        Table columns = new Table();
        columns.add(jokerColumn(me, canAct, onPickJoker)).growX().uniformX().top();
        columns.add(sabotageColumn(state, me, canAct, onBuySabotage))
                .growX().uniformX().top().padLeft(12);
        add(columns).growX().left().padTop(8).row();
    }

    private Table jokerColumn(PlayerView me, boolean canAct, IntConsumer onPick) {
        Table column = new Table();
        column.left().top();
        column.add(Ui.label(skin, me.jokerPicked() ? "JOKER ESCOLHIDO" : "JOKERS · ESCOLHA 1",
                "gold-small")).left().row();
        Table row = new Table();
        List<String> offers = me.offers();
        for (int i = 0; i < offers.size(); i++) {
            final int index = i;
            boolean enabled = canAct && !me.jokerPicked();
            row.add(card(Jokers.nameOf(offers.get(i)), Jokers.descriptionOf(offers.get(i)),
                    enabled, "panelGold", () -> onPick.accept(index)))
                    .width(0).growX().uniformX().height(92).padRight(6);
        }
        column.add(row).growX().left().padTop(4).row();
        return column;
    }

    private Table sabotageColumn(GameSnapshot state, PlayerView me, boolean canAct, IntConsumer onBuy) {
        Table column = new Table();
        column.left().top();
        column.add(Ui.label(skin, "SABOTAGENS · " + state.rules().sabotagePrice() + " MOEDAS CADA",
                "gold-small")).left().row();
        Table row = new Table();
        List<String> offers = me.sabotageOffers();
        boolean handFull = me.sabotageCount() >= state.rules().maxSabotages();
        for (int i = 0; i < offers.size(); i++) {
            final int index = i;
            SabotageDef def = Sabotages.byId(offers.get(i));
            boolean enabled = canAct && !handFull && me.coins() >= state.rules().sabotagePrice();
            String description = def == null ? "" : def.description();
            row.add(card(def == null ? offers.get(i) : def.name(), description,
                    enabled, "panelGreen", () -> onBuy.accept(index)))
                    .width(0).growX().uniformX().height(92).padRight(6);
        }
        column.add(row).growX().left().padTop(4).row();
        String footer = handFull
                ? "MÃO DE SABOTAGENS CHEIA"
                : "VOCÊ TEM " + me.coins() + " MOEDAS";
        column.add(Ui.label(skin, footer, "small")).left().padTop(2).row();
        if (!me.sabotageHand().isEmpty()) {
            StringBuilder owned = new StringBuilder("SUAS: ");
            for (int i = 0; i < me.sabotageHand().size(); i++) {
                if (i > 0) {
                    owned.append(" · ");
                }
                owned.append(Sabotages.nameOf(me.sabotageHand().get(i)));
            }
            column.add(Ui.label(skin, owned.toString(), "small")).left().padTop(2).row();
        }
        return column;
    }

    private Table card(String title, String description, boolean enabled, String patch,
                       Runnable action) {
        Table card = new Table();
        NinePatchDrawable base = new NinePatchDrawable(new NinePatch(assets.patch(patch)));
        card.setBackground(base);
        card.pad(8).top().left();
        card.add(Ui.wrapLabel(skin, title, "caption")).growX().left().row();
        Label desc = Ui.wrapLabel(skin, description, Typography.CAPTION);
        card.add(desc).growX().left().padTop(3).row();

        if (enabled) {
            card.setTouchable(Touchable.enabled);
            card.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    action.run();
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
            card.setColor(1f, 1f, 1f, 0.55f);
        }
        return card;
    }
}
