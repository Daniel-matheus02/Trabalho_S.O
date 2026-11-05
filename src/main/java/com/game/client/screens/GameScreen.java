package com.game.client.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.client.controller.MatchSession;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.network.ClientEvent;
import com.game.core.model.Card;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.LogEntry;
import com.game.core.state.PlayerView;
import com.game.launcher.DuelGame;
import com.game.rendering.CardHand;
import com.game.rendering.CombatRevealPanel;
import com.game.rendering.DraftPanel;
import com.game.rendering.Palette;
import com.game.rendering.PlayerPanel;
import com.game.rendering.ScorePanel;
import com.game.rendering.Ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A mesa: os dois jogadores, barras de vida, mão, prévia da pontuação ao vivo, draft e revelação. */
public final class GameScreen extends BaseScreen {

    private final MatchSession session;
    private final Runnable cleanup;
    private final int myId;
    private final ScoringEngine scoring = new ScoringEngine();

    private final PlayerPanel opponentPanel;
    private final PlayerPanel ownPanel;
    private final CardHand hand;
    private final com.badlogic.gdx.scenes.scene2d.ui.Label handPlaceholder;
    private final Table stageArea;
    private final Table actionBar;
    private final ScorePanel scorePanel;
    private final Table logPanel;
    private final Table logContent;
    private final ScrollPane logScroll;
    private final Label roundLabel;
    private final Label turnLabel;
    private final Label globalName;
    private final Label globalDesc;
    private final Label statusLabel;
    private final DraftPanel draftPanel;
    private final CombatRevealPanel revealPanel;

    private final Set<Integer> selection = new LinkedHashSet<>();
    private List<Card> renderedHand = List.of();
    private GameSnapshot snapshot;
    private long lastVersion = -1;
    private String stageSignature = "";
    private String actionSignature = "";
    private String logSignature = "";
    private int lastOutcomeRound = -1;
    private boolean submittedLocally;
    private boolean keepSessionAlive;
    private float gameOverDelay = -1f;
    private Table toast;

    public GameScreen(DuelGame game, MatchSession session, Runnable cleanup) {
        super(game);
        this.session = session;
        this.cleanup = cleanup;
        this.myId = session.myPlayerId();

        this.opponentPanel = new PlayerPanel(assets, opponentId(), true);
        this.ownPanel = new PlayerPanel(assets, myId, false, true);
        this.hand = new CardHand(assets);
        this.hand.setOnToggle(this::toggleCard);
        this.handPlaceholder = Ui.label(skin, "", "body-dim");
        this.handPlaceholder.setAlignment(Align.center);
        this.handPlaceholder.setVisible(false);
        this.stageArea = Ui.panel(assets, "panelGreen");
        this.stageArea.pad(16).top().left();
        this.actionBar = new Table();
        this.scorePanel = new ScorePanel(assets);
        this.roundLabel = Ui.label(skin, "", "gold-small");
        this.turnLabel = Ui.label(skin, "", "body-dim");
        this.globalName = Ui.label(skin, "", "h3");
        this.globalDesc = Ui.wrapLabel(skin, "", "small");
        this.statusLabel = Ui.label(skin, "", "small");
        this.logContent = new Table();
        this.logContent.top().left();
        this.logScroll = new ScrollPane(logContent, skin);
        this.logScroll.setFadeScrollBars(false);
        this.logScroll.setScrollingDisabled(true, false);
        this.logPanel = Ui.panel(assets, "panelDeep");
        this.logPanel.pad(10).top().left();
        this.draftPanel = new DraftPanel(assets);
        this.revealPanel = new CombatRevealPanel(assets);

        build();
        refresh(true);
    }

    private int opponentId() {
        return myId == 1 ? 2 : 1;
    }

    private void build() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(14).top();
        stage.addActor(root);

        Table header = new Table();
        header.add(Ui.label(skin, "DUELO DE FICHAS", "h2")).left();

        Table roundPill = new Table();
        roundPill.setBackground(new NinePatchDrawable(new com.badlogic.gdx.graphics.g2d.NinePatch(
                assets.patch("pillWhite"))).tint(Palette.rgba(0xe6b64c, 0.18f)));
        roundPill.add(roundLabel).pad(5, 12, 5, 12);

        header.add().expandX();
        header.add(roundPill).padRight(14);
        header.add(turnLabel).padRight(14);
        header.add(Ui.button(skin, "MENU", "ghost", this::leaveToMenu)).width(90).height(34);
        root.add(header).growX().height(40).padBottom(8).row();

        Table body = new Table();

        Table left = new Table();
        left.add(opponentPanel).growX().height(104).padBottom(6).row();
        left.add(stageArea).growX().minHeight(190).expandY().padBottom(6).row();
        left.add(ownPanel).growX().height(80).padBottom(4).row();
        Stack handStack = new Stack(hand, handPlaceholder);
        left.add(handStack).growX().height(132).padBottom(2).row();
        left.add(actionBar).growX().height(42).row();
        body.add(left).grow().padRight(14);

        Table right = new Table();
        Table globalPanel = Ui.panel(assets, "panelGold");
        globalPanel.pad(12).top().left();
        globalPanel.add(Ui.label(skin, "MODIFICADOR GLOBAL", "gold-small")).left().row();
        globalPanel.add(globalName).left().padTop(2).row();
        globalPanel.add(globalDesc).width(292).left().padTop(2).row();
        right.add(globalPanel).growX().height(98).padBottom(8).row();
        right.add(scorePanel).growX().height(250).padBottom(8).row();

        logPanel.add(Ui.label(skin, "LOG DO PROTOCOLO", "gold-small")).left().row();
        logPanel.add(logScroll).grow().padTop(6).row();
        right.add(logPanel).grow().row();

        body.add(right).width(320).growY();
        root.add(body).grow().row();
        root.add(statusLabel).growX().height(20).padTop(4);
    }

    // ------------------------------------------------------------------ quadro

    @Override
    public void render(float delta) {
        session.tick(delta);
        handleEvents();
        refresh(false);
        if (gameOverDelay >= 0) {
            gameOverDelay -= delta;
            if (gameOverDelay <= 0) {
                goToGameOver();
            }
        }
        super.render(delta);
    }

    private void handleEvents() {
        ClientEvent event;
        while ((event = session.pollEvent()) != null) {
            switch (event) {
                case ClientEvent.ErrorReceived error ->
                        showToast(error.code() + ": " + error.message(), Palette.BAD);
                case ClientEvent.ConnectionLost lost -> {
                    statusLabel.setText("CONEX\u00c3O PERDIDA \u2014 RECONECTANDO...");
                    showToast("CONEX\u00c3O PERDIDA", Palette.BAD);
                }
                case ClientEvent.Reconnecting reconnecting ->
                        statusLabel.setText("RECONECTANDO (" + reconnecting.attempt() + "/"
                                + reconnecting.maxAttempts() + ")...");
                case ClientEvent.Reconnected reconnected -> {
                    statusLabel.setText("RECONECTADO AO SERVIDOR");
                    showToast("RECONECTADO", Palette.GOOD);
                }
                case ClientEvent.ReconnectFailed failed ->
                        showToast("FALHA AO RECONECTAR: " + failed.reason(), Palette.BAD);
                case ClientEvent.PlayerStatus status -> {
                    if (!status.payload().connected()) {
                        showToast(status.payload().playerName() + " DESCONECTOU", Palette.BAD);
                    }
                }
                case ClientEvent.RoundResolved resolved ->
                        showToast(resolved.payload().outcome().isTie()
                                ? "EMPATE \u2014 NINGU\u00c9M PERDE VIDA"
                                : "JOGADOR " + resolved.payload().outcome().loser()
                                + " PERDE " + resolved.payload().outcome().damage() + " HP",
                                resolved.payload().outcome().isTie() ? Palette.INK_DIM : Palette.BAD);
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ atualização

    private void refresh(boolean force) {
        GameSnapshot state = session.snapshot();
        if (state == null) {
            statusLabel.setText("AGUARDANDO O IN\u00cdCIO DA PARTIDA...");
            return;
        }
        if (!force && state.version() == lastVersion) {
            return;
        }
        snapshot = state;
        lastVersion = state.version();

        PlayerView me = state.player(myId);
        PlayerView opponent = state.opponentOf(myId);

        roundLabel.setText(state.phase() == GamePhase.GAME_OVER ? "FIM DE JOGO" : "RODADA " + state.round());
        globalName.setText(state.globalModifierName());
        globalDesc.setText(state.globalModifierDescription());
        ownPanel.update(me, state.isTurnOf(myId), "SUA VEZ");
        opponentPanel.update(opponent, state.isTurnOf(opponent.id()), "VEZ DO OPONENTE");
        turnLabel.setText(headerStatus(state));

        List<Card> ownHand = me.hand();
        if (!ownHand.equals(renderedHand)) {
            renderedHand = ownHand;
            selection.clear();
            submittedLocally = false;
        }
        selection.removeIf(index -> index >= renderedHand.size());
        boolean interactive = canInteract(state);
        hand.setCards(renderedHand, interactive);
        hand.setSelectedIndices(selection);
        boolean handMissing = renderedHand.isEmpty();
        handPlaceholder.setVisible(handMissing);
        handPlaceholder.setText(state.phase() == GamePhase.REVEAL
                ? "SUA MÃO VOLTA NA PRÓXIMA RODADA"
                : "FIM DA PARTIDA");
        updatePreview();

        rebuildStage(state, me, opponent);
        rebuildActions(state, me);
        refreshLog(state);

        if (state.lastOutcome() == null) {
            lastOutcomeRound = -1;
        } else if (state.lastOutcome().round() != lastOutcomeRound) {
            lastOutcomeRound = state.lastOutcome().round();
            animateCombat(state.lastOutcome());
        }

        if (state.phase() == GamePhase.GAME_OVER && gameOverDelay < 0) {
            gameOverDelay = game.settings().reducedMotion() ? 0.35f : 1.1f;
        }

        String networkStatus = session.statusMessage();
        if (networkStatus != null && !networkStatus.isBlank()) {
            statusLabel.setText(networkStatus);
        } else if (state.phase() == GamePhase.DRAFT) {
            statusLabel.setText("DRAFT \u2014 CADA JOGADOR ESCOLHE 1 JOKER");
        } else if (state.phase() == GamePhase.REVEAL) {
            statusLabel.setText("REVELA\u00c7\u00c3O \u2014 COMPARE AS JOGADAS");
        } else {
            statusLabel.setText("PROTOCOLO: " + state.globalModifierId()
                    + " \u00b7 VERS\u00c3O " + state.version());
        }
    }

    private String headerStatus(GameSnapshot state) {
        if (state.phase() == GamePhase.GAME_OVER) {
            return "FIM DE JOGO";
        }
        if (state.phase() == GamePhase.REVEAL) {
            return "COMBATE REVELADO";
        }
        if (state.phase() == GamePhase.DRAFT) {
            return state.isTurnOf(myId) ? "SUA VEZ \u2014 ESCOLHA UM JOKER" : "VEZ DO OPONENTE";
        }
        if (state.isTurnOf(myId)) {
            return submittedLocally ? "JOGADA ENVIADA" : "SUA VEZ \u2014 MONTE SUA JOGADA";
        }
        return "VEZ DO OPONENTE";
    }

    private boolean canInteract(GameSnapshot state) {
        if (state.phase() != GamePhase.BUILD || state.currentPlayer() != myId) {
            return false;
        }
        if (submittedLocally) {
            return false;
        }
        if (session instanceof NetworkMatchSession network) {
            return network.client().isConnected();
        }
        return true;
    }

    private void toggleCard(int index) {
        if (snapshot == null || !canInteract(snapshot)) {
            return;
        }
        if (!selection.remove(index)) {
            if (selection.size() >= snapshot.rules().maxPlay()) {
                return;
            }
            selection.add(index);
        }
        hand.setSelectedIndices(selection);
        updatePreview();
        rebuildActions(snapshot, snapshot.player(myId));
        rebuildStage(snapshot, snapshot.player(myId), snapshot.opponentOf(myId));
    }

    private List<Card> selectedCards() {
        List<Card> cards = new ArrayList<>();
        for (int index : selection) {
            if (index < renderedHand.size()) {
                cards.add(renderedHand.get(index));
            }
        }
        return cards;
    }

    private void updatePreview() {
        if (snapshot == null) {
            return;
        }
        PlayerView me = snapshot.player(myId);
        if (snapshot.phase() == GamePhase.REVEAL && snapshot.lastOutcome() != null) {
            ScoreResult own = myId == 1
                    ? snapshot.lastOutcome().player1Score()
                    : snapshot.lastOutcome().player2Score();
            scorePanel.update(own, true);
            return;
        }
        if (snapshot.phase() == GamePhase.DRAFT) {
            scorePanel.showMessage("DRAFT DE JOKERS",
                    "ESCOLHA UM DOS JOKERS DO PALCO. ELE VALE PARA TODAS AS PRÓXIMAS RODADAS.");
            return;
        }
        if (snapshot.phase() == GamePhase.GAME_OVER) {
            scorePanel.showMessage("FIM DE JOGO", "O RESULTADO FINAL APARECE NA TELA SEGUINTE.");
            return;
        }
        ScoreResult preview = scoring.score(selectedCards(), me.jokers(), snapshot.globalModifierId());
        scorePanel.update(preview, !selection.isEmpty() && snapshot.phase() == GamePhase.BUILD);
    }

    private void submitPlay() {
        if (snapshot == null || !canInteract(snapshot) || selection.isEmpty()) {
            return;
        }
        List<Card> cards = selectedCards();
        session.submitPlay(cards);
        submittedLocally = true;
        selection.clear();
        hand.setSelectedIndices(selection);
        hand.setInteractive(false);
        updatePreview();
        rebuildActions(snapshot, snapshot.player(myId));
        rebuildStage(snapshot, snapshot.player(myId), snapshot.opponentOf(myId));
        refresh(true);
    }

    // ------------------------------------------------------------------ palco

    private void rebuildStage(GameSnapshot state, PlayerView me, PlayerView opponent) {
        String signature = state.phase() + ":" + state.round() + ":" + state.currentPlayer()
                + ":" + me.ready() + ":" + submittedLocally + ":" + selection.size()
                + ":" + me.offers() + ":" + (state.lastOutcome() == null ? 0 : state.lastOutcome().round())
                + ":" + me.hp() + ":" + opponent.hp();
        if (signature.equals(stageSignature)) {
            return;
        }
        stageSignature = signature;
        stageArea.clearChildren();

        switch (state.phase()) {
            case BUILD -> buildTurnStage(state, me, opponent);
            case REVEAL -> {
                stageArea.add(revealPanel).grow().row();
                if (state.lastOutcome() != null) {
                    revealPanel.update(state.lastOutcome(),
                            state.player(1), state.player(2));
                }
            }
            case DRAFT -> {
                boolean myTurn = state.isTurnOf(myId);
                draftPanel.update(me.offers(), myTurn && !me.ready(), me.ready(), this::pickJoker);
                stageArea.add(draftPanel).grow().row();
            }
            case GAME_OVER -> {
                stageArea.add(Ui.label(skin, "FIM DE JOGO", "winner")).padBottom(10).row();
                stageArea.add(Ui.label(skin, "CALCULANDO RESULTADO FINAL...", "body-dim")).row();
            }
            default -> stageArea.add(Ui.label(skin, "AGUARDANDO...", "body-dim")).row();
        }
    }

    private void buildTurnStage(GameSnapshot state, PlayerView me, PlayerView opponent) {
        boolean myTurn = state.isTurnOf(myId);
        String whoStyle = myId == 1 ? "h2-p1" : "h2-p2";
        if (myTurn && !submittedLocally) {
            stageArea.add(Ui.label(skin, "SUA VEZ \u2014 MONTE SUA JOGADA", whoStyle))
                    .left().padBottom(6).row();
            stageArea.add(Ui.label(skin,
                    "SELECIONE DE 1 A 5 CARTAS. A PR\u00c9VIA \u00c0 DIREITA MOSTRA FICHAS \u00d7 MULT.",
                    "body-dim")).left().row();
            stageArea.add(Ui.label(skin, selection.size() + " / " + state.rules().maxPlay()
                    + " CARTAS SELECIONADAS", "small")).left().padTop(10).row();
        } else if (submittedLocally) {
            stageArea.add(Ui.label(skin, "JOGADA ENVIADA", "gold")).left().padBottom(6).row();
            stageArea.add(Ui.label(skin, "AGUARDANDO O OPONENTE ENVIAR A JOGADA...", "body-dim"))
                    .left().row();
            stageArea.add(Ui.label(skin, "O SERVIDOR VALIDA E CALCULA O COMBATE.", "small"))
                    .left().padTop(10).row();
        } else {
            stageArea.add(Ui.label(skin, opponent.name().toUpperCase()
                    + " EST\u00c1 MONTANDO A JOGADA", myId == 1 ? "h2-p2" : "h2-p1"))
                    .left().padBottom(6).row();
            stageArea.add(Ui.label(skin, "AS CARTAS DO OPONENTE PERMANECEM OCULTAS.", "body-dim"))
                    .left().row();
            stageArea.add(Ui.label(skin, "AGUARDE A VEZ...", "small")).left().padTop(10).row();
        }
        stageArea.add().expandY().row();

        Table rules = new Table();
        rules.left();
        rules.add(Ui.label(skin, "COMO PONTUA", "gold-small")).left().padBottom(4).row();
        rules.add(Ui.label(skin, "FICHAS = BASE DA M\u00c3O + VALOR DAS CARTAS", "small"))
                .left().row();
        rules.add(Ui.label(skin, "MULT = MULTIPLICADOR DA M\u00c3O + MODIFICADOR GLOBAL + JOKERS",
                "small")).left().row();
        rules.add(Ui.label(skin, "PONTOS = FICHAS \u00d7 MULT   \u00b7   DANO = DIFEREN\u00c7A / 3",
                "small")).left().row();
        stageArea.add(rules).left();
    }

    private void pickJoker(int index) {
        if (snapshot == null || snapshot.phase() != GamePhase.DRAFT || !snapshot.isTurnOf(myId)) {
            return;
        }
        session.pickJoker(index);
        refresh(true);
    }

    // ------------------------------------------------------------------ ações

    private void rebuildActions(GameSnapshot state, PlayerView me) {
        String signature = state.phase() + ":" + state.currentPlayer() + ":" + me.ready()
                + ":" + submittedLocally + ":" + selection.size() + ":" + me.rerolls()
                + ":" + (session instanceof NetworkMatchSession network && network.client().isConnected());
        if (signature.equals(actionSignature)) {
            return;
        }
        actionSignature = signature;
        actionBar.clearChildren();
        Table info = new Table();
        info.left();
        Table buttons = new Table();

        switch (state.phase()) {
            case BUILD -> {
                if (state.isTurnOf(myId) && !submittedLocally) {
                    info.add(Ui.label(skin, selection.isEmpty()
                            ? "SELECIONE AO MENOS 1 CARTA" : "PRONTO PARA ENVIAR", "small")).left();
                    TextButton clear = Ui.button(skin, "LIMPAR", "ghost", this::clearSelection);
                    clear.setDisabled(selection.isEmpty());
                    TextButton submit = Ui.button(skin, "ENVIAR JOGADA", "gold", this::submitPlay);
                    submit.setDisabled(selection.isEmpty());
                    buttons.add(clear).width(130).height(44).padRight(10);
                    buttons.add(submit).width(210).height(44);
                } else {
                    Label waiting = Ui.label(skin, submittedLocally
                            ? "AGUARDANDO O OPONENTE..." : "AGUARDANDO SUA VEZ...", "body-dim");
                    info.add(waiting).left();
                }
            }
            case REVEAL -> {
                info.add(Ui.label(skin, me.ready()
                        ? "VOC\u00ca J\u00c1 CONFIRMOU. AGUARDANDO O OPONENTE..."
                        : "CONFIRME PARA IR AO DRAFT DE JOKERS", "small")).left();
                TextButton advance = Ui.button(skin, me.ready() ? "AGUARDANDO..." : "CONTINUAR", "gold",
                        this::advance);
                advance.setDisabled(me.ready());
                buttons.add(advance).width(210).height(44);
            }
            case DRAFT -> {
                if (state.isTurnOf(myId) && !me.ready()) {
                    info.add(Ui.label(skin, "CLIQUE EM UM DOS 3 JOKERS ACIMA", "small")).left();
                    TextButton reroll = Ui.button(skin, "ROLAR DE NOVO (RESTAM "
                            + me.rerolls() + ")", "green", this::reroll);
                    reroll.setDisabled(me.rerolls() <= 0);
                    buttons.add(reroll).width(290).height(44);
                } else {
                    info.add(Ui.label(skin, me.ready()
                            ? "ESCOLHA REGISTRADA. AGUARDANDO O OPONENTE..."
                            : "AGUARDANDO O OPONENTE ESCOLHER...", "body-dim")).left();
                }
            }
            case GAME_OVER -> info.add(Ui.label(skin, "FIM DA PARTIDA", "small")).left();
            default -> {
            }
        }

        actionBar.add(info).left().expandX();
        actionBar.add(buttons).right();
    }

    private void clearSelection() {
        selection.clear();
        hand.setSelectedIndices(selection);
        updatePreview();
        if (snapshot != null) {
            rebuildActions(snapshot, snapshot.player(myId));
            rebuildStage(snapshot, snapshot.player(myId), snapshot.opponentOf(myId));
        }
    }

    private void advance() {
        session.advance();
        refresh(true);
    }

    private void reroll() {
        session.reroll();
        refresh(true);
    }

    // ------------------------------------------------------------------ animação de combate

    private void animateCombat(RoundOutcome outcome) {
        if (outcome.damage() <= 0 || game.settings().reducedMotion()) {
            return;
        }
        if (outcome.loser() == myId) {
            ownPanel.flashDamage();
        } else {
            opponentPanel.flashDamage();
        }
        Label damage = Ui.label(skin, "\u2212" + outcome.damage() + " HP", "damage");
        damage.setAlignment(Align.center);
        damage.setPosition(stage.getWidth() / 2f - 80, stage.getHeight() * 0.68f);
        stage.addActor(damage);
        damage.addAction(Actions.sequence(
                Actions.parallel(Actions.moveBy(0, 70, 0.65f, Interpolation.pow2Out),
                        Actions.sequence(Actions.delay(0.45f), Actions.fadeOut(0.35f))),
                Actions.removeActor()));
        shake(stageArea);
    }

    private void shake(Actor actor) {
        actor.clearActions();
        actor.addAction(Actions.sequence(
                Actions.moveBy(-7, 0, 0.03f),
                Actions.moveBy(14, 0, 0.05f),
                Actions.moveBy(-10, 0, 0.05f),
                Actions.moveBy(6, 0, 0.04f),
                Actions.moveBy(-3, 0, 0.04f)));
    }

    private void showToast(String text, Color color) {
        if (toast != null) {
            toast.remove();
        }
        Table panel = Ui.panel(assets, "panelDeep");
        panel.pad(10, 18, 10, 18);
        Label label = Ui.label(skin, text, "body");
        label.setColor(color);
        panel.add(label);
        panel.pack();
        panel.setPosition((stage.getWidth() - panel.getWidth()) / 2f, stage.getHeight() * 0.86f);
        panel.getColor().a = 0f;
        panel.addAction(Actions.sequence(Actions.fadeIn(0.15f), Actions.delay(2.2f),
                Actions.fadeOut(0.35f), Actions.removeActor()));
        stage.addActor(panel);
        toast = panel;
    }

    // ------------------------------------------------------------------ registro

    private void refreshLog(GameSnapshot state) {
        if (!game.settings().showProtocolLog()) {
            if (!"hidden".equals(logSignature)) {
                logSignature = "hidden";
                logContent.clearChildren();
                logContent.add(Ui.label(skin, "LOG OCULTO \u2014 ATIVE EM CONFIGURA\u00c7\u00d5ES",
                        "small")).left();
            }
            return;
        }
        StringBuilder signature = new StringBuilder();
        for (LogEntry entry : state.log()) {
            signature.append(entry.text()).append('|').append(entry.style()).append('\n');
        }
        String value = signature.toString();
        if (value.equals(logSignature)) {
            return;
        }
        logSignature = value;
        logContent.clearChildren();
        if (state.log().isEmpty()) {
            logContent.add(Ui.label(skin, "(SEM MENSAGENS AINDA)", "small")).left();
            return;
        }
        for (LogEntry entry : state.log()) {
            String style = switch (entry.style()) {
                case "res" -> "log-gold";
                case "fim" -> "log-bad";
                case "draft" -> "log-draft";
                default -> "log";
            };
            Label line = Ui.label(skin, entry.text(), style);
            line.setWrap(true);
            line.setAlignment(Align.left);
            logContent.add(line).left().growX().padBottom(2).row();
        }
        logContent.pack();
        logScroll.layout();
        logScroll.setScrollPercentY(1f);
    }

    // ------------------------------------------------------------------ navegação

    private void goToGameOver() {
        gameOverDelay = -1f;
        if (keepSessionAlive) {
            return;
        }
        keepSessionAlive = true;
        game.showGameOver(session, cleanup);
    }

    private void leaveToMenu() {
        keepSessionAlive = false;
        game.showMainMenu();
    }

    public void demoSelect(int count) {
        if (snapshot == null) {
            return;
        }
        int max = Math.min(count, renderedHand.size());
        selection.clear();
        for (int i = 0; i < max; i++) {
            selection.add(i);
        }
        hand.setSelectedIndices(selection);
        updatePreview();
        rebuildActions(snapshot, snapshot.player(myId));
        rebuildStage(snapshot, snapshot.player(myId), snapshot.opponentOf(myId));
    }

    @Override
    protected void onEscape() {
        leaveToMenu();
    }

    @Override
    public void dispose() {
        if (!keepSessionAlive) {
            session.close();
            if (cleanup != null) {
                cleanup.run();
            }
        }
        super.dispose();
    }
}
