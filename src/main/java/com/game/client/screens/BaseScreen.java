package com.game.client.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.game.assets.GameAssets;
import com.game.launcher.DuelGame;
import com.game.rendering.Palette;
import com.game.rendering.Ui;

/** Infraestrutura compartilhada de stage/viewport/plano de fundo para todas as telas. */
public abstract class BaseScreen extends ScreenAdapter {

    protected final DuelGame game;
    protected final GameAssets assets;
    protected final Skin skin;
    protected final Stage stage;
    protected final Viewport viewport;
    protected final Ui.FeltBackground background;

    protected BaseScreen(DuelGame game) {
        this.game = game;
        this.assets = game.assets();
        this.skin = assets.skin();
        this.viewport = new ScreenViewport();
        this.stage = new Stage(viewport);
        this.background = new Ui.FeltBackground(assets);
        this.background.setSize(Math.max(1, Gdx.graphics.getWidth()),
                Math.max(1, Gdx.graphics.getHeight()));
        stage.addActor(background);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        onResize(viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            onEscape();
        }
        stage.act(Math.min(delta, 1f / 20f));
        stage.draw();
    }

    /** Sobrescreva para navegação com Escape. */
    protected void onEscape() {
        // sem operação por padrão
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        background.setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        onResize(viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    /** Chamado após a mudança do viewport; as telas posicionam decorações absolutas aqui. */
    protected void onResize(float width, float height) {
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
