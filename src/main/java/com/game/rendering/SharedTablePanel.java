package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.client.layout.LayoutMetrics;
import com.game.client.layout.LayoutMetrics.CardMetrics;
import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * A mesa compartilhada da rodada: cinco espaços por lado (oponente em cima, o jogador embaixo),
 * as cartas jogadas e a pontuação de mesa de cada mão. É persistente durante toda a rodada.
 *
 * <p>As cartas do jogador são movidas da mão para os espaços (o {@code GameScreen} entrega os
 * próprios {@link CardActor} já existentes). As cartas do oponente chegam viradas para baixo e
 * viram uma a uma quando o instantâneo passa a revelá-las. O tamanho das cartas vem do espaço
 * disponível, para caber de 1093x614 a 1920x1080.</p>
 */
public final class SharedTablePanel extends WidgetGroup {

    private static final int SLOTS = 5;
    private static final int POWER_SLOTS = 2;
    private static final float LAND_DURATION = 0.35f;
    private static final float DROP_DURATION = 0.32f;
    private static final float FLIP_DURATION = 0.2f;
    private static final float FLIP_STAGGER = 0.08f;
    private static final float LAND_STAGGER = 0.04f;
    /** 5 cartas + 2 poderes + os espaçamentos entre elas, em larguras de carta. */
    private static final float TABLE_ROW_FACTOR = 8.672f;

    private final GameAssets assets;
    private final Skin skin;
    private final Color tmp = new Color();
    private final Vector2 scratch = new Vector2();

    private final Side opponent;
    private final Side own;
    private final Label vsLabel;
    private final ScoreTally opponentTally;
    private final ScoreTally ownTally;

    private boolean animate = true;
    private CardTooltip tooltip;
    private String signature = "";
    private GamePhase lastPhase;
    private List<Card> shownOwn = List.of();
    private List<Card> shownOpponent = List.of();
    private boolean shownOpponentSubmitted;
    private List<CardActor> landing = List.of();
    private CardMetrics cardMetrics = LayoutMetrics.cardMetrics(1365f, 695f);
    private float laidOutCardWidth;
    private float laidOutCardHeight;

    public SharedTablePanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        setTransform(false);
        opponent = new Side(false);
        own = new Side(true);
        vsLabel = Ui.label(skin, "VS", "h2");
        vsLabel.setAlignment(Align.center);
        // Rótulo decorativo: nunca intercepta o clique das cartas.
        vsLabel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        opponentTally = new ScoreTally(assets, "p2");
        ownTally = new ScoreTally(assets, "p1");
        addActor(opponent);
        addActor(own);
        addActor(opponentTally);
        addActor(ownTally);
        addActor(vsLabel);
        opponentTally.right();
        ownTally.right();
    }

    /** Liga ou desliga as animações de entrada e de virada (movimento reduzido). */
    public void setAnimate(boolean animate) {
        this.animate = animate;
    }

    /** Tooltip global das cartas de poder jogadas nos slots. */
    public void setTooltip(CardTooltip tooltip) {
        this.tooltip = tooltip;
        for (int i = 0; i < POWER_SLOTS; i++) {
            PowerCardActor ownActor = own.powerSlot(i).power();
            if (ownActor != null) {
                ownActor.setTooltip(tooltip);
            }
            PowerCardActor opponentActor = opponent.powerSlot(i).power();
            if (opponentActor != null) {
                opponentActor.setTooltip(tooltip);
            }
        }
    }

    /** QA: injeta hover determinístico em um poder jogado. */
    public boolean demoHoverPower(boolean mine, int index) {
        PowerCardActor actor = powerSlotActor(mine, index);
        if (actor == null) {
            return false;
        }
        actor.demoHover(actor.getWidth() * 0.72f, actor.getHeight() / 2f);
        return true;
    }

    public void demoUnhoverPowers() {
        for (boolean mine : new boolean[]{true, false}) {
            for (int i = 0; i < POWER_SLOTS; i++) {
                PowerCardActor actor = powerSlotActor(mine, i);
                if (actor != null) {
                    actor.demoUnhover();
                }
            }
        }
    }

    /** Carta posicionada no espaço, ou {@code null} se o espaço está vazio. */
    public CardActor slotActor(boolean mine, int index) {
        return (mine ? own : opponent).slot(index).card();
    }

    /** Carta de poder posicionada no espaço da revelação, ou {@code null}. */
    public PowerCardActor powerSlotActor(boolean mine, int index) {
        Slot slot = (mine ? own : opponent).powerSlot(index);
        return slot == null ? null : slot.power();
    }

    /** Procura o ator de poder pelo id do catálogo (JOKER/SABOTAGE) no lado pedido. */
    public PowerCardActor powerActorFor(boolean mine, String sourceId) {
        Side side = mine ? own : opponent;
        for (int i = 0; i < POWER_SLOTS; i++) {
            PowerCardActor actor = side.powerSlot(i).power();
            if (actor != null && actor.power() != null
                    && actor.power().id().equals(sourceId)) {
                return actor;
            }
        }
        return null;
    }

    /** Centro do slot de poder em coordenadas de palco (para os voos da resolução). */
    public Vector2 powerSlotCenter(boolean mine, int index) {
        PowerCardActor actor = powerSlotActor(mine, index);
        if (actor == null) {
            return new Vector2(getWidth() / 2f, getHeight() / 2f);
        }
        Vector2 position = new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f);
        actor.localToStageCoordinates(position);
        return position;
    }

    /** Esconde os poderes jogados até o voo de resolução correspondente. */
    public void hidePowerSlotActors() {
        for (boolean mine : new boolean[]{true, false}) {
            for (int i = 0; i < POWER_SLOTS; i++) {
                PowerCardActor actor = powerSlotActor(mine, i);
                if (actor != null) {
                    actor.setVisible(false);
                }
            }
        }
    }

    public void revealPowerSlotActor(boolean mine, int index) {
        PowerCardActor actor = powerSlotActor(mine, index);
        if (actor != null) {
            actor.setVisible(true);
            actor.setScale(1f, 1f);
            actor.getColor().a = 1f;
        }
    }

    public void revealAllPowerSlots() {
        for (boolean mine : new boolean[]{true, false}) {
            for (int i = 0; i < POWER_SLOTS; i++) {
                revealPowerSlotActor(mine, i);
            }
        }
    }

    /** Estado inicial da mesa antes das sabotagens de mesa da resolução. */
    public void setResolutionCards(boolean mine, List<Card> cards) {
        (mine ? own : opponent).setCardsForResolution(cards);
    }

    /** Troca a face de uma carta com o flip scaleX 1 -> 0 -> 1. */
    public void flipResolutionCard(boolean mine, int index, Card newCard) {
        Side side = mine ? own : opponent;
        Slot slot = side.slot(index);
        if (slot == null) {
            return;
        }
        CardActor current = slot.card();
        if (current == null) {
            side.replaceSlotCard(index, newCard);
            return;
        }
        current.setOrigin(Align.center);
        current.addAction(Actions.scaleTo(0f, 1f, 0.12f, Interpolation.sine));
        addAction(Actions.sequence(Actions.delay(0.12f), Actions.run(() -> {
            side.replaceSlotCard(index, newCard);
            CardActor fresh = slot.card();
            if (fresh != null) {
                fresh.setOrigin(Align.center);
                fresh.setScale(0f, 1f);
                fresh.addAction(Actions.scaleTo(1f, 1f, 0.12f, Interpolation.sine));
            }
        })));
    }

    /** Widget da pontuação de mesa do lado pedido. */
    public ScoreTally tallyView(boolean mine) {
        return mine ? ownTally : opponentTally;
    }

    public void update(GameSnapshot state, int myId) {
        update(state, myId, List.of(), false);
    }

    /**
     * Atualiza a mesa. {@code landingActors} são as cartas já existentes que saíram da mão do
     * jogador e devem ser adotadas nos espaços; {@code replay} recria as cartas e repete as
     * animações sobre o mesmo instantâneo (DevPreview).
     */
    public void update(GameSnapshot state, int myId, List<CardActor> landingActors, boolean replay) {
        PlayerView ownView = state.player(myId);
        PlayerView opponentView = state.opponentOf(myId);
        RoundOutcome outcome = state.lastOutcome() != null
                && state.lastOutcome().round() == state.round() ? state.lastOutcome() : null;

        String next = state.round() + ":" + state.phase() + ":" + ownView.playedCards() + ":"
                + opponentView.playedCards() + ":" + ownView.submitted() + ":"
                + opponentView.submitted() + ":" + ownView.tableScore() + ":"
                + opponentView.tableScore() + ":" + ownView.playedPowers() + ":"
                + opponentView.playedPowers() + ":" + summary(outcome);
        boolean changed = !next.equals(signature);
        if (!changed && !replay && landingActors.isEmpty()) {
            return;
        }
        if (replay) {
            shownOwn = List.of();
            shownOpponent = List.of();
            shownOpponentSubmitted = false;
        }
        signature = next;
        landing = landingActors;

        boolean ownCardsChanged = !ownView.playedCards().equals(shownOwn);
        boolean opponentCardsChanged = !opponentView.playedCards().equals(shownOpponent);
        boolean opponentJustSubmitted = opponentView.submitted() && !shownOpponentSubmitted;

        own.apply(ownView, ownCardsChanged, animate, false);
        if (state.phase() == GamePhase.BUILD) {
            if (!landing.isEmpty()) {
                own.adoptLanding(animate);
            } else {
                own.clearCards();
            }
            opponent.clearCards();
            opponent.showBacks();
        } else if (state.phase() != GamePhase.REVEAL
                && state.phase() != GamePhase.GAME_OVER) {
            opponent.showBacks();
        } else {
            opponent.apply(opponentView, opponentCardsChanged, animate,
                    opponentJustSubmitted || shownOpponent.isEmpty());
        }
        boolean powersVisible = state.phase() == GamePhase.REVEAL
                || state.phase() == GamePhase.GAME_OVER;
        own.applyPowers(ownView.playedPowers(), powersVisible);
        opponent.applyPowers(opponentView.playedPowers(), powersVisible);
        shownOwn = ownView.playedCards();
        shownOpponent = opponentView.playedCards();
        shownOpponentSubmitted = opponentView.submitted();
        landing = List.of();
        lastPhase = state.phase();

        applyTally(ownTally, ownView);
        applyTally(opponentTally, opponentView);
        invalidate();
    }

    private static String summary(RoundOutcome outcome) {
        if (outcome == null) {
            return "-";
        }
        return outcome.winner() + "/" + outcome.damage() + "/"
                + outcome.player1Score().finalScore() + "/" + outcome.player2Score().finalScore();
    }

    private void applyTally(ScoreTally tally, PlayerView view) {
        ScoreResult score = view.tableScore();
        tally.setHand(view.name().toUpperCase()
                + (score == null ? "" : " \u00b7 " + score.type().displayName()));
        if (score == null) {
            tally.clearValues();
            return;
        }
        tally.setValues(score.chips(), score.mult(), score.finalScore(), true);
    }

    /**
     * A mesa é SEMPRE esticada pelo layout da tela (a região da mesa é decidida em
     * {@link LayoutMetrics}); não existe altura preferida que possa empurrar o painel para fora da
     * região e espremer a barra de ações.
     */
    @Override
    public float getPrefHeight() {
        return 0f;
    }

    /** Tamanho ÚNICO das cartas da mesa, fornecido pelo layout da tela. */
    public void setCardMetrics(CardMetrics cardMetrics) {
        if (cardMetrics == null || cardMetrics.equals(this.cardMetrics)) {
            return;
        }
        this.cardMetrics = cardMetrics;
        invalidate();
    }

    public CardMetrics cardMetrics() {
        return cardMetrics;
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        invalidate();
    }

    @Override
    public void layout() {
        float padding = 6f;
        float rowSlots = SLOTS + POWER_SLOTS;
        // O placar é dimensionado pelo conteúdo (texto real); as cartas recebem o que sobra.
        float tallyWidth = Math.min(ownTally.contentWidth(),
                Math.max(60f, getWidth() * 0.45f));
        float cardsAvailable = Math.max(0f, getWidth() - padding * 2f - 10f - tallyWidth);
        float cardWidth = Math.min(cardMetrics.tableCardWidth(),
                cardsAvailable / TABLE_ROW_FACTOR);
        float cardHeight = Math.min(cardWidth * LayoutMetrics.CARD_ASPECT,
                Math.max(1f, (getHeight() - padding * 2f - 8f) / 2f));
        cardWidth = cardHeight / LayoutMetrics.CARD_ASPECT;
        this.laidOutCardWidth = cardWidth;
        this.laidOutCardHeight = cardHeight;

        float gap = MathUtils.clamp(getHeight() - 2f * cardHeight - padding * 2f, 8f, 44f);
        float cardGap = Math.max(4f, cardWidth * 0.22f);
        float powerGap = cardGap * 1.6f;
        float rowWidth = rowSlots * cardWidth + (rowSlots - 1) * cardGap + powerGap;
        float totalWidth = rowWidth + 10f + tallyWidth;
        float startX = Math.max(padding, (getWidth() - totalWidth) / 2f);
        float tallyX = startX + rowWidth + 10f;

        float topY = Math.max(padding,
                (getHeight() + 2f * cardHeight + gap) / 2f - cardHeight);
        float bottomY = topY - gap - cardHeight;
        opponent.layoutRow(startX, topY, cardWidth, cardHeight, cardGap);
        own.layoutRow(startX, bottomY, cardWidth, cardHeight, cardGap);
        // O painel do placar nunca é maior que a fileira de cartas; fica centralizado nela e
        // inteiro dentro da região da mesa.
        float tallyHeight = Math.min(ownTally.getPrefHeight(), cardHeight);
        opponentTally.setBounds(tallyX, topY + (cardHeight - tallyHeight) / 2f,
                tallyWidth, tallyHeight);
        ownTally.setBounds(tallyX, bottomY + (cardHeight - tallyHeight) / 2f,
                tallyWidth, tallyHeight);
        opponentTally.validate();
        ownTally.validate();

        float centerY = (topY + bottomY + cardHeight) / 2f;
        vsLabel.setBounds(0f, centerY - 10f, getWidth(), 24f);
    }

    /** Largura efetiva das cartas da mesa neste quadro (já cabendo largura e altura). */
    public float laidOutCardWidth() {
        return laidOutCardWidth;
    }

    /** Altura efetiva das cartas da mesa neste quadro. */
    public float laidOutCardHeight() {
        return laidOutCardHeight;
    }

    /** QA: quantos espaços do lado pedido mostram verso, face para cima, ou estão vazios. */
    public int[] probeRow(boolean mine) {
        Side side = mine ? own : opponent;
        int backs = 0;
        int faceUp = 0;
        int empty = 0;
        for (int i = 0; i < SLOTS; i++) {
            CardActor actor = side.slot(i).card();
            if (actor == null) {
                empty++;
            } else if (actor.isFaceDown()) {
                backs++;
            } else {
                faceUp++;
            }
        }
        return new int[]{backs, faceUp, empty};
    }

    /** QA: nº de poderes jogados desenhados no lado e se o rótulo "SEM PODER" está visível. */
    public int probePowerSlots(boolean mine) {
        Side side = mine ? own : opponent;
        int shown = 0;
        for (int i = 0; i < POWER_SLOTS; i++) {
            if (side.powerSlot(i).power() != null) {
                shown++;
            }
        }
        return shown;
    }

    /** QA: o rótulo "SEM PODER" está sendo desenhado? */
    public boolean probeNoPowerVisible(boolean mine) {
        return (mine ? own : opponent).noPower.isVisible();
    }

    /** QA: o placar do lado pedido, para checar as células de texto. */
    public ScoreTally probeTally(boolean mine) {
        return mine ? ownTally : opponentTally;
    }

    /**
     * Placar de um lado, em UMA linha de células de tamanho fixo: nome da mão em cima e, embaixo,
     * {@code [FICHAS] x [MULT] = [PONTOS]}. Cada célula é dimensionada pelo {@link GlyphLayout} do
     * MAIOR valor que ela pode mostrar na fonte atual (fichas 4 dígitos, mult 2, pontos 6), então
     * nada transborda do painel escuro; o painel se dimensiona ao conteúdo e é centralizado na
     * fileira de cartas pelo {@link SharedTablePanel}.
     */
    public static final class ScoreTally extends Table {

        private static final String MAX_CHIPS = "9999";
        private static final String MAX_MULT = "99";
        private static final String MAX_SCORE = "999999";
        private static final float CELL_PAD_X = 9f;
        private static final float CELL_PAD_Y = 2f;
        private static final float ROW_GAP = 2f;
        private static final float BORDER_PAD = 6f;

        private final Label hand;
        private final Box chipsBox;
        private final Box multBox;
        private final Label score;
        private final Label times;
        private final Label equals;
        private final Table cells;
        private final GlyphLayout glyphs = new GlyphLayout();

        private float chipsCellWidth;
        private float multCellWidth;
        private float scoreCellWidth;
        private float pillHeight = 30f;

        ScoreTally(GameAssets assets, String handStyle) {
            Skin skin = assets.skin();
            setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDeep"))));
            setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
            pad(BORDER_PAD, BORDER_PAD, BORDER_PAD, BORDER_PAD);
            hand = Ui.label(skin, "", handStyle);
            hand.setEllipsis(true);
            hand.setAlignment(Align.left);

            Table row = new Table();
            cells = row;
            chipsBox = new Box(assets, "CHIPS", Palette.CHIPS);
            multBox = new Box(assets, "MULT", Palette.MULT);
            score = Ui.label(skin, "", "final");
            score.setAlignment(Align.right);
            times = Ui.label(skin, "\u00d7", "h3");
            equals = Ui.label(skin, "=", "h3");
            row.add(chipsBox).height(pillHeight).width(chipsCellWidth);
            row.add(times).padLeft(ROW_GAP).padRight(ROW_GAP);
            row.add(multBox).height(pillHeight).width(multCellWidth);
            row.add(equals).padLeft(ROW_GAP).padRight(ROW_GAP);
            row.add(score).width(scoreCellWidth).right();

            add(hand).left().growX().height(handHeight()).row();
            add(row).left().growX().padTop(ROW_GAP);
            measureCells();
        }

        private float handHeight() {
            return lineHeight(hand);
        }

        /** Redimensiona as células a partir do texto real na escala de fonte corrente. */
        private void measureCells() {
            chipsCellWidth = Math.max(textWidth(chipsBox.value, MAX_CHIPS),
                    textWidth(chipsBox.unit, "CHIPS")) + CELL_PAD_X * 2f;
            multCellWidth = Math.max(textWidth(multBox.value, MAX_MULT),
                    textWidth(multBox.unit, "MULT")) + CELL_PAD_X * 2f;
            scoreCellWidth = textWidth(score, MAX_SCORE) + CELL_PAD_X * 2f;
            float valueHeight = Math.max(lineHeight(chipsBox.value), lineHeight(multBox.value));
            float unitHeight = Math.max(lineHeight(chipsBox.unit), lineHeight(multBox.unit));
            pillHeight = Math.max(26f, valueHeight + unitHeight + CELL_PAD_Y * 2f);
            cells.getCell(chipsBox).width(chipsCellWidth).height(pillHeight);
            cells.getCell(multBox).width(multCellWidth).height(pillHeight);
            cells.getCell(score).width(scoreCellWidth);
            invalidateHierarchy();
        }

        /** Largura de célula + espaçamentos: é a largura que o painel ocupa. */
        public float contentWidth() {
            measureCells();
            return BORDER_PAD * 2f + chipsCellWidth + multCellWidth + scoreCellWidth
                    + textWidth(times, "\u00d7") * 2f + ROW_GAP * 4f + 2f;
        }

        @Override
        public float getPrefWidth() {
            return contentWidth();
        }

        @Override
        public float getPrefHeight() {
            return BORDER_PAD * 2f + handHeight() + ROW_GAP + pillHeight;
        }

        /** QA: verifica que cada célula comporta o MAIOR valor possível na fonte atual. */
        public List<String> probeCellWidths() {
            List<String> problems = new ArrayList<>();
            String savedHand = hand.getText().toString();
            String savedChips = chipsBox.value.getText().toString();
            String savedMult = multBox.value.getText().toString();
            String savedScore = score.getText().toString();
            setValues(9999, 99, 999999, true);
            setHand("FULL HOUSE \u00b7 PAR");
            measureCells();
            checkCell(problems, "fichas", chipsBox.value, chipsCellWidth);
            checkCell(problems, "mult", multBox.value, multCellWidth);
            checkCell(problems, "pontos", score, scoreCellWidth);
            if (getWidth() > 0.5f && contentWidth() > getWidth() + 0.5f) {
                problems.add("painel do placar menor que o conteúdo ("
                        + Math.round(contentWidth()) + " > " + Math.round(getWidth()) + ")");
            }
            if (getHeight() > 0.5f && getPrefHeight() > getHeight() + 0.5f) {
                problems.add("painel do placar baixo demais (" + Math.round(getPrefHeight())
                        + " > " + Math.round(getHeight()) + ")");
            }
            setHand(savedHand);
            chipsBox.value.setText(savedChips);
            multBox.value.setText(savedMult);
            score.setText(savedScore);
            measureCells();
            return problems;
        }

        private void checkCell(List<String> problems, String name, Label label, float cell) {
            float needed = label.getPrefWidth() + CELL_PAD_X;
            if (needed > cell + 0.5f) {
                problems.add("célula de " + name + " pequena (" + Math.round(needed)
                        + " > " + Math.round(cell) + ")");
            }
        }

        private float textWidth(Label label, String text) {
            glyphs.setText(label.getStyle().font, text);
            return glyphs.width;
        }

        private static float lineHeight(Label label) {
            return label.getStyle().font.getLineHeight();
        }

        public Label handLabel() {
            return hand;
        }

        public Label chipsLabel() {
            return chipsBox.value;
        }

        public Label multLabel() {
            return multBox.value;
        }

        public Label scoreLabel() {
            return score;
        }

        public void setHand(String text) {
            hand.setText(text == null ? "" : text);
        }

        public void setValues(int chips, int mult, int finalScore, boolean showScore) {
            chipsBox.value.setText(Integer.toString(chips));
            multBox.value.setText(Integer.toString(mult));
            score.setText(showScore ? Integer.toString(finalScore) : "");
            score.setVisible(showScore);
        }

        public void setScore(int finalScore) {
            score.setText(Integer.toString(finalScore));
            score.setVisible(true);
        }

        public void setChips(int chips) {
            chipsBox.value.setText(Integer.toString(chips));
        }

        public void setMult(int mult) {
            multBox.value.setText(Integer.toString(mult));
        }

        public void clearValues() {
            chipsBox.value.setText("\u2014");
            multBox.value.setText("\u2014");
            score.setText("");
            score.setVisible(false);
        }
    }

    private static final class Box extends Table {

        private final Label value;
        private final Label unit;

        Box(GameAssets assets, String unit, Color color) {
            Skin skin = assets.skin();
            setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                    .tint(color));
            value = Ui.label(skin, "0", "body");
            this.unit = Ui.label(skin, unit, "white-small");
            add(value).padTop(1f).row();
            add(this.unit).padBottom(1f);
        }
    }

    private final class Side extends WidgetGroup {

        private final boolean mine;
        private final Slot[] slots = new Slot[SLOTS];
        private final Slot[] powerSlots = new Slot[POWER_SLOTS];
        private final Label noPower;

        Side(boolean mine) {
            this.mine = mine;
            setTransform(false);
            for (int i = 0; i < SLOTS; i++) {
                slots[i] = new Slot();
                addActor(slots[i]);
            }
            for (int i = 0; i < POWER_SLOTS; i++) {
                powerSlots[i] = new Slot();
                addActor(powerSlots[i]);
            }
            noPower = Ui.label(skin, "SEM PODER", "body-dim");
            noPower.setAlignment(Align.center);
            noPower.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
            noPower.setVisible(false);
            addActor(noPower);
        }

        Slot slot(int index) {
            return index >= 0 && index < SLOTS ? slots[index] : null;
        }

        Slot powerSlot(int index) {
            return index >= 0 && index < POWER_SLOTS ? powerSlots[index] : null;
        }

        void layoutRow(float x, float y, float cardWidth, float cardHeight, float gap) {
            for (int i = 0; i < SLOTS; i++) {
                slots[i].setBounds(x + i * (cardWidth + gap), y, cardWidth, cardHeight);
                slots[i].resizeCard(cardWidth, cardHeight);
            }
            float powerX = x + SLOTS * (cardWidth + gap) + gap * 1.6f;
            for (int i = 0; i < POWER_SLOTS; i++) {
                powerSlots[i].setBounds(powerX + i * (cardWidth + gap), y, cardWidth, cardHeight);
                powerSlots[i].resizeCard(cardWidth, cardHeight);
            }
            noPower.setBounds(powerX, y, POWER_SLOTS * cardWidth + gap, cardHeight);
        }

        /** Mostra os dois espaços de poder da revelação; vazios ficam apagados. */
        void applyPowers(List<PowerCard> powers, boolean visible) {
            for (Slot slot : powerSlots) {
                slot.clearCard();
            }
            noPower.setVisible(visible && powers.isEmpty());
            if (!visible) {
                return;
            }
            for (int i = 0; i < POWER_SLOTS && i < powers.size(); i++) {
                Slot slot = powerSlots[i];
                PowerCardActor card = new PowerCardActor(assets, powers.get(i), false, null);
                card.setInteractive(false);
                if (tooltip != null) {
                    card.setTooltip(tooltip);
                }
                card.showInstantly();
                card.setSize(slot.getWidth(), slot.getHeight());
                slot.setCard(card);
            }
        }

        /** Repõe as cartas da mesa no estado anterior às sabotagens de mesa. */
        void setCardsForResolution(List<Card> cards) {
            for (int i = 0; i < SLOTS; i++) {
                Slot slot = slots[i];
                slot.clearCard();
                if (i >= cards.size()) {
                    continue;
                }
                CardActor card = new CardActor(assets, cards.get(i), false, null);
                card.setInteractive(false);
                card.showInstantly();
                card.setSize(slot.getWidth(), slot.getHeight());
                slot.setCard(card);
            }
        }

        void replaceSlotCard(int index, Card card) {
            Slot slot = slot(index);
            if (slot == null) {
                return;
            }
            slot.clearCard();
            if (card == null) {
                return;
            }
            CardActor actor = new CardActor(assets, card, false, null);
            actor.setInteractive(false);
            actor.showInstantly();
            actor.setSize(slot.getWidth(), slot.getHeight());
            slot.setCard(actor);
        }

        /**
         * @param cardsChanged  a lista de cartas mudou desde a última atualização
         * @param animate       animações ligadas
         * @param flip          o lado do oponente deve virar as cartas ao aparecerem
         */
        void apply(PlayerView view, boolean cardsChanged, boolean animateCards, boolean flip) {
            if (view.playedCards().isEmpty()) {
                if (view.submitted()) {
                    showBacks();
                } else {
                    clearCards();
                }
                return;
            }
            if (!cardsChanged) {
                return;
            }
            if (mine && !landing.isEmpty() && landing.size() == view.playedCards().size()) {
                adoptLanding(animateCards);
                return;
            }
            createCards(view.playedCards(), animateCards, flip);
        }

        private void adoptLanding(boolean animateCards) {
            for (int i = 0; i < SLOTS; i++) {
                Slot slot = slots[i];
                slot.clearCard();
                if (i >= landing.size()) {
                    continue;
                }
                CardActor actor = landing.get(i);
                actor.setSelected(false);
                actor.setInteractive(false);
                if (!animateCards) {
                    slot.setCard(actor);
                    actor.setPosition(0f, 0f);
                    actor.setRotation(0f);
                    actor.setSize(slot.getWidth(), slot.getHeight());
                    continue;
                }
                actor.setRotation(-6f);
                scratch.set(0f, 0f);
                slot.localToStageCoordinates(scratch);
                float targetX = scratch.x;
                float targetY = scratch.y;
                final int index = i;
                actor.addAction(Actions.sequence(
                        Actions.delay(index * LAND_STAGGER),
                        Actions.parallel(
                                Actions.moveTo(targetX, targetY, LAND_DURATION,
                                        Interpolation.swingOut),
                                Actions.rotateTo(0f, LAND_DURATION, Interpolation.swingOut),
                                Actions.sizeTo(slot.getWidth(), slot.getHeight(), LAND_DURATION)),
                        Actions.run(() -> {
                            slot.setCard(actor);
                            actor.setPosition(0f, 0f);
                            actor.setRotation(0f);
                            actor.setSize(slot.getWidth(), slot.getHeight());
                        })));
            }
        }

        private void createCards(List<Card> cards, boolean animateCards, boolean flip) {
            for (int i = 0; i < SLOTS; i++) {
                Slot slot = slots[i];
                slot.clearCard();
                if (i >= cards.size()) {
                    continue;
                }
                CardActor card = new CardActor(assets, cards.get(i), false, null);
                card.setInteractive(false);
                card.setSize(slot.getWidth(), slot.getHeight());
                slot.setCard(card);
                if (!animateCards) {
                    card.showInstantly();
                    continue;
                }
                if (mine) {
                    card.showInstantly();
                    card.setPosition(0f, -48f);
                    card.setRotation(-6f);
                    card.addAction(Actions.sequence(
                            Actions.delay(i * LAND_STAGGER),
                            Actions.parallel(
                                    Actions.moveTo(0f, 0f, LAND_DURATION, Interpolation.swingOut),
                                    Actions.rotateTo(0f, LAND_DURATION, Interpolation.swingOut))));
                } else if (flip) {
                    card.setPosition(0f, 48f);
                    card.startFlip(DROP_DURATION + 0.05f + i * FLIP_STAGGER, FLIP_DURATION);
                    card.addAction(Actions.sequence(
                            Actions.delay(i * LAND_STAGGER),
                            Actions.moveTo(0f, 0f, DROP_DURATION, Interpolation.swingOut)));
                } else {
                    card.showInstantly();
                }
            }
        }

        private void showBacks() {
            for (int i = 0; i < slots.length; i++) {
                Slot slot = slots[i];
                if (slot.card() != null) {
                    continue;
                }
                CardActor back = new CardActor(assets, null, true, null);
                back.setInteractive(false);
                back.setSize(slot.getWidth(), slot.getHeight());
                slot.setCard(back);
                if (animate) {
                    back.startDeal(i * 0.08f, 0.30f);
                } else {
                    back.showInstantly();
                }
            }
        }

        private void clearCards() {
            for (Slot slot : slots) {
                slot.clearCard();
            }
        }
    }

    private final class Slot extends Group {

        private CardActor card;
        private PowerCardActor power;

        void setCard(CardActor actor) {
            card = actor;
            power = null;
            addActor(actor);
        }

        void setCard(PowerCardActor actor) {
            power = actor;
            card = null;
            addActor(actor);
        }

        CardActor card() {
            return card;
        }

        PowerCardActor power() {
            return power;
        }

        void clearCard() {
            if (card != null) {
                card.remove();
                card = null;
            }
            if (power != null) {
                power.remove();
                power = null;
            }
        }

        void resizeCard(float width, float height) {
            setSize(width, height);
            if (card != null) {
                card.setSize(width, height);
            }
            if (power != null) {
                power.setSize(width, height);
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
        }
    }
}
