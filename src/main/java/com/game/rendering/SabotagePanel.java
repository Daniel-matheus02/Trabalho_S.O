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
import com.game.core.model.Card;
import com.game.core.sabotage.SabotageDef;
import com.game.core.sabotage.Sabotages;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Mesa congelada no river e escolha de sabotagem. Mostra a ordem de resolução da rodada em
 * destaque — ela alterna a cada rodada desde a v0.5.
 */
public final class SabotagePanel extends Table {

    private final GameAssets assets;
    private final Skin skin;

    public SabotagePanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        top().left();
    }

    public void update(GameSnapshot state, PlayerView me, boolean canAct,
                       IntConsumer onUse, Runnable onPass) {
        clearChildren();
        int opponent = me.id() == 1 ? 2 : 1;
        String order = "RODADA " + state.round() + " · JOGADOR " + state.firstResolver()
                + " RESOLVE PRIMEIRO";
        Table header = new Table();
        header.add(Ui.label(skin, "SABOTAGEM", "h3")).left().expandX();
        Label orderLabel = Ui.label(skin, order, "gold-small");
        header.add(orderLabel).right();
        add(header).growX().row();
        add(Ui.label(skin, "A MESA ESTÁ CONGELADA NO RIVER. APPLIQUE UMA SABOTAGEM OU PASSE.",
                "small")).left().padTop(2).row();

        add(communityRow(state.community())).left().padTop(6).row();

        if (me.sabotageChosen()) {
            add(Ui.label(skin, "ESCOLHA REGISTRADA. AGUARDANDO O OPONENTE...", "good"))
                    .left().padTop(8).row();
            if (state.player(opponent).sabotageChosen()) {
                add(Ui.label(skin, "RESOLVENDO A RODADA...", "body-dim")).left().padTop(2).row();
            }
            return;
        }

        Table handRow = new Table();
        List<String> hand = me.sabotageHand();
        for (int i = 0; i < hand.size(); i++) {
            final int index = i;
            SabotageDef def = Sabotages.byId(hand.get(i));
            handRow.add(sabotageCard(def == null ? hand.get(i) : def.name(),
                    def == null ? "" : def.description(), canAct, () -> onUse.accept(index)))
                    .width(210).height(74).padRight(6);
        }
        if (hand.isEmpty()) {
            handRow.add(Ui.label(skin, "VOCÊ NÃO TEM SABOTAGENS GUARDADAS.", "body-dim")).left();
        }
        add(handRow).left().padTop(8).row();

        if (canAct) {
            Label hint = Ui.label(skin, "CLIQUE EM UMA CARTA PARA USAR, OU PASSE.", "small");
            add(hint).left().padTop(6).row();
        }
    }

    private Table communityRow(List<Card> community) {
        Table row = new Table();
        row.add(Ui.label(skin, "MESA ", "gold-small")).padRight(8);
        for (Card card : community) {
            CardActor actor = CardActor.decorative(assets, card, false, 42);
            actor.showInstantly();
            row.add(actor).size(42, 58).padRight(4);
        }
        if (community.isEmpty()) {
            row.add(Ui.label(skin, "FECHADA", "small"));
        }
        return row;
    }

    private Table sabotageCard(String title, String description, boolean enabled, Runnable action) {
        Table card = new Table();
        NinePatchDrawable base = new NinePatchDrawable(new NinePatch(assets.patch("panelGold")));
        card.setBackground(base);
        card.pad(7).top().left();
        card.add(Ui.label(skin, title, "body")).left().row();
        card.add(Ui.wrapLabel(skin, description, "small")).width(194).left().padTop(2).row();
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
            card.setColor(1f, 1f, 1f, 0.65f);
        }
        return card;
    }
}
