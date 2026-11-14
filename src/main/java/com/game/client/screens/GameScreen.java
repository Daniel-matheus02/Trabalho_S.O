package com.game.client.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.client.controller.MatchSession;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.network.ClientEvent;
import com.game.core.model.Card;
import com.game.core.rules.GameRuleException;
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
import com.game.rendering.FixedHeightScrollPane;
import com.game.rendering.Palette;
import com.game.rendering.PlayerPanel;
import com.game.rendering.SabotagePanel;
import com.game.rendering.ScorePanel;
import com.game.rendering.ShopPanel;
import com.game.rendering.Typography;
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
    private final FixedHeightScrollPane logScroll;
    private final Label roundLabel;
    private final Label turnLabel;
    private final Label globalName;
    private final Label globalDesc;
    private final Label statusLabel;
    private final ShopPanel shopPanel;
    private final SabotagePanel sabotagePanel;
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
        this.shopPanel = new ShopPanel(assets);
        this.sabotagePanel = new SabotagePanel(assets);
        this.revealPanel = new CombatRevealPanel(assets);
        boolean reducedMotion = game.settings().reducedMotion();
        this.revealPanel.setAnimateDeals(!reducedMotion);
        this.ownPanel.setEffectsEnabled(!reducedMotion);
        this.opponentPanel.setEffectsEnabled(!reducedMotion);

        build();
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
        left.add(opponentPanel).growX().height(92).padBottom(6).row();
        left.add(stageArea).growX().minHeight(196).expandY().padBottom(6).row();
        left.add(ownPanel).growX().height(72).padBottom(4).row();
        Stack handStack = new Stack(hand, handPlaceholder);
        left.add(handStack).growX().height(108).padBottom(2).row();
        left.add(actionBar).growX().height(40).row();
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

        header.clearChildren();
        if (stacked) {
            header.add(titleBox).left().growX().row();
            header.add(headerPills).left().padTop(4);
            if (root.getCell(header) != null) {
                root.getCell(header).height(102);
            }
        } else {
            header.add(titleBox).left().expandX();
            header.add(headerPills).right();
            if (root.getCell(header) != null) {
                root.getCell(header).height(56);
            }
        }

        body.clearChildren();
        if (stacked) {
            body.setMinimumHeight(0f);
            body.add(left).growX().row();
            body.add(right).growX().padTop(10).row();
        } else {
            // Altura mínima do corpo = janela - padding do root - cabeçalho - barra de status.
            body.setMinimumHeight(Math.max(0f, height - headerHeight() - 62f));
            // 320 px é a largura mínima que a coluna lateral (ScorePanel) exige; manter a barra
            // lateral nessa largura em janelas estreitas evita recorte horizontal.
            body.add(left).grow().padRight(14f);
            body.add(right).width(SIDEBAR_WIDTH).growY();
        }
        body.invalidateHierarchy();
    }

    private float headerHeight() {
        return stacked ? 102f : 56f;
    }

    // ------------------------------------------------------------------ quadro

    @Override
    public void render(float delta) {
        boolean referenceOpen = isReferenceOpen();
        if (!referenceOpen) {
            session.tick(delta);
        }
        handleEvents();
        refresh(false);
        if (!referenceOpen && gameOverDelay >= 0) {
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
        } else if (state.phase() == GamePhase.SABOTAGE) {
            statusLabel.setText("SABOTAGEM \u2014 ORDEM: JOGADOR " + state.firstResolver()
                    + " PRIMEIRO");
        } else if (state.phase() == GamePhase.SHOP) {
            statusLabel.setText("LOJA \u2014 ESCOLHA 1 JOKER E GASTE SUAS MOEDAS");
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
        if (state.phase() == GamePhase.SABOTAGE) {
            return "ORDEM DA RODADA: JOGADOR " + state.firstResolver() + " PRIMEIRO";
        }
        if (state.phase() == GamePhase.SHOP) {
            return "LOJA DA RODADA";
        }
        if (state.isTurnOf(myId)) {
            return submittedLocally ? "JOGADA ENVIADA" : "SUA VEZ \u2014 ESCOLHA 2 CARTAS";
        }
        return state.phase() == GamePhase.BUILD ? "VEZ DO OPONENTE" : "AGUARDANDO";
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
        if (snapshot.phase() == GamePhase.SABOTAGE || snapshot.phase() == GamePhase.SHOP) {
            scorePanel.showMessage("MESA ABERTA",
                    "AS 5 CARTAS COMUNITÁRIAS JÁ ESTÃO NA MESA. A PONTUAÇÃO FINAL É CALCULADA "
                            + "DEPOIS DAS SABOTAGENS.");
            return;
        }
        if (snapshot.phase() == GamePhase.GAME_OVER) {
            scorePanel.showMessage("FIM DE JOGO", "O RESULTADO FINAL APARECE NA TELA SEGUINTE.");
            return;
        }
        ScoreResult preview = scoring.score(combineWithCommunity(selectedCards()), me.jokers(),
                snapshot.globalModifierId());
        scorePanel.update(preview, selection.size() == snapshot.rules().maxPlay()
                && snapshot.phase() == GamePhase.BUILD);
    }

    private List<Card> combineWithCommunity(List<Card> cards) {
        List<Card> pool = new ArrayList<>(cards);
        if (snapshot != null) {
            pool.addAll(snapshot.community());
        }
        return pool;
    }

    private void submitPlay() {
        if (snapshot == null || !canInteract(snapshot)
                || selection.size() != snapshot.rules().maxPlay()) {
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
                + ":" + me.offers() + ":" + me.sabotageOffers() + ":" + me.sabotageHand()
                + ":" + me.sabotageChosen() + ":" + me.jokerPicked() + ":" + me.shopReady()
                + ":" + me.coins() + ":" + state.firstResolver() + ":" + state.community()
                + ":" + (state.lastOutcome() == null ? 0 : state.lastOutcome().round())
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
            case SABOTAGE -> {
                sabotagePanel.update(state, me, canSabotage(state, me),
                        this::useSabotage, this::passSabotage);
                stageArea.add(sabotagePanel).grow().row();
            }
            case SHOP -> {
                boolean canAct = canShop(state);
                shopPanel.update(state, me, canAct, this::pickJoker, this::buySabotage);
                stageArea.add(shopPanel).grow().row();
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
            stageArea.add(Ui.label(skin, "SUA VEZ \u2014 ESCOLHA 2 CARTAS", whoStyle))
                    .left().padBottom(6).row();
            stageArea.add(Ui.wrapLabel(skin,
                    "VOCÊ JOGA 2 CARTAS. A MESA ABRE 5 (FLOP · TURN · RIVER) E A MELHOR MÃO DE 5 "
                            + "ENTRE SUAS 2 + AS 5 DA MESA PONTUA.",
                    "body-dim")).growX().left().row();
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
        if (snapshot == null || snapshot.phase() != GamePhase.SHOP || !canShop(snapshot)
                || snapshot.player(myId).jokerPicked()) {
            return;
        }
        applyAction(() -> session.pickJoker(index));
    }

    private void buySabotage(int index) {
        if (snapshot == null || snapshot.phase() != GamePhase.SHOP || !canShop(snapshot)) {
            return;
        }
        PlayerView me = snapshot.player(myId);
        if (me.sabotageCount() >= snapshot.rules().maxSabotages()
                || me.coins() < snapshot.rules().sabotagePrice()) {
            return;
        }
        applyAction(() -> session.buySabotage(index));
    }

    private void useSabotage(int index) {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE
                || !canSabotage(snapshot, snapshot.player(myId))) {
            return;
        }
        applyAction(() -> session.useSabotage(index));
    }

    private void passSabotage() {
        if (snapshot == null || snapshot.phase() != GamePhase.SABOTAGE
                || !canSabotage(snapshot, snapshot.player(myId))) {
            return;
        }
        applyAction(session::passSabotage);
    }

    private void ready() {
        if (snapshot == null || snapshot.phase() != GamePhase.SHOP || !canShop(snapshot)
                || !snapshot.player(myId).jokerPicked()
                || snapshot.player(myId).shopReady()) {
            return;
        }
        applyAction(session::ready);
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

    private boolean canShop(GameSnapshot state) {
        return state.phase() == GamePhase.SHOP && isConnected();
    }

    private boolean canSabotage(GameSnapshot state, PlayerView me) {
        return state.phase() == GamePhase.SABOTAGE && !me.sabotageChosen() && isConnected();
    }

    private boolean isConnected() {
        return !(session instanceof NetworkMatchSession network) || network.client().isConnected();
    }

    // ------------------------------------------------------------------ ações

    private void rebuildActions(GameSnapshot state, PlayerView me) {
        String signature = state.phase() + ":" + state.currentPlayer() + ":" + me.ready()
                + ":" + submittedLocally + ":" + selection.size() + ":" + me.rerolls()
                + ":" + me.nextRerollCost() + ":" + me.coins() + ":" + me.jokerPicked()
                + ":" + me.shopReady() + ":" + me.sabotageChosen()
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
                    boolean exactly = selection.size() == state.rules().maxPlay();
                    info.add(Ui.label(skin, exactly
                            ? "PRONTO PARA ENVIAR" : "SELECIONE EXATAMENTE "
                            + state.rules().maxPlay() + " CARTAS", "small")).left();
                    TextButton clear = Ui.button(skin, "LIMPAR", "ghost", this::clearSelection);
                    clear.setDisabled(selection.isEmpty());
                    TextButton submit = Ui.button(skin, "ENVIAR JOGADA", "gold", this::submitPlay);
                    submit.setDisabled(!exactly);
                    buttons.add(clear).width(130).height(44).padRight(10);
                    buttons.add(submit).width(210).height(44);
                } else {
                    Label waiting = Ui.label(skin, submittedLocally
                            ? "AGUARDANDO O OPONENTE..." : "AGUARDANDO SUA VEZ...", "body-dim");
                    info.add(waiting).left();
                }
            }
            case SABOTAGE -> {
                info.add(Ui.label(skin, me.sabotageChosen()
                        ? "ESCOLHA REGISTRADA. AGUARDANDO O OPONENTE..."
                        : "USE UMA SABOTAGEM OU PASSE", "small")).left();
                TextButton pass = Ui.button(skin, me.sabotageChosen() ? "AGUARDANDO..."
                        : "PASSAR", "ghost", this::passSabotage);
                pass.setDisabled(me.sabotageChosen() || !canSabotage(state, me));
                buttons.add(pass).width(180).height(44);
            }
            case REVEAL -> {
                info.add(Ui.label(skin, me.ready()
                        ? "VOC\u00ca J\u00c1 CONFIRMOU. AGUARDANDO O OPONENTE..."
                        : "CONFIRME PARA IR À LOJA", "small")).left();
                TextButton advance = Ui.button(skin, me.ready() ? "AGUARDANDO..." : "CONTINUAR", "gold",
                        this::advance);
                advance.setDisabled(me.ready());
                buttons.add(advance).width(210).height(44);
            }
            case SHOP -> {
                boolean canAct = canShop(state);
                String rerollLabel;
                boolean canReroll;
                if (me.rerolls() > 0) {
                    rerollLabel = "ROLAR JOKERS (GRÁTIS x" + me.rerolls() + ")";
                    canReroll = canAct && !me.jokerPicked();
                } else {
                    rerollLabel = "ROLAR JOKERS (" + me.nextRerollCost() + " MOEDAS)";
                    canReroll = canAct && !me.jokerPicked() && me.coins() >= me.nextRerollCost();
                }
                TextButton reroll = Ui.button(skin, rerollLabel, "green", this::reroll);
                reroll.setDisabled(!canReroll);

                info.add(Ui.label(skin, me.jokerPicked()
                        ? "JOKER ESCOLHIDO. COMPRE SABOTAGENS OU CONFIRME."
                        : "ESCOLHA 1 JOKER NA LOJA", "small")).left();
                TextButton confirm = Ui.button(skin, me.shopReady() ? "AGUARDANDO..."
                        : "PRONTO", "gold", this::ready);
                confirm.setDisabled(!canAct || !me.jokerPicked() || me.shopReady());
                buttons.add(reroll).width(360).height(44).padRight(10);
                buttons.add(confirm).width(150).height(44);
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
                || snapshot.player(myId).ready()) {
            return;
        }
        applyAction(session::advance);
    }

    private void reroll() {
        if (snapshot == null || snapshot.phase() != GamePhase.SHOP || !canShop(snapshot)
                || snapshot.player(myId).jokerPicked()) {
            return;
        }
        applyAction(session::reroll);
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
            Label line = Ui.label(skin, breakable(entry.text()), style);
            line.setWrap(true);
            line.setAlignment(Align.left);
            logContent.add(line).left().growX().padBottom(2).row();
        }
        logContent.pack();
        logScroll.layout();
        logScroll.setScrollPercentY(1f);
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
        if (!keepSessionAlive) {
            session.close();
            if (cleanup != null) {
                cleanup.run();
            }
        }
        super.dispose();
    }
}
