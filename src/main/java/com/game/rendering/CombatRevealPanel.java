package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.core.model.Card;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.state.PlayerView;

import java.util.List;

/** A tela de revelação: as duas jogadas, suas pontuações e o dano causado. */
public final class CombatRevealPanel extends Table {

    private final GameAssets assets;
    private final Skin skin;

    public CombatRevealPanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        top();
    }

    public void update(RoundOutcome outcome, PlayerView player1, PlayerView player2) {
        clearChildren();
        add(Ui.label(skin, "COMBATE DA RODADA " + outcome.round(), "h3")).left().padBottom(8).row();

        Table row = new Table();
        row.add(side(player1, outcome.player1Cards(), outcome.player1Score(),
                outcome.winner() == 1, "p1")).growX().expandX().fillX();
        row.add(center(outcome)).width(170).padLeft(10).padRight(10);
        row.add(side(player2, outcome.player2Cards(), outcome.player2Score(),
                outcome.winner() == 2, "p2")).growX().expandX().fillX();
        add(row).growX().row();
    }

    private Table side(PlayerView player, List<Card> cards, ScoreResult score,
                       boolean winner, String styleName) {
        Table panel = Ui.panel(assets, winner ? "panelGold" : "panelGreen");
        panel.pad(10).top().left();
        panel.add(Ui.label(skin, player.name().toUpperCase()
                + (winner ? "  \u00b7  VENCEU" : ""), widthStyle(styleName))).left().row();

        Table cardRow = new Table();
        for (Card card : cards) {
            CardActor actor = CardActor.decorative(assets, card, false, 36);
            cardRow.add(actor).size(36, 50).padRight(3);
        }
        panel.add(cardRow).left().padTop(6).row();
        panel.add(Ui.label(skin, score.type().displayName(), "h3")).left().padTop(4).row();

        Table scoreRow = new Table();
        scoreRow.add(Ui.label(skin, Integer.toString(score.chips()), "chips")).padRight(4);
        scoreRow.add(Ui.label(skin, "\u00d7", "small")).padRight(4);
        scoreRow.add(Ui.label(skin, Integer.toString(score.mult()), "mult")).padRight(6);
        scoreRow.add(Ui.label(skin, "=", "body-dim")).padRight(6);
        scoreRow.add(Ui.label(skin, Integer.toString(score.finalScore()), "final"));
        panel.add(scoreRow).left().padTop(2).row();
        return panel;
    }

    private String widthStyle(String styleName) {
        return styleName.equals("p1") ? "p1" : "p2";
    }

    private Table center(RoundOutcome outcome) {
        Table middle = new Table();
        middle.add(Ui.label(skin, "VS", "h2")).row();
        String damageText = outcome.damage() == 0 ? "EMPATE" : "\u2212" + outcome.damage() + " HP";
        String damageStyle = outcome.damage() == 0 ? "body-dim" : "bad";
        middle.add(Ui.label(skin, damageText, damageStyle)).padTop(6).row();
        String loserText = outcome.damage() == 0
                ? "NINGU\u00c9M PERDE VIDA"
                : "JOGADOR " + outcome.loser() + " PERDE VIDA";
        Label hint = Ui.label(skin, loserText, "small");
        hint.setWrap(true);
        hint.setAlignment(com.badlogic.gdx.utils.Align.center);
        middle.add(hint).width(150).padTop(4).row();
        return middle;
    }
}
