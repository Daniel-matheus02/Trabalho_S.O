package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
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
import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

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
    private static final float MIN_CARD_HEIGHT = 28f;
    private static final float MAX_CARD_HEIGHT = 110f;
    private static final float CARD_ASPECT = 1.38f;
    private static final float LAND_DURATION = 0.35f;
    private static final float DROP_DURATION = 0.32f;
    private static final float FLIP_DURATION = 0.2f;
    private static final float FLIP_STAGGER = 0.08f;
    private static final float LAND_STAGGER = 0.04f;
    private static final float TALLY_WIDTH = 168f;

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
    private List<Card> shownOwn = List.of();
    private List<Card> shownOpponent = List.of();
    private boolean shownOpponentSubmitted;
    private List<CardActor> landing = List.of();
    private float cardHeight = 46f;

    public SharedTablePanel(GameAssets assets) {
        this.assets = assets;
        this.skin = assets.skin();
        setTransform(false);
        opponent = new Side(false);
        own = new Side(true);
        vsLabel = Ui.label(skin, "VS", "h2");
        vsLabel.setAlignment(Align.center);
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
        opponent.apply(opponentView, opponentCardsChanged, animate,
                opponentJustSubmitted || shownOpponent.isEmpty());
        boolean powersVisible = state.phase() == GamePhase.REVEAL
                || state.phase() == GamePhase.GAME_OVER;
        own.applyPowers(ownView.playedPowers(), powersVisible);
        opponent.applyPowers(opponentView.playedPowers(), powersVisible);
        shownOwn = ownView.playedCards();
        shownOpponent = opponentView.playedCards();
        shownOpponentSubmitted = opponentView.submitted();
        landing = List.of();

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

    @Override
    public float getPrefHeight() {
        return MIN_CARD_HEIGHT * 2f + 28f;
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        invalidate();
    }

    @Override
    public void layout() {
        float padding = 6f;
        float gap = MathUtils.clamp(getHeight() * 0.16f, 12f, 44f);
        float available = Math.max(MIN_CARD_HEIGHT,
                (getHeight() - gap - padding * 2f) / 2f);
        cardHeight = MathUtils.clamp(available, MIN_CARD_HEIGHT, MAX_CARD_HEIGHT);
        float cardWidth = cardHeight / CARD_ASPECT;
        float cardGap = Math.max(4f, cardWidth * 0.22f);
        float powerGap = cardGap * 1.6f;
        int rowSlots = SLOTS + POWER_SLOTS;
        float rowWidth = rowSlots * cardWidth + (rowSlots - 1) * cardGap + powerGap;
        float tallyWidth = Math.min(TALLY_WIDTH, Math.max(60f, getWidth() - rowWidth - 24f));
        // Centraliza o conjunto cartas + placar; centralizar só a fileira empurrava o placar
        // para dentro dos slots de poder.
        float totalWidth = rowWidth + 10f + tallyWidth;
        float startX = Math.max(padding, (getWidth() - totalWidth) / 2f);
        float tallyX = startX + rowWidth + 10f;

        float block = 2f * cardHeight + gap;
        float topY = Math.max(padding, (getHeight() + block) / 2f - cardHeight);
        float bottomY = Math.max(padding, topY - gap - cardHeight);
        opponent.layoutRow(startX, topY, cardWidth, cardHeight, cardGap);
        own.layoutRow(startX, bottomY, cardWidth, cardHeight, cardGap);
        opponentTally.setBounds(tallyX, topY, tallyWidth, cardHeight);
        ownTally.setBounds(tallyX, bottomY, tallyWidth, cardHeight);
        opponentTally.validate();
        ownTally.validate();

        float centerY = (topY + bottomY + cardHeight) / 2f;
        vsLabel.setBounds(0f, centerY - 10f, getWidth(), 24f);
    }

    /**
     * Placar de um lado: nome da mão, caixa azul de FICHAS, caixa vermelha de MULT e o SCORE.
     * A contagem animada altera os rótulos diretamente.
     */
    public static final class ScoreTally extends Table {

        private final Label hand;
        private final Box chipsBox;
        private final Box multBox;
        private final Label score;

        ScoreTally(GameAssets assets, String handStyle) {
            Skin skin = assets.skin();
            setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDeep"))));
            pad(2, 4, 2, 4);
            hand = Ui.label(skin, "", handStyle);
            add(hand).left().growX().row();

            Table row = new Table();
            chipsBox = new Box(assets, "CHIPS", Palette.CHIPS);
            multBox = new Box(assets, "MULT", Palette.MULT);
            score = Ui.label(skin, "", "final");
            row.add(chipsBox).height(30).width(56);
            row.add(Ui.label(skin, "\u00d7", "h3")).padLeft(2).padRight(2);
            row.add(multBox).height(30).width(48);
            row.add(Ui.label(skin, "=", "h3")).padLeft(2).padRight(2);
            row.add(score).minWidth(32).left();
            add(row).left().growX().padTop(1);
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

        Box(GameAssets assets, String unit, Color color) {
            Skin skin = assets.skin();
            setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                    .tint(color));
            value = Ui.label(skin, "0", "body");
            add(value).padTop(0).row();
            add(Ui.label(skin, unit, "white-small")).padBottom(0);
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
            for (Slot slot : slots) {
                if (slot.card() != null) {
                    continue;
                }
                CardActor back = new CardActor(assets, null, true, null);
                back.setInteractive(false);
                back.setSize(slot.getWidth(), slot.getHeight());
                slot.setCard(back);
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
            if (card == null && power == null) {
                tmp.set(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b,
                        0.55f * parentAlpha);
                Draws.patch(batch, assets.patch("panelDeep"), getX(), getY(), getWidth(),
                        getHeight(), tmp);
            }
            super.draw(batch, parentAlpha);
        }
    }
}
