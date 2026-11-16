package com.game.rendering;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.rules.PowerEvent;
import com.game.core.rules.RoundOutcome;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.Jokers;

import java.util.ArrayList;
import java.util.List;

/**
 * Apresentação da resolução: para cada {@link PowerEvent}, na ordem de resolução, a carta de
 * poder voa da mão para o slot, o tooltip aparece por ~1 s, um popup de efeito é mostrado e as
 * cartas de mesa afetadas por sabotagens viram com flip scaleX. A fonte é exclusivamente o
 * {@link RoundOutcome}; nada é recalculado.
 */
public final class ResolutionEffect extends Actor {

    private static final float STEP = 1.0f;
    private static final float FLIGHT = 0.35f;
    private static final float TOOLTIP_TIME = 1.0f;

    private final GameAssets assets;
    private final Stage stage;
    private final SharedTablePanel table;
    private final PowerHand powerHand;
    private final PlayerPanel opponentPanel;
    private final CardTooltip tooltip;
    private final Skin skin;
    private final int myId;

    private final List<PowerCardActor> flights = new ArrayList<>();
    private final List<Actor> popups = new ArrayList<>();
    private final List<Source> ownSources = new ArrayList<>();
    private final List<Source> opponentSources = new ArrayList<>();
    private final int[] eventCount = new int[3];

    private RoundOutcome outcome;
    private Runnable onFinished;
    private boolean running;

    public ResolutionEffect(GameAssets assets, Stage stage, SharedTablePanel table,
                            PowerHand powerHand, PlayerPanel opponentPanel,
                            CardTooltip tooltip, int myId) {
        this.assets = assets;
        this.stage = stage;
        this.table = table;
        this.powerHand = powerHand;
        this.opponentPanel = opponentPanel;
        this.tooltip = tooltip;
        this.skin = assets.skin();
        this.myId = myId;
        setTouchable(Touchable.disabled);
    }

    public void play(RoundOutcome outcome, Runnable onFinished) {
        finishNowQuietly();
        this.outcome = outcome;
        this.onFinished = onFinished;
        this.running = true;
        this.table.clearActions();
        this.powerHand.resetVisuals();
        this.opponentPanel.resetBackVisuals();
        this.table.hidePowerSlotActors();

        List<Card> initial1 = initialCards(outcome, 1);
        List<Card> initial2 = initialCards(outcome, 2);
        table.setResolutionCards(true, initial1);
        table.setResolutionCards(false, initial2);

        buildOwnSources(outcome);
        buildOpponentSources(outcome);

        for (int i = 1; i <= 2; i++) {
            eventCount[i] = 0;
        }
        if (getStage() == null) {
            stage.addActor(this);
        }
        setBounds(0f, 0f, 0f, 0f);
        toFront();

        List<PowerEvent> events = outcome == null ? List.of() : outcome.powerEvents();
        if (events.isEmpty()) {
            addAction(Actions.sequence(Actions.delay(0.1f), Actions.run(this::finishNow)));
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            final int index = i;
            addAction(Actions.sequence(Actions.delay(i * STEP),
                    Actions.run(() -> beginEvent(events.get(index)))));
        }
        addAction(Actions.sequence(
                Actions.delay((events.size() - 1) * STEP + TOOLTIP_TIME),
                Actions.run(this::finishNow)));
    }

    private List<Card> initialCards(RoundOutcome outcome, int player) {
        if (outcome == null) {
            return List.of();
        }
        for (PowerEvent event : outcome.powerEvents()) {
            if (event.targetPlayer() == player && event.cardsBefore() != null) {
                return event.cardsBefore();
            }
        }
        return player == 1 ? outcome.player1Cards() : outcome.player2Cards();
    }

    private void buildOwnSources(RoundOutcome outcome) {
        ownSources.clear();
        if (outcome == null) {
            return;
        }
        List<PowerCard> played = myId == 1 ? outcome.player1Powers() : outcome.player2Powers();
        List<PowerCard> hand = powerHand.cards();
        for (PowerCard card : played) {
            int index = hand.indexOf(card);
            PowerCardActor actor = powerHand.actor(index);
            Source source = sourceOf(actor, card, true);
            ownSources.add(source);
            if (actor != null) {
                actor.setVisible(false);
            }
        }
        for (int i = 0; i < hand.size(); i++) {
            PowerCard card = hand.get(i);
            PowerCardActor actor = powerHand.actor(i);
            if (!played.contains(card) && actor != null) {
                actor.addAction(Actions.fadeOut(0.4f));
            }
        }
    }

    private void buildOpponentSources(RoundOutcome outcome) {
        opponentSources.clear();
        if (outcome == null) {
            return;
        }
        List<PowerCard> played = myId == 1 ? outcome.player2Powers() : outcome.player1Powers();
        List<CardActor> backs = opponentPanel.backActors();
        for (int i = 0; i < played.size(); i++) {
            CardActor actor = i < backs.size() ? backs.get(i) : null;
            Source source = sourceOf(actor, played.get(i), false);
            opponentSources.add(source);
            if (actor != null) {
                actor.setVisible(false);
            }
        }
        for (int i = played.size(); i < backs.size(); i++) {
            backs.get(i).addAction(Actions.fadeOut(0.4f));
        }
    }

    private Source sourceOf(Actor actor, PowerCard card, boolean own) {
        if (actor != null && actor.getWidth() > 1f && actor.getHeight() > 1f) {
            Vector2 position = actor.localToStageCoordinates(new Vector2(0f, 0f));
            return new Source(card, position.x, position.y, actor.getWidth(), actor.getHeight());
        }
        float width = Math.max(96f, stage.getWidth() * 0.09f);
        float height = width * com.game.client.layout.LayoutMetrics.CARD_ASPECT;
        float x = own ? stage.getWidth() * 0.34f : stage.getWidth() * 0.56f;
        float y = stage.getHeight() * 0.22f;
        return new Source(card, x, y, width, height);
    }

    private void beginEvent(PowerEvent event) {
        if (!running) {
            return;
        }
        int player = event.player();
        boolean mine = player == myId;
        int slotIndex = eventCount[player]++;
        Source source = mine
                ? (slotIndex < ownSources.size() ? ownSources.get(slotIndex) : fallback(event))
                : (slotIndex < opponentSources.size()
                        ? opponentSources.get(slotIndex) : fallback(event));
        PowerCard card = new PowerCard(event.kind(), event.id());
        Vector2 target = table.powerSlotCenter(mine, slotIndex);

        PowerCardActor flight = new PowerCardActor(assets, card, false, null);
        flight.setInteractive(false);
        flight.showInstantly();
        flight.setSize(source.width, source.height);
        flight.setPosition(source.x, source.y);
        flight.setOrigin(Align.center);
        stage.addActor(flight);
        flights.add(flight);

        tooltip.show(flight, titleOf(card), descriptionOf(card));
        addAction(Actions.sequence(Actions.delay(TOOLTIP_TIME),
                Actions.run(() -> tooltip.hide(flight))));

        float targetX = target.x - source.width / 2f;
        float targetY = target.y - source.height / 2f;
        flight.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.moveTo(targetX, targetY, FLIGHT, Interpolation.swingOut),
                        Actions.sequence(
                                Actions.scaleTo(1.2f, 1.2f, FLIGHT * 0.5f,
                                        Interpolation.swingOut),
                                Actions.scaleTo(1f, 1f, FLIGHT * 0.5f,
                                        Interpolation.swingOut))),
                Actions.run(() -> landed(flight, mine, slotIndex, card))));

        if (event.cardsBefore() != null && event.cardsAfter() != null) {
            List<Card> before = event.cardsBefore();
            List<Card> after = event.cardsAfter();
            boolean targetMine = event.targetPlayer() == myId;
            addAction(Actions.sequence(Actions.delay(FLIGHT), Actions.run(() -> {
                for (int i = 0; i < before.size() && i < after.size(); i++) {
                    if (!before.get(i).equals(after.get(i))) {
                        table.flipResolutionCard(targetMine, i, after.get(i));
                    }
                }
            })));
        }
        addAction(Actions.sequence(Actions.delay(FLIGHT),
                Actions.run(() -> popup(target, card))));
    }

    private Source fallback(PowerEvent event) {
        PowerCard card = new PowerCard(event.kind(), event.id());
        float width = Math.max(96f, stage.getWidth() * 0.09f);
        float height = width * com.game.client.layout.LayoutMetrics.CARD_ASPECT;
        float x = stage.getWidth() * (event.player() == myId ? 0.34f : 0.56f);
        float y = stage.getHeight() * 0.22f;
        return new Source(card, x, y, width, height);
    }

    private void landed(PowerCardActor flight, boolean mine, int slotIndex, PowerCard card) {
        flight.remove();
        flights.remove(flight);
        PowerCardActor slot = table.powerSlotActor(mine, slotIndex);
        if (slot != null) {
            slot.setVisible(true);
            slot.setScale(1.2f, 1.2f);
            slot.addAction(Actions.scaleTo(1f, 1f, 0.2f, Interpolation.swingOut));
            tooltip.show(slot, titleOf(card), descriptionOf(card));
        }
    }

    private void popup(Vector2 target, PowerCard card) {
        Label label = Ui.label(skin, titleOf(card), "gold-small");
        label.setColor(card.kind() == PowerKind.JOKER ? Palette.GOLD : Palette.MULT);
        label.pack();
        label.setPosition(target.x - label.getWidth() / 2f, target.y + 12f);
        stage.addActor(label);
        label.toFront();
        label.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.moveBy(0f, 34f, 0.55f, Interpolation.pow2Out),
                        Actions.sequence(Actions.delay(0.18f), Actions.fadeOut(0.37f))),
                Actions.removeActor()));
        popups.add(label);
    }

    public void finishNow() {
        if (!running) {
            return;
        }
        boolean callbackPending = true;
        running = false;
        clearActions();
        table.clearActions();
        tooltip.hide();
        for (PowerCardActor flight : new ArrayList<>(flights)) {
            flight.remove();
        }
        flights.clear();
        for (Actor popup : popups) {
            popup.remove();
        }
        popups.clear();
        if (outcome != null) {
            table.setResolutionCards(true, outcome.player1Cards());
            table.setResolutionCards(false, outcome.player2Cards());
        }
        table.revealAllPowerSlots();
        if (getStage() != null) {
            remove();
        }
        Runnable callback = onFinished;
        onFinished = null;
        if (callbackPending && callback != null) {
            callback.run();
        }
    }

    private void finishNowQuietly() {
        if (!running) {
            return;
        }
        running = false;
        clearActions();
        table.clearActions();
        tooltip.hide();
        for (PowerCardActor flight : new ArrayList<>(flights)) {
            flight.remove();
        }
        flights.clear();
        for (Actor popup : popups) {
            popup.remove();
        }
        popups.clear();
        if (getStage() != null) {
            remove();
        }
        onFinished = null;
    }

    public boolean isRunning() {
        return running;
    }

    public void dispose() {
        finishNowQuietly();
    }

    private static String titleOf(PowerCard card) {
        return card.kind() == PowerKind.JOKER
                ? Jokers.nameOf(card.id()) : Sabotages.nameOf(card.id());
    }

    private static String descriptionOf(PowerCard card) {
        return card.kind() == PowerKind.JOKER
                ? Jokers.descriptionOf(card.id()) : Sabotages.descriptionOf(card.id());
    }

    private record Source(PowerCard card, float x, float y, float width, float height) {
    }
}
