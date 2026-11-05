package com.game.client.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.game.core.model.Card;
import com.game.launcher.DuelGame;
import com.game.rendering.CardActor;
import com.game.rendering.Ui;

/** Tela de título com a mesa de feltro, naipes à deriva e os quatro pontos de entrada. */
public final class MainMenuScreen extends BaseScreen {

    private Ui.Ambience ambience;
    private Ui.GlowActor glow;
    private CardActor ace;
    private CardActor king;

    public MainMenuScreen(DuelGame game) {
        super(game);
        build();
    }

    private void build() {
        ambience = new Ui.Ambience(assets);
        ambience.setBounds(0, 0, DuelGame.VIEW_WIDTH, DuelGame.VIEW_HEIGHT);
        stage.addActor(ambience);

        glow = new Ui.GlowActor(assets.glow());
        glow.setBounds(280, 400, 720, 320);
        stage.addActor(glow);

        ace = CardActor.decorative(assets, Card.fromCode("AE"), false, 108);
        ace.setOrigin(54, 74);
        ace.setRotation(-13);
        ace.getColor().a = 0.62f;
        stage.addActor(ace);

        king = CardActor.decorative(assets, Card.fromCode("KO"), false, 108);
        king.setOrigin(54, 74);
        king.setRotation(11);
        king.getColor().a = 0.62f;
        stage.addActor(king);

        Table content = new Table();
        content.setFillParent(true);
        content.center();

        Label title = Ui.label(skin, "DUELO DE FICHAS", "title");
        content.add(title).padBottom(10).row();

        Label tagline = Ui.label(skin, "FICHAS \u00d7 MULT \u00b7 JOKERS \u00b7 UM DUELO 1V1", "subtitle");
        content.add(tagline).padBottom(38).row();

        Table buttons = new Table();
        buttons.add(Ui.button(skin, "JOGAR CONTRA A CPU", "gold", game::showSinglePlayer))
                .width(430).height(58).padBottom(12).row();
        buttons.add(Ui.button(skin, "MULTIPLAYER NA LAN", "green", game::showMultiplayer))
                .width(430).height(58).padBottom(12).row();
        buttons.add(Ui.button(skin, "CONFIGURA\u00c7\u00d5ES", "ghost", game::showSettings))
                .width(430).height(44).padBottom(8).row();
        buttons.add(Ui.button(skin, "SAIR", "ghost", () -> Gdx.app.exit()))
                .width(430).height(44).row();
        content.add(buttons).row();

        Label hint = Ui.label(skin,
                "REDUZA A VIDA DO OPONENTE A ZERO PARA VENCER O DUELO",
                "body-dim");
        content.add(hint).padTop(26).row();

        stage.addActor(content);

        Table footer = new Table();
        footer.setFillParent(true);
        footer.bottom().padBottom(18);
        footer.add(Ui.label(skin, "JAVA 21 \u00b7 LIBGDX \u00b7 LWJGL3", "small")).row();
        footer.add(Ui.label(skin, "VERS\u00c3O 1.0", "small")).padTop(4);
        stage.addActor(footer);
    }

    @Override
    protected void onResize(float width, float height) {
        if (ambience == null) {
            return;
        }
        ambience.setBounds(0, 0, width, height);
        glow.setBounds(width * 0.22f, height * 0.42f, width * 0.56f, height * 0.54f);
        ace.setPosition(width * 0.10f, height * 0.50f);
        king.setPosition(width * 0.78f, height * 0.55f);
    }
}
