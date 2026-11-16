package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.game.assets.GameAssets;
import com.game.core.state.PlayerView;

import java.util.ArrayList;
import java.util.List;

/** Nome, barra de vida, selo de turno e lista de curingas de um lado da mesa. */
public final class PlayerPanel extends Table {

    private final GameAssets assets;
    private final Skin skin;
    private final int playerId;
    private final boolean showOpponentHand;

    private final Label nameLabel;
    private final Label hpLabel;
    private final Label statusLabel;
    private final Label turnLabel;
    private final Table turnPill;
    private final HealthBar healthBar;
    private final Table backRow;

    private int shownHp = -1;
    private String backsSignature = "";
    private boolean showPowerCards = true;
    private final List<CardActor> backActors = new ArrayList<>();

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
        turnPill.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDeep"))));
        turnLabel = Ui.label(skin, "", "gold-small");
        turnPill.add(turnLabel).pad(compact ? 2 : 4, compact ? 8 : 10,
                compact ? 2 : 4, compact ? 8 : 10);
        turnPill.setVisible(false);
        hpLabel = Ui.label(skin, "", compact ? "body" : "hp-number");
        statusLabel = Ui.label(skin, "", "small");

        Table head = new Table();
        head.add(nameLabel).left().expandX();
        head.add(turnPill).padRight(compact ? 8 : 14);
        head.add(hpLabel).right();
        add(head).growX().row();

        healthBar = new HealthBar(assets, playerId == 1 ? Palette.P1 : Palette.P2);
        add(healthBar).growX().height(compact ? 12 : 16)
                .padTop(compact ? 4 : 8).row();

        Table bottom = new Table();
        bottom.add(statusLabel).left().padRight(8).expandX();
        backRow = new Table();
        if (showOpponentHand) {
            bottom.add(backRow).right();
        }
        add(bottom).growX().padTop(compact ? 4 : 8).row();
    }

    public void update(PlayerView view, boolean isTurn, String turnText) {
        update(view, isTurn, turnText, view.hp(), true);
    }

    /**
     * Atualiza o painel com uma vida EXIBIDA separada da vida real e com a escolha entre mostrar
     * as cartas de poder ocultas ou as cartas restantes na mão.
     */
    public void update(PlayerView view, boolean isTurn, String turnText, int displayedHp) {
        update(view, isTurn, turnText, displayedHp, true);
    }

    public void update(PlayerView view, boolean isTurn, String turnText, int displayedHp,
                       boolean showPowers) {
        showPowerCards = showPowers;
        nameLabel.setText(view.name().toUpperCase());
        hpLabel.setText(displayedHp + " / " + view.maxHp());
        if (shownHp >= 0 && displayedHp < shownHp) {
            hpLabel.clearActions();
            hpLabel.setColor(Palette.BAD);
            hpLabel.addAction(Actions.color(Color.WHITE, 0.55f));
        }
        shownHp = displayedHp;
        healthBar.setValues(displayedHp, view.maxHp(), true);
        int shownCount = showPowerCards && view.powerCount() > 0
                ? view.powerCount() : view.handCount();
        statusLabel.setText((showPowerCards && view.powerCount() > 0
                ? "PODERES " : "CARTAS ") + shownCount);

        turnLabel.setText(turnText);
        turnPill.setVisible(isTurn);

        if (showOpponentHand) {
            updateBacks(view);
        }
    }

    public void flashDamage() {
        healthBar.flash();
    }

    /** Desliga tremor, rastro e partículas (usado pela preferência de movimento reduzido). */
    public void setEffectsEnabled(boolean enabled) {
        healthBar.setEffectsEnabled(enabled);
    }

    private void updateBacks(PlayerView view) {
        boolean powers = showPowerCards && view.powerCount() > 0;
        int count = powers ? view.powerCount() : view.handCount();
        String signature = (powers ? "P" : "H") + count;
        if (signature.equals(backsSignature)) {
            return;
        }
        backsSignature = signature;
        backRow.clearChildren();
        backActors.clear();
        if (count <= 0) {
            backRow.add(Ui.label(skin, "SEM CARTAS", "small"));
            return;
        }
        int shown = Math.min(count, 7);
        for (int i = 0; i < shown; i++) {
            CardActor back = CardActor.decorative(assets, null, true, 18);
            backRow.add(back).width(18f).height(25f).padRight(3);
            backActors.add(back);
        }
    }

    /** Versos atualmente exibidos, usados pela queima da mão do oponente. */
    public List<CardActor> backActors() {
        return List.copyOf(backActors);
    }

    /** Restaura os versos após uma apresentação de resolução/replay. */
    public void resetBackVisuals() {
        for (CardActor actor : backActors) {
            actor.clearActions();
            actor.setVisible(true);
            actor.setScale(1f, 1f);
            actor.getColor().a = 1f;
        }
    }

    public int playerId() {
        return playerId;
    }
}
