package com.game.client.animation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.client.presentation.RoundPresenter;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoreStep;
import com.game.core.scoring.StepKind;
import com.game.rendering.CardActor;
import com.game.rendering.Palette;
import com.game.rendering.PowerCardActor;
import com.game.rendering.SharedTablePanel;
import com.game.rendering.SharedTablePanel.ScoreTally;
import com.game.rendering.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Executa as batidas de {@link ScoreCountTimeline} dos dois lados em paralelo, usando Actions do
 * Scene2D sobre o {@link SharedTablePanel}. O lado com menos batidas simplesmente permanece no
 * valor final enquanto o outro continua.
 */
public final class ScoreCountAnimator implements RoundPresenter.CountPlayer {

    private final SharedTablePanel table;
    private final GameAssets assets;
    private final Skin skin;
    private final boolean animate;
    private final IntConsumer shakeScreen;

    private final List<Actor> touched = new ArrayList<>();
    private final List<Actor> transientActors = new ArrayList<>();

    private ScoreResult ownResult;
    private ScoreResult opponentResult;
    private Runnable onFinished;
    private boolean running;

    public ScoreCountAnimator(SharedTablePanel table, GameAssets assets, boolean animate,
                              IntConsumer shakeScreen) {
        this.table = table;
        this.assets = assets;
        this.skin = assets.skin();
        this.animate = animate;
        this.shakeScreen = shakeScreen;
    }

    @Override
    public void play(ScoreResult own, ScoreResult opponent, Runnable onFinished) {
        resetAnimations();
        this.ownResult = own;
        this.opponentResult = opponent;
        this.onFinished = onFinished;
        this.running = true;

        if (!animate || (own == null && opponent == null)) {
            finishNow();
            return;
        }
        if (own == null || own.steps().isEmpty()) {
            table.tallyView(true).clearValues();
        } else {
            startSide(own, true);
        }
        if (opponent == null || opponent.steps().isEmpty()) {
            table.tallyView(false).clearValues();
        } else {
            startSide(opponent, false);
        }

        float total = Math.max(ScoreCountTimeline.totalDuration(own),
                ScoreCountTimeline.totalDuration(opponent));
        table.addAction(Actions.sequence(Actions.delay(Math.max(0.01f, total)),
                Actions.run(this::complete)));
    }

    private void startSide(ScoreResult result, boolean mine) {
        ScoreTally tally = table.tallyView(mine);
        List<ScoreCountTimeline.Beat> beats = ScoreCountTimeline.build(result);
        if (beats.isEmpty()) {
            tally.clearValues();
            return;
        }
        ScoreCountTimeline.Beat first = beats.get(0);
        tally.setHand(result.type().displayName());
        tally.setChips(first.step().chipsAfter());
        tally.setMult(first.step().multAfter());
        tally.setValues(first.step().chipsAfter(), first.step().multAfter(), 0, false);
        popHand(tally);
        touched.add(tally.chipsLabel());
        touched.add(tally.multLabel());
        touched.add(tally.scoreLabel());
        touched.add(tally.handLabel());
        schedule(0.01f, () -> popupAtTally(tally, first.popupText(), "neutral"));

        for (int i = 1; i < beats.size() - 1; i++) {
            ScoreCountTimeline.Beat beat = beats.get(i);
            schedule(beat.timeOffset(), () -> applyBeat(beat, mine));
        }
        ScoreCountTimeline.Beat finalBeat = beats.get(beats.size() - 1);
        schedule(finalBeat.timeOffset(), () -> applyFinalBeat(finalBeat, result, tally));
    }

    private void applyBeat(ScoreCountTimeline.Beat beat, boolean mine) {
        ScoreTally tally = table.tallyView(mine);
        ScoreStep step = beat.step();
        tally.setMult(step.multAfter());
        animateChips(tally, step.chipsAfter());

        if (step.kind() == StepKind.CARD) {
            CardActor card = table.slotActor(mine, step.cardIndex());
            if (card != null) {
                popCard(card);
                popupAt(card, beat.popupText(), beat.colorKey());
                return;
            }
        } else if (step.kind() == StepKind.JOKER || step.kind() == StepKind.SABOTAGE) {
            // O joker que pontua está no lado do jogador; a sabotagem que o afeta foi jogada
            // pelo OPONENTE e está no slot do outro lado.
            boolean powerMine = step.kind() == StepKind.JOKER ? mine : !mine;
            PowerCardActor power = table.powerActorFor(powerMine, step.sourceId());
            if (power != null) {
                pulsePower(power, step.kind() == StepKind.SABOTAGE);
                popupAt(power, beat.popupText(), beat.colorKey());
                return;
            }
        }
        popupAtTally(tally, beat.popupText(), beat.colorKey());
    }

    private void applyFinalBeat(ScoreCountTimeline.Beat beat, ScoreResult result,
                                ScoreTally tally) {
        ScoreStep last = beat.step();
        tally.setChips(last.chipsAfter());
        tally.setMult(last.multAfter());
        Label score = tally.scoreLabel();
        score.setVisible(true);
        touched.add(score);
        score.addAction(new IntCountAction(0, result.finalScore(),
                ScoreFeel.FINAL_COUNT, value -> score.setText(Integer.toString(value))).easeOut());
        spawnTrailParticles(tally);
        if (shakeScreen != null) {
            shakeScreen.accept(beat.shakeLevel());
        }
    }

    private void animateChips(ScoreTally tally, int target) {
        Label label = tally.chipsLabel();
        int from = parse(label.getText().toString());
        touched.add(label);
        label.addAction(new IntCountAction(from, target, ScoreFeel.CHIPS_ROLL,
                value -> label.setText(Integer.toString(value))).easeOut());
    }

    private void popHand(ScoreTally tally) {
        Label hand = tally.handLabel();
        hand.setOrigin(Align.left);
        hand.addAction(Actions.sequence(
                Actions.scaleTo(1.18f, 1.18f, ScoreFeel.CARD_POP_UP),
                Actions.scaleTo(1f, 1f, ScoreFeel.CARD_POP, Interpolation.swingOut)));
        touched.add(hand);
    }

    private void popCard(CardActor card) {
        card.setOrigin(Align.center);
        float wobble = (MathUtils.randomBoolean() ? 1f : -1f)
                * MathUtils.random(1f, ScoreFeel.CARD_WOBBLE_DEGREES);
        card.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.scaleTo(1.18f, 1.18f, ScoreFeel.CARD_POP_UP),
                        Actions.rotateTo(wobble, ScoreFeel.CARD_POP_UP)),
                Actions.parallel(
                        Actions.scaleTo(1f, 1f, ScoreFeel.CARD_POP, Interpolation.swingOut),
                        Actions.rotateTo(0f, ScoreFeel.CARD_POP, Interpolation.swingOut))));
        touched.add(card);
    }

    private void pulsePower(PowerCardActor power, boolean sabotage) {
        power.setOrigin(Align.center);
        Color flash = sabotage ? Palette.MULT : Palette.GOLD;
        power.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.scaleTo(1.16f, 1.16f, ScoreFeel.CARD_POP_UP),
                        Actions.color(flash, ScoreFeel.CARD_POP_UP)),
                Actions.parallel(
                        Actions.scaleTo(1f, 1f, ScoreFeel.CARD_POP, Interpolation.swingOut),
                        Actions.color(Color.WHITE, ScoreFeel.CARD_POP))));
        touched.add(power);
    }

    private void popupAtTally(ScoreTally tally, String text, String colorKey) {
        popupAt(tally, text, colorKey);
    }

    private void popupAt(Actor source, String text, String colorKey) {
        if (text == null || text.isBlank()) {
            return;
        }
        Label label = Ui.label(skin, text, "body");
        label.setColor(colorFor(colorKey));
        label.pack();
        Vector2 center = centerOf(source);
        label.setPosition(center.x - label.getWidth() / 2f, center.y);
        table.addActor(label);
        label.toFront();
        label.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.moveBy(0f, 36f, ScoreFeel.POPUP_RISE, Interpolation.pow2Out),
                        Actions.sequence(Actions.delay(ScoreFeel.POPUP_RISE * 0.35f),
                                Actions.fadeOut(ScoreFeel.POPUP_RISE * 0.65f))),
                Actions.removeActor()));
        transientActors.add(label);
    }

    private void spawnTrailParticles(ScoreTally tally) {
        Vector2 target = centerOf(tally.scoreLabel());
        Vector2 origin = centerOf(tally);
        for (int i = 0; i < 10; i++) {
            Image particle = new Image(new TextureRegionDrawable(assets.region("white")));
            particle.setColor(i % 2 == 0 ? Palette.CHIPS : Palette.GOLD);
            float size = MathUtils.random(4f, 7f);
            particle.setSize(size, size);
            particle.setPosition(origin.x + MathUtils.random(-36f, 36f),
                    origin.y + MathUtils.random(-18f, 18f));
            table.addActor(particle);
            particle.addAction(Actions.sequence(
                    Actions.parallel(
                            Actions.moveTo(target.x, target.y, ScoreFeel.FINAL_COUNT,
                                    Interpolation.pow2In),
                            Actions.fadeOut(ScoreFeel.FINAL_COUNT)),
                    Actions.removeActor()));
            transientActors.add(particle);
        }
    }

    private Vector2 centerOf(Actor actor) {
        Vector2 position = new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f);
        actor.localToAscendantCoordinates(table, position);
        return position;
    }

    private void schedule(float delay, Runnable action) {
        table.addAction(Actions.sequence(Actions.delay(Math.max(0f, delay)),
                Actions.run(action)));
    }

    @Override
    public void finishNow() {
        resetAnimations();
        applyFinalResult(ownResult, true);
        applyFinalResult(opponentResult, false);
        running = false;
        complete();
    }

    private void applyFinalResult(ScoreResult result, boolean mine) {
        ScoreTally tally = table.tallyView(mine);
        if (result == null || result.steps().isEmpty()) {
            tally.clearValues();
            return;
        }
        tally.setHand(result.type().displayName());
        tally.setValues(result.chips(), result.mult(), result.finalScore(), true);
    }

    private void resetAnimations() {
        table.clearActions();
        for (Actor actor : touched) {
            actor.clearActions();
            actor.setScale(1f);
            actor.setRotation(0f);
            actor.setColor(Color.WHITE);
        }
        touched.clear();
        for (Actor actor : transientActors) {
            actor.remove();
        }
        transientActors.clear();
    }

    private void complete() {
        if (!running && onFinished == null) {
            return;
        }
        running = false;
        Runnable callback = onFinished;
        onFinished = null;
        if (callback != null) {
            callback.run();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Color colorFor(String key) {
        return switch (key == null ? "neutral" : key) {
            case "chips" -> Palette.CHIPS;
            case "mult" -> Palette.MULT;
            default -> Palette.GOLD;
        };
    }

    /** Interpola um inteiro no rótulo (fichas rolando e contagem final). */
    private static final class IntCountAction extends TemporalAction {

        private final int from;
        private final int to;
        private final IntConsumer setter;

        IntCountAction(int from, int to, float duration, IntConsumer setter) {
            super(duration);
            this.from = from;
            this.to = to;
            this.setter = setter;
        }

        IntCountAction easeOut() {
            setInterpolation(Interpolation.pow2Out);
            return this;
        }

        @Override
        protected void begin() {
            setter.accept(from);
        }

        @Override
        protected void update(float percent) {
            setter.accept(Math.round(from + (to - from) * percent));
        }

        @Override
        protected void end() {
            setter.accept(to);
        }
    }
}
