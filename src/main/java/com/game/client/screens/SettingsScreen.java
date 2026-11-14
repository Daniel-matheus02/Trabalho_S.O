package com.game.client.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.game.launcher.DuelGame;
import com.game.rendering.Ui;

/** Preferências de nome do jogador, animação e log do protocolo. */
public final class SettingsScreen extends BaseScreen {

    private final Table root = new Table();
    private Table panel;

    public SettingsScreen(DuelGame game) {
        super(game);
        build();
    }

    private void build() {
        root.setFillParent(true);
        root.center();

        panel = Ui.panel(assets, "panelDark");
        panel.pad(34).defaults().padBottom(12);

        panel.add(Ui.label(skin, "CONFIGURA\u00c7\u00d5ES", "h1")).padBottom(22).row();
        panel.add(Ui.label(skin, "NOME DO JOGADOR", "h3")).left().row();

        TextField nameField = new TextField(game.settings().playerName(), skin);
        nameField.setMessageText("SEU NOME");
        nameField.setMaxLength(20);
        nameField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.settings().setPlayerName(nameField.getText());
            }
        });
        panel.add(nameField).width(440).height(48).left().row();

        TextButton motion = Ui.button(skin, motionLabel(), "green", null);
        motion.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.settings().setReducedMotion(!game.settings().reducedMotion());
                motion.setText(motionLabel());
            }
        });
        panel.add(motion).width(440).height(46).padTop(14).left().row();

        TextButton log = Ui.button(skin, logLabel(), "green", null);
        log.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.settings().setShowProtocolLog(!game.settings().showProtocolLog());
                log.setText(logLabel());
            }
        });
        panel.add(log).width(440).height(46).left().row();

        Label note = Ui.wrapLabel(skin,
                "O LOG MOSTRA AS MENSAGENS DO PROTOCOLO TROCADAS DURANTE A PARTIDA.",
                "body-dim");
        panel.add(note).width(440).left().padTop(10).row();

        panel.add(Ui.button(skin, "VOLTAR", "gold", () -> game.showMainMenu()))
                .width(440).height(52).padTop(24).row();

        root.add(panel).width(560);
        stage.addActor(root);
    }

    private String motionLabel() {
        return "ANIMA\u00c7\u00d5ES: " + (game.settings().reducedMotion() ? "REDUZIDAS" : "COMPLETAS");
    }

    private String logLabel() {
        return "LOG DO PROTOCOLO: " + (game.settings().showProtocolLog() ? "VIS\u00cdVEL" : "OCULTO");
    }

    /** Mantém o painel dentro da largura da janela em qualquer resolução. */
    @Override
    protected void onResize(float width, float height) {
        if (panel != null && root.getCell(panel) != null) {
            root.getCell(panel).width(Math.max(320f, Math.min(560f, width - 48f)));
        }
    }

    @Override
    protected void onEscape() {
        game.showMainMenu();
    }
}
