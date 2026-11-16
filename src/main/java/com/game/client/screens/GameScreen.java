package com.game.client.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.client.animation.ScoreCountAnimator;
import com.game.client.animation.ScoreFeel;
import com.game.client.controller.MatchSession;
import com.game.client.controller.LocalMatchSession;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.dev.DevPreview;
import com.game.client.layout.LayoutMetrics;
import com.game.client.network.ClientEvent;
import com.game.client.presentation.RoundPresenter;
import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.RoundOutcome;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.GlobalModifiers;
import com.game.core.scoring.Jokers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.LogEntry;
import com.game.core.state.PlayerView;
import com.game.launcher.DuelGame;
import com.game.rendering.CardActor;
import com.game.rendering.CardHand;
import com.game.rendering.CardTooltip;
import com.game.rendering.FixedHeightScrollPane;
import com.game.rendering.HandBurnEffect;
import com.game.rendering.Palette;
import com.game.rendering.PlayerPanel;
import com.game.rendering.PowerHand;
import com.game.rendering.ResolutionEffect;
import com.game.rendering.ScorePanel;
import com.game.rendering.SharedTablePanel;
import com.game.rendering.Typography;
import com.game.rendering.Ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A mesa: os dois jogadores, barras de vida, mão, prévia da pontuação ao vivo, poderes e revelação. */
public final class GameScreen extends BaseScreen {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(GameScreen.class.getName());

    private final MatchSession session;
    private final Runnable cleanup;
    private final int myId;
    private final ScoringEngine scoring = new ScoringEngine();

    private final PlayerPanel opponentPanel;
    private final PlayerPanel ownPanel;
    private final CardHand hand;
    private final com.badlogic.gdx.scenes.scene2d.ui.Label handPlaceholder;
    private final Stack handStack;
    private final Table stageArea;
    private final Table actionBar;
    private final ScorePanel scorePanel;
    private final Table logPanel;
    private final Table logContent;
    private final FixedHeightScrollPane logScroll;
    private final Label roundLabel;
    private final Label turnLabel;
    private final Label globalName;
    private final Label globalDesc;
    private final Label statusLabel;
    private final PowerHand powerHand;
    private final SharedTablePanel sharedTable;
    private final CardTooltip cardTooltip;
    private final HandBurnEffect burnEffect;
    private final ResolutionEffect resolutionEffect;
    private final ScoreCountAnimator countAnimator;
    private final RoundPresenter presenter;
    private final boolean devPreview = DevPreview.enabled();
    private final Vector2 scratch = new Vector2();
    private List<CardActor> landingActors;
    private List<Card> submittedCards = List.of();
    private List<Integer> submittedIndices = List.of();

    private final Set<Integer> selection = new LinkedHashSet<>();
    private List<Card> renderedHand = List.of();
    private List<PowerCard> renderedPowerHand = List.of();
    private int renderedPowerRound = -1;
    private long lastRecoveryAt = -10_000L;
    private GameSnapshot snapshot;
    private long lastVersion = -1;
    private String actionSignature = "";
    private String logSignature = "";
    private GamePhase layoutPhase;
    private boolean submittedLocally;
    private boolean keepSessionAlive;
    private boolean refreshing;
    private boolean refreshPending;
    private float gameOverDelay = -1f;
    private Table toast;

    // Estrutura responsiva: cabeçalho + corpo (coluna esquerda + barra lateral).
    private final Table root = new Table();
    private final Table header = new Table();
    private final Table titleBox = new Table();
    private final Table headerPills = new Table();
    private final FillTable body = new FillTable();
    private final Table left = new Table();
    private final Table right = new Table();
    private boolean stacked;
    private float layoutWidth = -1f;
    private float layoutHeight = -1f;
    private LayoutMetrics metrics;

    /** Altura reservada ao log: o conteúdo rola dentro dela e nunca empurra o layout. */
    private static final float LOG_PREF_HEIGHT = 100f;
    /** Largura fixa da barra lateral; abaixo do limite o corpo inteiro empilha. */
    private static final float SIDEBAR_WIDTH = 320f;
    /** Abaixo desta largura disponível o layout empilha (cabeçalho e barra lateral). */
    private static final float STACK_WIDTH = 1120f;

    public GameScreen(DuelGame game, MatchSession session, Runnable cleanup) {
        super(game);
        this.session = session;
        this.cleanup = cleanup;
        this.myId = session.myPlayerId();

        this.opponentPanel = new PlayerPanel(assets, opponentId(), true);
        this.ownPanel = new PlayerPanel(assets, myId, false, true);
        this.hand = new CardHand(assets);
        this.hand.setOnToggle(this::toggleCard);
        this.hand.setAnimateDeals(!game.settings().reducedMotion());
        this.handPlaceholder = Ui.label(skin, "", "body-dim");
        this.handPlaceholder.setAlignment(Align.center);
        this.handPlaceholder.setVisible(false);
        this.powerHand = new PowerHand(assets);
        this.powerHand.setAnimateDeals(!game.settings().reducedMotion());
        this.powerHand.setOnToggle(this::togglePower);
        this.powerHand.setOnSelectionChanged(this::onPowerSelectionChanged);
        this.handStack = new Stack(hand, handPlaceholder, powerHand);
        this.stageArea = Ui.panel(assets, "panelGreen");
        this.stageArea.pad(16).top().left();
        this.actionBar = new Table();
        this.scorePanel = new ScorePanel(assets);
        this.roundLabel = Ui.label(skin, "", Typography.HEADER);
        this.turnLabel = Ui.label(skin, "", Typography.HEADER);
        this.globalName = Ui.label(skin, "", "h3");
        this.globalDesc = Ui.wrapLabel(skin, "", "small");
        this.statusLabel = Ui.label(skin, "", "small");
        this.logContent = new Table();
        this.logContent.top().left();
        this.logScroll = new FixedHeightScrollPane(logContent, skin, LOG_PREF_HEIGHT);
        this.logScroll.setFadeScrollBars(false);
        this.logScroll.setScrollingDisabled(true, false);
        this.logPanel = Ui.panel(assets, "panelDeep");
        this.logPanel.pad(10).top().left();
        this.sharedTable = new SharedTablePanel(assets);
        this.cardTooltip = new CardTooltip(assets, stage);
        this.powerHand.setTooltip(cardTooltip);
        this.sharedTable.setTooltip(cardTooltip);
        boolean reducedMotion = game.settings().reducedMotion();
        this.sharedTable.setAnimate(!reducedMotion);
        this.ownPanel.setEffectsEnabled(!reducedMotion);
        this.opponentPanel.setEffectsEnabled(!reducedMotion);
        this.countAnimator = new ScoreCountAnimator(sharedTable, assets, !reducedMotion,
                this::shakeScreen);
        this.burnEffect = new HandBurnEffect(assets, stage, hand, opponentPanel,
                this::shakeScreen);
        this.resolutionEffect = new ResolutionEffect(assets, stage, sharedTable, powerHand,
                opponentPanel, cardTooltip, myId);
        this.presenter = new RoundPresenter(myId, reducedMotion, countAnimator,
                new RoundPresenter.BurnPlayer() {
                    @Override
                    public void play(Runnable onFinished) {
                        burnEffect.play(onFinished);
                    }

                    @Override
                    public void finishNow() {
                        burnEffect.finishNow();
                    }
                },
                new RoundPresenter.ResolutionPlayer() {
                    @Override
                    public void play(com.game.core.rules.RoundOutcome outcome,
                                     Runnable onFinished) {
                        resolutionEffect.play(outcome, onFinished);
                    }

                    @Override
                    public void finishNow() {
                        resolutionEffect.finishNow();
                    }
                }, this::onPresentationChanged, this::animateOutcomeDamage);

        build();
        stage.addActor(cardTooltip);
        refresh(true);
    }

    private int opponentId() {
        return myId == 1 ? 2 : 1;
    }

    private void build() {
        root.setFillParent(true);
        root.pad(14).top();
        stage.addActor(root);

        titleBox.left();
        titleBox.add(Ui.label(skin, "DUELO DE FICHAS", Typography.TITLE)).left();

        Table roundPill = pill(roundLabel);
        Table turnPill = pill(turnLabel);
        headerPills.add(roundPill).height(36).padRight(10);
        headerPills.add(turnPill).height(36).padRight(12);
        headerPills.add(Ui.button(skin, "MENU", "ghost", this::leaveToMenu))
                .width(90).height(36);

        root.add(header).growX().height(56).padBottom(8).row();

        buildLeftColumn();
        buildRightColumn();

        body.top().left();
        root.add(body).grow().row();

        root.add(statusLabel).growX().height(22).padTop(4);

        relayout(stage.getWidth(), stage.getHeight(), true);
    }

    private Table pill(Label label) {
        // Fundo escuro: as duas pílulas do cabeçalho usam o MESMO fundo, padding e fonte, com
        // contraste alto para o texto HEADER.
        Table pill = new Table();
        pill.setBackground(new NinePatchDrawable(
                new com.badlogic.gdx.graphics.g2d.NinePatch(assets.patch("panelDeep"))));
        pill.add(label).pad(6, 14, 6, 14);
        return pill;
    }

    private void buildLeftColumn() {
        left.top().left();
        stageArea.clearChildren();
        stageArea.add(sharedTable).grow().padBottom(4).row();
        left.add(opponentPanel).growX().fillY().height(92).padBottom(4).row();
        left.add(stageArea).grow().height(196).padBottom(4).row();
        left.add(ownPanel).growX().fillY().height(72).padBottom(4).row();
        left.add(handStack).growX().fillY().height(108).padBottom(4).row();
        left.add(actionBar).growX().fillY().height(42).row();
    }

    private void buildRightColumn() {
        right.top().left();

        Table globalPanel = Ui.panel(assets, "panelGold");
        globalPanel.pad(12).top().left();
        globalPanel.add(Ui.label(skin, "MODIFICADOR GLOBAL", Typography.CAPTION)).left().row();
        globalPanel.add(globalName).left().padTop(2).row();
        globalPanel.add(globalDesc).growX().left().padTop(2).row();
        right.add(globalPanel).growX().height(100).padBottom(6).row();

        right.add(scorePanel).growX().height(230).padBottom(6).row();

        logPanel.clearChildren();
        logPanel.add(Ui.label(skin, "LOG DO PROTOCOLO", Typography.CAPTION)).left().row();
        logPanel.add(logScroll).grow().padTop(6).row();
        // O log ocupa o espaço que sobra na coluna: a altura é do layout, não do conteúdo.
        right.add(logPanel).grow().padBottom(42).row();
    }

    /**
     * Reorganiza cabeçalho e corpo conforme a largura disponível (sem coordenadas absolutas).
     * Largo: coluna principal + barra lateral. Estreito: cabeçalho em duas linhas e a barra
     * lateral abaixo do tabuleiro.
     */
    private void relayout(float width, float height, boolean force) {
        if (width <= 1f || height <= 1f) {
            return;
        }
        float availableWidth = width - 28f;
        boolean nextStacked = availableWidth < STACK_WIDTH;
        if (!force && Math.abs(width - layoutWidth) < 1f
                && Math.abs(height - layoutHeight) < 1f && stacked == nextStacked) {
            return;
        }
        layoutWidth = width;
        layoutHeight = height;
        stacked = nextStacked;

        metrics = LayoutMetrics.compute(width, height, nextStacked, 7f);
        hand.setCardMetrics(metrics.cards);
        powerHand.setCardMetrics(metrics.cards);
        sharedTable.setCardMetrics(metrics.cards);

        header.clearChildren();
        if (stacked) {
            header.add(titleBox).left().growX().row();
            header.add(headerPills).left().padTop(4);
        } else {
            header.add(titleBox).left().expandX();
            header.add(headerPills).right();
        }
        if (root.getCell(header) != null) {
            root.getCell(header).height(metrics.header.height());
        }

        body.clearChildren();
        body.setMinimumHeight(0f);
        if (stacked) {
            body.add(left).growX().row();
            if (showSidebar()) {
                body.add(right).growX().padTop(10).row();
            }
            actionBar.padRight(240);
            stageArea.pad(8);
        } else {
            body.add(left).grow().padRight(14f);
            body.add(right).width(SIDEBAR_WIDTH).growY();
            actionBar.padRight(0);
            stageArea.pad(16);
        }

        if (left.getCell(opponentPanel) != null) {
            left.getCell(opponentPanel).height(metrics.opponentBar.height());
        }
        if (left.getCell(ownPanel) != null) {
            left.getCell(ownPanel).height(metrics.ownBar.height());
        }
        if (left.getCell(actionBar) != null) {
            left.getCell(actionBar).height(metrics.actionBar.height());
        }
        if (left.getCell(stageArea) != null) {
            left.getCell(stageArea).height(metrics.table.height());
            left.getCell(stageArea).minHeight(metrics.table.height());
            left.getCell(stageArea).maxHeight(metrics.table.height());
            left.getCell(stageArea).expand(false, false);
        }
        if (left.getCell(handStack) != null) {
            left.getCell(handStack).height(handStack.isVisible() ? metrics.hand.height() : 0f);
        }
        body.invalidateHierarchy();
        left.invalidate();
        stageArea.invalidate();
    }

    private void adoptLandedCards(PlayerView me) {
        if (landingActors != null || submittedIndices.isEmpty()) {
            return;
        }
        if (!me.submitted() || !me.playedCards().equals(submittedCards)) {
            return;
        }
        List<CardActor> taken = hand.takeActors(submittedIndices);
        landingActors = new ArrayList<>(taken.size());
        for (CardActor actor : taken) {
            scratch.set(0f, 0f);
            actor.localToStageCoordinates(scratch);
            actor.remove();
            actor.setSelected(false);
            actor.setInteractive(false);
            stage.addActor(actor);
            actor.setPosition(scratch.x, scratch.y);
            landingActors.add(actor);
        }
        submittedIndices = List.of();
        submittedCards = List.of();
    }

    private float headerHeight() {
        return metrics == null ? (stacked ? 102f : 56f) : metrics.header.height();
    }

    private float handHeight() {
        return metrics == null ? (stacked ? 108f : 224f) : metrics.hand.height();
    }

    /** No modo empilhado a barra lateral é omitida: a mesa e a mão precisam da altura inteira. */
    private boolean showSidebar() {
        return !stacked;
    }

    // ------------------------------------------------------------------ quadro

    @Override
    public void render(float delta) {
        try {
            renderFrame(delta);
        } catch (RuntimeException e) {
            recoverPresentation(e);
        }
    }

    private void renderFrame(float delta) {
        boolean referenceOpen = isReferenceOpen();
        if (!referenceOpen) {
            session.tick(delta);
        }
        handleEvents();
        if (referenceOpen) {
            cardTooltip.hide();
        }
        if (!referenceOpen && (presenter.isCounting() || presenter.isBurning()
                || presenter.isResolving())
                && (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.justTouched())) {
            presenter.skip();
        }
        refresh(false);
        if (devPreview && snapshot != null && DevPreview.replayRequested()) {
            sharedTable.update(snapshot, myId, List.of(), true);
        }
        if (devPreview && snapshot != null && DevPreview.countReplayRequested()) {
            presenter.replayLastCount();
        }
        if (devPreview && snapshot != null && DevPreview.tooltipRequested()) {
            powerHand.demoHoverFirst(null);
        }
        if (devPreview && snapshot != null && DevPreview.burnReplayRequested()) {
            presenter.replayBurn();
        }
        if (devPreview && snapshot != null && DevPreview.resolutionReplayRequested()) {
            presenter.replayResolution();
        }
        if (devPreview && snapshot != null && DevPreview.redealRequested()) {
            if (snapshot.phase() == GamePhase.SABOTAGE) {
                if (session instanceof LocalMatchSession local) {
                    local.redealPowers();
                    refresh(true);
                } else {
                    powerHand.replayDeal();
                }
            }
        }
        if (!referenceOpen && gameOverDelay >= 0) {
            gameOverDelay -= delta;
            if (gameOverDelay <= 0) {
                goToGameOver();
            }
        }
        super.render(delta);
    }

    /**
     * Uma exceção de apresentação nunca pode encerrar o libGDX: registra, avisa e reconstrói a
     * tela a partir do último instantâneo autoritativo (o mesmo caminho de uma reconexão).
     */
    private void recoverPresentation(RuntimeException failure) {
        LOG.log(java.util.logging.Level.SEVERE, "Falha na apresentação da partida", failure);
        com.game.diagnostics.CrashLog.write(failure, "presentation");
        long now = System.currentTimeMillis();
        try {
            if (now - lastRecoveryAt > 3_000L) {
                lastRecoveryAt = now;
                showToast("ERRO INTERNO - log salvo", Palette.BAD);
            }
        } catch (RuntimeException ignored) {
            // A própria interface pode estar inconsistente; a reconstrução abaixo é o essencial.
        }
        try {
            GameSnapshot latest = session.snapshot();
            if (latest != null) {
                lastVersion = -1;
                selection.clear();
                renderedHand = List.of();
                renderedPowerHand = List.of();
                renderedPowerRound = -1;
                presenter.recover(latest);
            }
            refresh(true);
        } catch (RuntimeException e) {
            LOG.log(java.util.logging.Level.SEVERE,
                    "Falha ao reconstruir a tela a partir do instantâneo", e);
        }
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
                case ClientEvent.RoundResolved resolved -> {
                    // O resultado é aplicado na apresentação mesmo se o instantâneo que o contém
                    // ainda não chegou (as mensagens podem chegar em qualquer ordem).
                    presenter.onOutcome(resolved.payload().outcome());
                    showToast(resolved.payload().outcome().isTie()
                            ? "EMPATE \u2014 NINGU\u00c9M PERDE VIDA"
                            : "JOGADOR " + resolved.payload().outcome().loser()
                            + " PERDE " + resolved.payload().outcome().damage() + " HP",
                            resolved.payload().outcome().isTie() ? Palette.INK_DIM : Palette.BAD);
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ atualização

    private void refresh(boolean force) {
        if (refreshing) {
            refreshPending = true;
            return;
        }
        refreshing = true;
        try {
            refreshInternal(force);
        } finally {
            refreshing = false;
            if (refreshPending) {
                refreshPending = false;
                refresh(true);
            }
        }
    }

    private void refreshInternal(boolean force) {
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
        com.game.diagnostics.CrashLog.context(state.phase().name(), state.round(),
                state.version());

        if (state.phase() != layoutPhase) {
            layoutPhase = state.phase();
            if (stacked) {
                relayout(stage.getWidth(), stage.getHeight(), true);
            }
        }

        PlayerView me = state.player(myId);
        PlayerView opponent = state.opponentOf(myId);

        roundLabel.setText(state.phase() == GamePhase.GAME_OVER
                ? "FIM DE JOGO" : "RODADA " + state.round());
        globalName.setText(state.globalModifierName());
        globalDesc.setText(state.globalModifierDescription());
        turnLabel.setText(headerStatus(state));

        List<Card> ownHand = me.hand();
        adoptLandedCards(me);
        if (!ownHand.equals(renderedHand)) {
            renderedHand = ownHand;
            selection.clear();
        }
        if (!me.submitted()) {
            submittedLocally = false;
        }
        selection.removeIf(index -> index >= renderedHand.size());

        // A mesa (cartas/placares) precisa estar pronta antes de a contagem começar.
        // A resolução lê a mão de poderes antes de o palco ser redesenhado.
        if (state.phase() == GamePhase.REVEAL && !renderedPowerHand.isEmpty()
                && powerHand.cards().isEmpty()) {
            powerHand.setCards(renderedPowerHand, false);
        }
        rebuildStage(state, me, opponent);
        presenter.onSnapshot(state);

        boolean powersPhase = state.phase() == GamePhase.SABOTAGE;
        boolean revealPhase = state.phase() == GamePhase.REVEAL;
        boolean resolving = revealPhase && presenter.isResolving();
        boolean showPowerHand = (powersPhase && presenter.powerHandReady()) || resolving;

        ownPanel.update(me, state.isTurnOf(myId), "SUA VEZ",
                presenter.displayedHp(myId, me.hp()));
        opponentPanel.update(opponent, state.isTurnOf(opponent.id()), "VEZ DO OPONENTE",
                presenter.displayedHp(opponent.id(), opponent.hp()),
                presenter.powerHandReady());

        boolean interactive = canInteract(state);
        hand.setCards(renderedHand, interactive);
        hand.setSelectedIndices(selection);
        hand.setVisible(!showPowerHand && !revealPhase);
        boolean handMissing = renderedHand.isEmpty();
        handPlaceholder.setVisible(!showPowerHand && (revealPhase || handMissing));
        handPlaceholder.setText(revealPhase || state.phase() == GamePhase.REVEAL
                ? "SUA M\u00c3O VOLTA NA PR\u00d3XIMA RODADA"
                : "FIM DA PARTIDA");

        if (showPowerHand) {
            if (powersPhase) {
                if (state.round() != renderedPowerRound) {
                    renderedPowerRound = state.round();
                    powerHand.clearSelection();
                }
                List<PowerCard> powerCards = me.powerHand();
                if (!powerCards.equals(renderedPowerHand)) {
                    renderedPowerHand = powerCards;
                    powerHand.clearSelection();
                }
                powerHand.setMaxSelected(state.rules().maxPowersPlayed());
                powerHand.setCards(powerCards, canLockPowers(state, me));
            } else {
                // REVEAL: a mão de poderes permanece com as 6 cartas para os voos da resolução.
                powerHand.setInteractive(false);
            }
            powerHand.setVisible(true);
        } else {
            if (!revealPhase && !renderedPowerHand.isEmpty()) {
                renderedPowerHand = List.of();
                powerHand.setCards(List.of(), false);
                powerHand.clearSelection();
            }
            if (!revealPhase) {
                renderedPowerRound = -1;
            }
            powerHand.setVisible(false);
        }
        handStack.setVisible(showPowerHand || revealPhase || !handMissing);
        if (left.getCell(handStack) != null) {
            left.getCell(handStack).height(handStack.isVisible() ? handHeight() : 0f);
            left.invalidateHierarchy();
        }
        updatePreview();
        rebuildActions(state, me);
        refreshLog(state);

        if (state.phase() == GamePhase.GAME_OVER && gameOverDelay < 0) {
            gameOverDelay = game.settings().reducedMotion() ? 0.35f : 1.1f;
        }

        String networkStatus = session.statusMessage();
        if (networkStatus != null && !networkStatus.isBlank()) {
            statusLabel.setText(networkStatus);
        } else if (presenter.isBurning()) {
            statusLabel.setText("QUEIMANDO A M\u00c3O \u2014 CLIQUE OU ESPA\u00c7O PARA PULAR");
        } else if (presenter.isResolving()) {
            statusLabel.setText("RESOLVENDO A RODADA \u2014 CLIQUE OU ESPA\u00c7O PARA PULAR");
        } else if (powersPhase && !presenter.powerHandReady()) {
            statusLabel.setText("CONTAGEM DA MESA \u2014 CLIQUE OU ESPA\u00c7O PARA ACELERAR");
        } else if (powersPhase) {
            statusLabel.setText("PODERES \u2014 ORDEM: JOGADOR " + state.firstResolver()
                    + " PRIMEIRO");
        } else if (state.phase() == GamePhase.REVEAL && presenter.isFinalCounting()) {
            statusLabel.setText("CONTAGEM FINAL \u2014 CLIQUE OU ESPA\u00c7O PARA ACELERAR");
        } else if (state.phase() == GamePhase.REVEAL) {
            statusLabel.setText("REVELA\u00c7\u00c3O \u2014 COMPARE AS JOGADAS NA MESA");
        } else if (state.phase() == GamePhase.GAME_OVER) {
            statusLabel.setText("FIM DE JOGO");
        } else {
            statusLabel.setText("PROTOCOLO: " + state.globalModifierId()
                    + " \u00b7 VERS\u00c3O " + state.version());
        }
    }

    private void onPresentationChanged() {
        if (refreshing) {
            refreshPending = true;
            return;
        }
        refresh(true);
    }

    private void animateOutcomeDamage() {
        if (snapshot != null && snapshot.lastOutcome() != null) {
            animateCombat(snapshot.lastOutcome());
        }
    }

    private String headerStatus(GameSnapshot state) {
        if (state.phase() == GamePhase.GAME_OVER) {
            return "FIM DE JOGO";
        }
        if (state.phase() == GamePhase.REVEAL) {
            return "COMBATE REVELADO";
        }
        if (state.phase() == GamePhase.SABOTAGE) {
            return "ORDEM DA RODADA: JOGADOR " + state.firstResolver() + " PRIMEIRO";
        }
        if (state.isTurnOf(myId)) {
            return submittedLocally ? "JOGADA ENVIADA"
                    : "SUA VEZ \u2014 ESCOLHA " + state.rules().maxPlay() + " CARTAS";
        }
        return state.phase() == GamePhase.BUILD ? "VEZ DO OPONENTE" : "AGUARDANDO";
    }

    private boolean canInteract(GameSnapshot state) {
        if (state.phase() != GamePhase.BUILD || state.currentPlayer() != myId) {
            return false;
        }
        if (submittedLocally || state.player(myId).submitted()) {
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
        if (snapshot.phase() == GamePhase.REVEAL) {
            scorePanel.showMessage("RESOLU\u00c7\u00c3O",
                    presenter.isResolving()
                            ? "PODERES VOANDO PARA A MESA..."
                            : presenter.isFinalCounting()
                            ? "FICHAS \u00d7 MULT EST\u00c1 SUBINDO NA MESA..."
                            : "A PONTUA\u00c7\u00c3O FINAL APARECE NA MESA.");
            return;
        }
        if (snapshot.phase() == GamePhase.SABOTAGE) {
            scorePanel.showMessage("CONTAGEM DA MESA",
                    presenter.isBurning()
                            ? "A M\u00c3O EST\u00c1 QUEIMANDO..."
                            : presenter.powerHandReady()
                            ? "ESCOLHA SEUS PODERES NA M\u00c3O."
                            : "FICHAS E MULT EST\u00c3O SUBINDO NA MESA...");
            return;
        }
        if (snapshot.phase() == GamePhase.GAME_OVER) {
            scorePanel.showMessage("FIM DE JOGO", "O RESULTADO FINAL APARECE NA TELA SEGUINTE.");
            return;
        }
        ScoreResult preview = scoring.score(selectedCards(), List.of(),
                snapshot.globalModifierId());
        scorePanel.update(preview, selection.size() == snapshot.rules().maxPlay()
                && snapshot.phase() == GamePhase.BUILD, snapshot.rules().maxPlay());
    }

    private void submitPlay() {
        if (snapshot == null || !canInteract(snapshot)
                || selection.size() != snapshot.rules().maxPlay()) {
            return;
        }
        List<Card> cards = selectedCards();
        submittedIndices = new ArrayList<>(selection);
        submittedCards = cards;
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

    /** QA/DevPreview: percorre o mesmo caminho do botão JOGAR MÃO. */
    public void demoSubmit() {
        submitPlay();
    }

    // ------------------------------------------------------------------ palco

    private void rebuildStage(GameSnapshot state, PlayerView me, PlayerView opponent) {
        sharedTable.update(state, myId, landingActors == null ? List.of() : landingActors, false);
        landingActors = null;
    }

    private void confirmPowers() {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE
                || !presenter.powerHandReady()
                || !canLockPowers(snapshot, snapshot.player(myId))) {
            return;
        }
        List<Integer> indices = powerHand.sortedSelection();
        applyAction(() -> session.lockPowers(indices));
    }

    private void togglePower(int index) {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE
                || !canLockPowers(snapshot, snapshot.player(myId))) {
            return;
        }
        powerHand.toggle(index);
    }

    private void onPowerSelectionChanged() {
        if (snapshot != null) {
            rebuildActions(snapshot, snapshot.player(myId));
        }
    }

    /** Executa uma ação do jogador e mantém a tela sincronizada mesmo se o motor recusá-la. */
    private void applyAction(Runnable action) {
        try {
            action.run();
        } catch (GameRuleException refused) {
            showToast(refused.getMessage(), Palette.BAD);
        }
        refresh(true);
    }

    private boolean canLockPowers(GameSnapshot state, PlayerView me) {
        return state.phase() == GamePhase.SABOTAGE && !me.powersLocked() && isConnected();
    }

    private boolean isConnected() {
        return !(session instanceof NetworkMatchSession network) || network.client().isConnected();
    }

    // ------------------------------------------------------------------ ações

    private void rebuildActions(GameSnapshot state, PlayerView me) {
        int powerCount = powerHand.selection().size();
        String signature = state.phase() + ":" + state.currentPlayer() + ":" + me.ready()
                + ":" + submittedLocally + ":" + selection.size() + ":" + me.powersLocked()
                + ":" + powerCount + ":" + presenter.powerHandReady()
                + ":" + presenter.canAdvance()
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
                if (state.isTurnOf(myId) && !submittedLocally && !me.submitted()) {
                    boolean exactly = selection.size() == state.rules().maxPlay();
                    info.add(Ui.label(skin, exactly
                            ? "PRONTO PARA ENVIAR" : "SELECIONE EXATAMENTE "
                            + state.rules().maxPlay() + " CARTAS", "small")).left();
                    TextButton clear = Ui.button(skin, "LIMPAR", "ghost", this::clearSelection);
                    clear.setDisabled(selection.isEmpty());
                    TextButton submit = Ui.button(skin, "JOGAR MÃO", "gold", this::submitPlay);
                    submit.setDisabled(!exactly);
                    buttons.add(clear).width(130).height(36).padRight(10);
                    buttons.add(submit).width(210).height(36);
                } else {
                    Label waiting = Ui.label(skin, submittedLocally
                            ? "AGUARDANDO O OPONENTE..." : "AGUARDANDO SUA VEZ...", "body-dim");
                    info.add(waiting).left();
                }
            }
            case SABOTAGE -> {
                int max = state.rules().maxPowersPlayed();
                if (presenter.isBurning()) {
                    info.add(Ui.label(skin,
                            "QUEIMANDO A M\u00c3O... CLIQUE OU ESPA\u00c7O PARA PULAR",
                            "small")).left();
                } else if (!presenter.powerHandReady()) {
                    info.add(Ui.label(skin,
                            "CONTAGEM DA MESA... CLIQUE OU ESPA\u00c7O PARA ACELERAR",
                            "small")).left();
                } else if (me.powersLocked()) {
                    info.add(Ui.label(skin, "PODERES CONFIRMADOS. AGUARDANDO O OPONENTE...",
                            "small")).left();
                } else {
                    info.add(Ui.label(skin, "CLIQUE AT\u00c9 " + max
                            + " CARTAS DE PODER NA SUA M\u00c3O", "small")).left();
                    TextButton confirm = Ui.button(skin,
                            "CONFIRMAR PODERES (" + powerHand.selection().size() + "/" + max + ")",
                            "gold", this::confirmPowers);
                    buttons.add(confirm).width(290).height(36);
                }
            }
            case REVEAL -> {
                if (presenter.isResolving()) {
                    info.add(Ui.label(skin,
                            "RESOLVENDO OS PODERES... CLIQUE OU ESPA\u00c7O PARA PULAR",
                            "small")).left();
                } else if (!presenter.canAdvance()) {
                    info.add(Ui.label(skin,
                            "CONTAGEM FINAL... CLIQUE OU ESPA\u00c7O PARA ACELERAR",
                            "small")).left();
                } else {
                    info.add(Ui.label(skin, me.ready()
                            ? "VOC\u00ca J\u00c1 CONFIRMOU. AGUARDANDO O OPONENTE..."
                            : "SUA M\u00c3O VOLTA NA PR\u00d3XIMA RODADA \u00b7 CONFIRME PARA CONTINUAR",
                            "small")).left();
                    TextButton advance = Ui.button(skin,
                            me.ready() ? "AGUARDANDO..." : "CONTINUAR", "gold",
                            this::advance);
                    advance.setDisabled(me.ready());
                    buttons.add(advance).width(210).height(36);
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
        if (snapshot == null || snapshot.phase() != GamePhase.REVEAL
                || !presenter.canAdvance()
                || snapshot.player(myId).ready()) {
            return;
        }
        applyAction(session::advance);
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

    /** Tremor da contagem final: três faixas de intensidade definidas em {@link ScoreFeel}. */
    public void shakeScreen(int level) {
        if (level <= 0 || game.settings().reducedMotion()) {
            return;
        }
        int tier = Math.min(level, ScoreFeel.SHAKE_AMPLITUDE.length - 1);
        float amplitude = ScoreFeel.SHAKE_AMPLITUDE[tier];
        float step = ScoreFeel.SHAKE_STEP_SECONDS[tier];
        if (amplitude <= 0f || step <= 0f) {
            return;
        }
        stageArea.clearActions();
        stageArea.addAction(Actions.sequence(
                Actions.moveBy(-amplitude, 0f, step),
                Actions.moveBy(amplitude * 2f, 0f, step),
                Actions.moveBy(-amplitude * 1.5f, 0f, step),
                Actions.moveBy(amplitude * 0.8f, 0f, step),
                Actions.moveBy(-amplitude * 0.3f, 0f, step)));
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
                case "system" -> "log-system";
                default -> "log";
            };
            Label line = Ui.label(skin, breakable(readableLog(entry)), style);
            line.setWrap(true);
            line.setAlignment(Align.left);
            logContent.add(line).left().growX().padBottom(2).row();
        }
        logContent.pack();
        logScroll.layout();
        logScroll.setScrollPercentY(1f);
    }

    /** Traduz os códigos do log do motor para linhas legíveis na interface. */
    private static String readableLog(LogEntry entry) {
        String[] parts = entry.text().split("\\|");
        return switch (parts[0]) {
            case "GLOBAL" -> "MODIFICADOR GLOBAL: " + globalName(parts[1]);
            case "ROUND" -> "RODADA " + parts[1] + " COME\u00c7A \u00b7 JOGADOR "
                    + value(parts[2]) + " RESOLVE PRIMEIRO";
            case "SUBMIT" -> "JOGADOR " + parts[1] + " ENVIOU: " + cards(parts[2]);
            case "LOCK" -> "JOGADOR " + parts[1] + " TRAVOU OS PODERES";
            case "ORDEM" -> "ORDEM DA RODADA " + parts[1] + ": JOGADOR " + parts[2]
                    + " RESOLVE PRIMEIRO";
            case "JOKERS" -> "JOGADOR " + parts[1] + " JOGOU JOKERS: " + jokerNames(parts[2]);
            case "SABOTAGE" -> "JOGADOR " + parts[1] + " USOU " + Sabotages.nameOf(parts[2])
                    + " CONTRA O JOGADOR " + opponent(parts[1]);
            case "NIAPES" -> "VIRADA DE NAIPES REESCREVEU A JOGADA DO JOGADOR " + parts[1]
                    + ": " + replaceArrow(parts[2]);
            case "TROCA_CARTA" -> "TROCA DE CARTA REESCREVEU A JOGADA DO JOGADOR " + parts[1]
                    + ": " + replaceArrow(parts[2]);
            case "RESULT" -> result(parts);
            case "HP" -> "VIDA FINAL: J1 " + value(parts[1]) + " \u00b7 J2 " + value(parts[2]);
            case "FIM" -> "FIM DE JOGO: JOGADOR " + value(parts[1]) + " VENCEU";
            default -> entry.text();
        };
    }

    private static String globalName(String id) {
        var def = GlobalModifiers.byId(id);
        return def == null ? id : def.name();
    }

    private static String result(String[] parts) {
        String winner = value(parts[1]);
        String outcome = "empate".equals(winner) ? "EMPATE" : "JOGADOR " + winner + " VENCEU";
        return "RESULTADO: " + outcome + " \u00b7 P1 " + value(parts[2]) + " \u00d7 P2 "
                + value(parts[3]) + " \u00b7 DANO " + value(parts[4]);
    }

    private static String value(String token) {
        int equals = token.indexOf('=');
        return equals < 0 ? token : token.substring(equals + 1);
    }

    private static String opponent(String player) {
        return "1".equals(player) ? "2" : "1";
    }

    private static String jokerNames(String csv) {
        StringBuilder text = new StringBuilder();
        for (String id : csv.split(",")) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(Jokers.nameOf(id.trim()));
        }
        return text.toString();
    }

    private static String cards(String csv) {
        return csv.replace(",", " \u00b7 ");
    }

    private static String replaceArrow(String value) {
        return value.replace("->", " -> ");
    }

    /**
     * O protocolo separa campos com "|", "," e ":" sem espaços; sem estas quebras o Label não
     * consegue quebrar a linha e o texto vazaria para fora do painel lateral.
     */
    private static String breakable(String text) {
        return text.replace("|", "| ").replace(",", ", ").replace(":", ": ");
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

    // ------------------------------------------------------------------ QA

    /** QA: métricas de layout em vigor (regiões e tamanhos de carta). */
    public LayoutMetrics probeMetrics() {
        return metrics;
    }

    /** QA: a mesa compartilhada (duas fileiras + placares). */
    public SharedTablePanel probeTable() {
        return sharedTable;
    }

    /** QA: mão de cartas do jogador. */
    public CardHand probeHand() {
        return hand;
    }

    /** QA: mão de poderes do jogador. */
    public PowerHand probePowerHand() {
        return powerHand;
    }

    /** QA: barra de ações (botão principal + LIMPAR). */
    public Table probeActionBar() {
        return actionBar;
    }

    /** QA: último instantâneo renderizado. */
    public GameSnapshot probeSnapshot() {
        return snapshot;
    }

    /** QA: id do jogador desta tela. */
    public int probeMyId() {
        return myId;
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

    /** QA: seleciona as primeiras cartas de poder pelo mesmo caminho do clique. */
    public void demoSelectPowers(int count) {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE) {
            return;
        }
        powerHand.clearSelection();
        for (int i = 0; i < count; i++) {
            powerHand.toggle(i);
        }
        onPowerSelectionChanged();
    }

    /** QA: com o limite já atingido, clica na próxima carta para exercitar a recusa. */
    public void demoRefusedPowerClick() {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE) {
            return;
        }
        int next = powerHand.selection().size();
        powerHand.toggle(next);
    }

    /** QA: injeta hover determinístico na primeira carta de poder do tipo pedido. */
    public void demoHoverPower(PowerKind kind) {
        powerHand.demoHoverFirst(kind);
    }

    /** QA: injeta hover determinístico em um poder jogado nos slots da mesa. */
    public void demoHoverPlayedPower(boolean mine, int index) {
        sharedTable.demoHoverPower(mine, index);
    }

    /** QA: repete a queima da mão no último instantâneo. */
    public void demoReplayBurn() {
        presenter.replayBurn();
    }

    /** QA: repete a resolução inteira no último resultado. */
    public void demoReplayResolution() {
        presenter.replayResolution();
    }

    /** QA: confirma os poderes já selecionados. */
    public void demoConfirmPowers() {
        if (snapshot != null && snapshot.phase() == GamePhase.SABOTAGE
                && presenter.powerHandReady()) {
            confirmPowers();
        }
    }

    /** QA: confirma a revelação pelo mesmo caminho do botão CONTINUAR. */
    public void demoAdvance() {
        advance();
    }

    /** QA: conclui resolução e contagem final para capturar o estado final. */
    public void demoFinishPresentation() {
        if (presenter.isResolving() || presenter.isCounting() || presenter.isBurning()
                || presenter.isFinalCounting()) {
            presenter.skip();
        }
    }

    /** QA: conclui só a resolução; a contagem final continua rodando. */
    public void demoSkipResolution() {
        if (presenter.isResolving()) {
            resolutionEffect.finishNow();
        }
    }

    /** QA: conclui a apresentação corrente e força um refresh para um estado estável. */
    public void probeSettle() {
        if (presenter.isResolving() || presenter.isCounting() || presenter.isBurning()
                || presenter.isFinalCounting()) {
            presenter.skip();
        }
        refresh(true);
    }

    /** QA: verdadeiro durante a apresentação da resolução. */
    public boolean demoIsResolving() {
        return presenter.isResolving();
    }

    /** QA: verdadeiro quando a contagem e a primeira queima já entregaram a mão de poderes. */
    public boolean demoTransitionDone() {
        return presenter.powerHandReady();
    }

    public void demoUnhoverPowers() {
        powerHand.demoUnhover();
        sharedTable.demoUnhoverPowers();
        cardTooltip.hide();
    }

    @Override
    protected void onResize(float width, float height) {
        relayout(width, height, false);
    }

    @Override
    protected void onEscape() {
        leaveToMenu();
    }

    /** Tabela do corpo que ocupa ao menos a altura do viewport quando há espaço sobrando. */
    private static final class FillTable extends Table {
        private float minimumHeight;

        FillTable() {
            top().left();
        }

        void setMinimumHeight(float value) {
            minimumHeight = Math.max(0f, value);
        }

        @Override
        public float getPrefHeight() {
            return Math.max(minimumHeight, super.getPrefHeight());
        }
    }

    @Override
    public void dispose() {
        burnEffect.dispose();
        resolutionEffect.dispose();
        if (!keepSessionAlive) {
            session.close();
            if (cleanup != null) {
                cleanup.run();
            }
        }
        super.dispose();
    }
}
