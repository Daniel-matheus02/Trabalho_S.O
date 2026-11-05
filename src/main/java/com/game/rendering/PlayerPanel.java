package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.core.state.PlayerView;

/** Nome, barra de vida, selo de turno e lista de curingas de um lado da mesa. */
public final class PlayerPanel extends Table {

    private final GameAssets assets;
    private final Skin skin;
    private final int playerId;
    private final boolean showOpponentHand;

    private final Label nameLabel;
    private final Label hpLabel;
    private final Label turnLabel;
    private final Table turnPill;
    private final HealthBar healthBar;
    private final JokerRow jokerRow;
    private final Table backRow;

    private int shownBacks = -1;

    public PlayerPanel(GameAssets assets, int playerId, boolean showOpponentHand) {
        this(assets, playerId, showOpponentHand, false);
    }

    public PlayerPanel(GameAssets assets, int playerId, boolean showOpponentHand, boolean compact) {
        this.assets = assets;
        this.skin = assets.skin();
        this.playerId = playerId;
        this.showOpponentHand = showOpponentHand;

        setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDark"))));
        pad(compact ? 8 : 12);

        nameLabel = Ui.label(skin, "", compact
                ? (playerId == 1 ? "p1" : "p2") : (playerId == 1 ? "h2-p1" : "h2-p2"));
        turnPill = new Table();
        turnPill.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                .tint(Palette.rgba(0xe6b64c, 0.22f)));
        turnLabel = Ui.label(skin, "", "gold-small");
        turnPill.add(turnLabel).pad(compact ? 2 : 4, compact ? 8 : 10,
                compact ? 2 : 4, compact ? 8 : 10);
        turnPill.setVisible(false);
        hpLabel = Ui.label(skin, "", compact ? "body" : "hp-number");

        Table head = new Table();
        head.add(nameLabel).left().expandX();
        head.add(turnPill).padRight(compact ? 8 : 14);
        head.add(hpLabel).right();
        add(head).growX().row();

        healthBar = new HealthBar(assets, playerId == 1 ? Palette.P1 : Palette.P2);
        add(healthBar).growX().height(compact ? 12 : 16)
                .padTop(compact ? 4 : 8).row();

        Table bottom = new Table();
        jokerRow = new JokerRow(assets);
        bottom.add(jokerRow).left().expandX();
        backRow = new Table();
        if (showOpponentHand) {
            bottom.add(backRow).right();
        }
        add(bottom).growX().padTop(compact ? 4 : 8).row();
    }

    public void update(PlayerView view, boolean isTurn, String turnText) {
        nameLabel.setText(view.name().toUpperCase());
        hpLabel.setText(view.hp() + " / " + view.maxHp());
        healthBar.setValues(view.hp(), view.maxHp(), true);
        jokerRow.update(view.jokers());

        turnLabel.setText(turnText);
        turnPill.setVisible(isTurn);

        if (showOpponentHand) {
            updateBacks(view.handCount());
        }
    }

    public void flashDamage() {
        healthBar.flash();
    }

    private void updateBacks(int count) {
        if (count == shownBacks) {
            return;
        }
        shownBacks = count;
        backRow.clearChildren();
        if (count <= 0) {
            backRow.add(Ui.label(skin, "SEM CARTAS", "small"));
            return;
        }
        CardActor back = CardActor.decorative(assets, null, true, 26);
        backRow.add(back).padRight(7);
        backRow.add(Ui.label(skin, count + " CARTAS", "small"));
    }

    public int playerId() {
        return playerId;
    }
}
