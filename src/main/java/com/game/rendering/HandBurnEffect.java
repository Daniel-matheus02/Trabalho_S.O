package com.game.rendering;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.game.assets.GameAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Transição da mão: gira as cartas restantes, queima cada uma de ruído com borda incandescente e
 * então deixa a mão de poderes ser distribuída. As cartas jogadas já saíram da mão (foram para a
 * mesa); só as remanescentes são capturadas e queimadas.
 */
public final class HandBurnEffect extends Actor {

    private static final float SPIN_DURATION = 0.7f;
    private static final float SPIN_STAGGER = 0.05f;
    private static final float SPIN_LIFT = 9f;
    private static final float BURN_START = 0.85f;
    private static final float BURN_DURATION = 0.9f;
    private static final float BURN_STAGGER = 0.08f;
    private static final float EMBER_TAIL = 0.4f;

    private final GameAssets assets;
    private final Stage stage;
    private final CardHand hand;
    private final PlayerPanel opponentPanel;
    private final IntConsumer shakeScreen;

    private final List<BurnCard> ownCards = new ArrayList<>();
    private final List<BurnCard> opponentCards = new ArrayList<>();
    private final List<Actor> hiddenSources = new ArrayList<>();
    private final Particles embers;
    private final Particles smoke;

    private Runnable onFinished;
    private boolean running;
    private boolean dealt;
    private boolean shaken;
    private float time;
    private float emberTimer;

    public HandBurnEffect(GameAssets assets, Stage stage, CardHand hand,
                          PlayerPanel opponentPanel, IntConsumer shakeScreen) {
        this.assets = assets;
        this.stage = stage;
        this.hand = hand;
        this.opponentPanel = opponentPanel;
        this.shakeScreen = shakeScreen;
        this.embers = new Particles(assets, true);
        this.smoke = new Particles(assets, false);
        setBounds(0f, 0f, Math.max(1f, stage.getWidth()), Math.max(1f, stage.getHeight()));
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    /** Inicia a transição; {@code onFinished} é chamado quando a mão de poderes deve entrar. */
    public void play(Runnable onFinished) {
        if (running) {
            finishNow();
        }
        restoreSources();
        cleanupSnapshots();
        this.onFinished = onFinished;
        ownCards.clear();
        opponentCards.clear();
        hiddenSources.clear();
        embers.clear();
        smoke.clear();
        time = 0f;
        emberTimer = 0f;
        shaken = false;
        dealt = false;
        running = true;

        for (CardActor actor : hand.actorsSnapshot()) {
            if (actor.card() == null || !actor.isVisible()) {
                continue;
            }
            addCard(actor, false, ownCards);
        }
        for (CardActor actor : opponentPanel.backActors()) {
            if (!actor.isVisible()) {
                continue;
            }
            addCard(actor, true, opponentCards);
        }
        int max = Math.max(ownCards.size(), opponentCards.size());
        for (int i = 0; i < max; i++) {
            if (i < ownCards.size()) {
                ownCards.get(i).index = i;
            }
            if (i < opponentCards.size()) {
                opponentCards.get(i).index = i;
            }
        }

        if (ownCards.isEmpty() && opponentCards.isEmpty()) {
            running = false;
            Runnable callback = onFinished;
            this.onFinished = null;
            if (callback != null) {
                callback.run();
            }
            return;
        }

        setBounds(0f, 0f, Math.max(1f, stage.getWidth()), Math.max(1f, stage.getHeight()));
        if (getStage() == null) {
            stage.addActor(this);
        }
        toFront();
    }

    private void addCard(CardActor actor, boolean faceDown, List<BurnCard> destination) {
        float width = actor.getWidth() > 1f ? actor.getWidth() : 90f;
        float height = actor.getHeight() > 1f ? actor.getHeight() : 124f;
        CardSnapshot snapshot = CardSnapshot.capture(assets, actor.card(), faceDown, width, height);
        Vector2 position = actor.localToStageCoordinates(new Vector2(0f, 0f));
        BurnCard card = new BurnCard(snapshot, position.x, position.y, width, height);
        destination.add(card);
        actor.setVisible(false);
        hiddenSources.add(actor);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!running) {
            return;
        }
        time += delta;
        embers.update(delta);
        smoke.update(delta);

        if (!shaken && time >= BURN_START) {
            shaken = true;
            if (shakeScreen != null) {
                shakeScreen.accept(1);
            }
        }
        if (time >= BURN_START) {
            emberTimer -= delta;
            if (emberTimer <= 0f) {
                emberTimer = 0.04f;
                spawnEmbers(ownCards);
                spawnEmbers(opponentCards);
            }
        }

        float burnEnd = burnEnd();
        if (!dealt && time >= burnEnd) {
            dealt = true;
            spawnMist();
            Runnable callback = onFinished;
            onFinished = null;
            if (callback != null) {
                callback.run();
            }
        }
        if (time >= burnEnd + EMBER_TAIL) {
            finishNow();
        }
    }

    private void spawnEmbers(List<BurnCard> cards) {
        for (BurnCard card : cards) {
            float progress = burnProgress(card);
            if (progress <= 0f || progress >= 1f) {
                continue;
            }
            embers.fire(card.x + card.width / 2f, card.y + card.height * 0.55f,
                    card.width * 0.42f, card.height * 0.32f, 2);
        }
    }

    private void spawnMist() {
        float x = ownCards.isEmpty()
                ? getWidth() * 0.5f : ownCards.get(0).x + ownCards.get(0).width * 2f;
        float y = ownCards.isEmpty() ? getHeight() * 0.25f : ownCards.get(0).y + 16f;
        smoke.smoke(x, y, 150f, 40f, 22);
        embers.fire(x, y, 110f, 26f, 12);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!running) {
            return;
        }
        batch.setColor(Color.WHITE);
        drawCards(batch, opponentCards);
        drawCards(batch, ownCards);
        embers.draw(batch, parentAlpha);
        smoke.draw(batch, parentAlpha);
    }

    private void drawCards(Batch batch, List<BurnCard> cards) {
        for (BurnCard card : cards) {
            float progress = burnProgress(card);
            if (progress >= 1f) {
                continue;
            }
            float spin = spinAngle(card);
            float lift = spinLift(card);
            float originX = card.width / 2f;
            float originY = card.height / 2f;
            if (progress <= 0f) {
                batch.setColor(Color.WHITE);
                batch.draw(card.snapshot.region(), card.x, card.y + lift,
                        originX, originY, card.width, card.height, 1f, 1f, spin);
            } else {
                DissolveShader.draw(batch, card.snapshot.region(), card.x, card.y + lift,
                        originX, originY, card.width, card.height, spin, progress, 1f);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private float spinAngle(BurnCard card) {
        float progress = MathUtils.clamp(
                (time - card.index * SPIN_STAGGER) / SPIN_DURATION, 0f, 1f);
        return 360f * progress;
    }

    private float spinLift(BurnCard card) {
        float progress = MathUtils.clamp(
                (time - card.index * SPIN_STAGGER) / SPIN_DURATION, 0f, 1f);
        return MathUtils.sin(progress * MathUtils.PI) * SPIN_LIFT;
    }

    private float burnProgress(BurnCard card) {
        float start = BURN_START + card.index * BURN_STAGGER;
        return MathUtils.clamp((time - start) / BURN_DURATION, 0f, 1f);
    }

    private float burnEnd() {
        int max = Math.max(ownCards.size(), opponentCards.size());
        return BURN_START + Math.max(0, max - 1) * BURN_STAGGER + BURN_DURATION;
    }

    /** Clique/SPACE: conclui a transição e entrega a mão de poderes imediatamente. */
    public void finishNow() {
        if (!running && !dealt) {
            return;
        }
        boolean callbackPending = !dealt;
        dealt = true;
        running = false;
        restoreSources();
        cleanupSnapshots();
        embers.clear();
        smoke.clear();
        if (getStage() != null) {
            remove();
        }
        Runnable callback = onFinished;
        onFinished = null;
        if (callbackPending && callback != null) {
            callback.run();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void dispose() {
        running = false;
        restoreSources();
        cleanupSnapshots();
        embers.clear();
        smoke.clear();
        if (getStage() != null) {
            remove();
        }
    }

    private void restoreSources() {
        for (Actor actor : hiddenSources) {
            actor.setVisible(true);
        }
        hiddenSources.clear();
    }

    private void cleanupSnapshots() {
        for (BurnCard card : ownCards) {
            card.snapshot.dispose();
        }
        for (BurnCard card : opponentCards) {
            card.snapshot.dispose();
        }
        ownCards.clear();
        opponentCards.clear();
    }

    private static final class BurnCard {
        private final CardSnapshot snapshot;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private int index;

        BurnCard(CardSnapshot snapshot, float x, float y, float width, float height) {
            this.snapshot = snapshot;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
