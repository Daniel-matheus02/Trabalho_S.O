package com.game.client.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.game.client.controller.MatchSession;
import com.game.client.network.ClientEvent;
import com.game.core.scoring.Jokers;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;
import com.game.launcher.DuelGame;
import com.game.rendering.Palette;
import com.game.rendering.Ui;

import java.util.List;

/** Vencedor, estatísticas finais, revanche e retorno ao menu principal. */
public final class GameOverScreen extends BaseScreen {

    private Ui.Ambience ambience;
    private Ui.GlowActor glow;
    private final MatchSession session;
    private final Runnable cleanup;

    private final GameSnapshot snapshot;
    private final int winner;
    private final Label statusLabel;
    private boolean handedOff;
    private boolean rematchRequested;

    public GameOverScreen(DuelGame game, MatchSession session, Runnable cleanup) {
        super(game);
        this.session = session;
        this.cleanup = cleanup;
        this.snapshot = session.snapshot();
        this.winner = snapshot == null ? 0 : snapshot.winner();
        this.statusLabel = Ui.label(skin, "", "small");
        build();
    }

    private void build() {
        ambience = new Ui.Ambience(assets);
        ambience.setBounds(0, 0, DuelGame.VIEW_WIDTH, DuelGame.VIEW_HEIGHT);
        stage.addActor(ambience);

        glow = new Ui.GlowActor(assets.glow());
        glow.setBounds(340, 380, 600, 320);
        stage.addActor(glow);

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        root.pad(24);

        if (snapshot == null) {
            root.add(Ui.label(skin, "PARTIDA ENCERRADA", "title")).row();
            root.add(Ui.button(skin, "VOLTAR AO MENU", "gold", () -> game.showMainMenu()))
                    .width(360).height(52).padTop(20).row();
            stage.addActor(root);
            return;
        }

        PlayerView winnerView = winner == 0 ? null : snapshot.player(winner);
        Label title = Ui.label(skin, winner == 0 ? "EMPATE" : "VIT\u00d3RIA", "title");
        if (winnerView != null) {
            title.setColor(winner == 1 ? Palette.P1 : Palette.P2);
        }
        root.add(title).row();
        if (winnerView != null) {
            root.add(Ui.label(skin, winnerView.name().toUpperCase() + " VENCEU O DUELO", "h2"))
                    .padTop(4).row();
        }
        String rounds = snapshot.round() == 1 ? "1 RODADA" : snapshot.round() + " RODADAS";
        root.add(Ui.label(skin, "A PARTIDA DUROU " + rounds, "body-dim"))
                .padTop(2).padBottom(18).row();

        Table stats = new Table();
        stats.add(statsPanel(snapshot.player(1))).width(420).padRight(16);
        stats.add(statsPanel(snapshot.player(2))).width(420);
        root.add(stats).padBottom(18).row();

        Table actions = new Table();
        actions.add(Ui.button(skin, "REVANCHE", "gold", this::requestRematch))
                .width(230).height(52).padRight(14);
        actions.add(Ui.button(skin, "MENU PRINCIPAL", "green", this::goToMenu))
                .width(230).height(52);
        root.add(actions).row();
        root.add(statusLabel).padTop(14).row();

        stage.addActor(root);
    }

    private Table statsPanel(PlayerView view) {
        Table panel = Ui.panel(assets, winner == view.id() ? "panelGold" : "panelDark");
        panel.pad(16).top().left();
        panel.add(Ui.label(skin, view.name().toUpperCase(),
                winner == view.id() ? (view.id() == 1 ? "h2-p1" : "h2-p2") : "h2"))
                .left().padBottom(6).row();
        panel.add(Ui.label(skin, "HP FINAL: " + view.hp() + " / " + view.maxHp(), "body"))
                .left().row();

        Table hpRow = new Table();
        hpRow.left();
        hpRow.add(hpBar(view)).width(300).height(12).padTop(6).padBottom(8);
        panel.add(hpRow).left().row();

        panel.add(Ui.label(skin, "JOKERS", "gold-small")).left().padTop(4).row();
        if (view.jokers().isEmpty()) {
            panel.add(Ui.label(skin, "NENHUM JOKER ADQUIRIDO", "small")).left().row();
        } else {
            for (String id : view.jokers()) {
                panel.add(Ui.label(skin, "\u2605 " + Jokers.nameOf(id) + " \u2014 "
                        + Jokers.descriptionOf(id), "small")).left().row();
            }
        }
        panel.add(Ui.label(skin, "JOGADAS ENVIADAS: " + (view.submitted() ? "SIM" : "\u2014"),
                "small")).left().padTop(6).row();
        return panel;
    }

    private com.badlogic.gdx.scenes.scene2d.Actor hpBar(PlayerView view) {
        com.game.rendering.HealthBar bar = new com.game.rendering.HealthBar(assets,
                view.id() == 1 ? Palette.P1 : Palette.P2);
        bar.setValues(view.hp(), view.maxHp(), false);
        return bar;
    }

    private void requestRematch() {
        if (rematchRequested) {
            return;
        }
        rematchRequested = true;
        session.requestRematch();
        statusLabel.setText(session.isSinglePlayer()
                ? "INICIANDO NOVA PARTIDA..." : "AGUARDANDO O OPONENTE ACEITAR A REVANCHE...");
        if (session.isSinglePlayer()) {
            GameSnapshot state = session.snapshot();
            if (state != null && state.phase() != GamePhase.GAME_OVER) {
                openGame();
            }
        }
    }

    @Override
    protected void onResize(float width, float height) {
        if (ambience == null) {
            return;
        }
        ambience.setBounds(0, 0, width, height);
        glow.setBounds(width * 0.27f, height * 0.42f, width * 0.46f, height * 0.52f);
    }

    @Override
    public void render(float delta) {
        ClientEvent event;
        while ((event = session.pollEvent()) != null) {
            if (event instanceof ClientEvent.GameStarted) {
                openGame();
                return;
            } else if (event instanceof ClientEvent.ErrorReceived error) {
                statusLabel.setText(error.code() + ": " + error.message());
                statusLabel.setColor(Palette.BAD);
            } else if (event instanceof ClientEvent.PlayerStatus status
                    && !status.payload().connected()) {
                statusLabel.setText("OPONENTE DESCONECTOU. AGUARDANDO RECONEX\u00c3O...");
                statusLabel.setColor(Palette.BAD);
            }
        }
        if (session.isSinglePlayer()) {
            GameSnapshot state = session.snapshot();
            if (state != null && state.phase() != GamePhase.GAME_OVER && rematchRequested) {
                openGame();
                return;
            }
        }
        super.render(delta);
    }

    private void openGame() {
        if (handedOff) {
            return;
        }
        handedOff = true;
        game.showGameScreen(session, cleanup);
    }

    private void goToMenu() {
        handedOff = false;
        game.showMainMenu();
    }

    @Override
    protected void onEscape() {
        goToMenu();
    }

    @Override
    public void dispose() {
        if (!handedOff) {
            session.close();
            if (cleanup != null) {
                cleanup.run();
            }
        }
        super.dispose();
    }
}
