package com.game.rendering;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.RotateToAction;
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.game.assets.GameAssets;

/**
 * Comportamento comum das cartas desenhadas à mão: hover, elevação de seleção, distribuição,
 * virada e partículas. {@link CardActor} e {@link PowerCardActor} só implementam o desenho.
 */
public abstract class HandCardActor extends Actor {

    /** Duração da transição leque → reta (hover/seleção), como o :hover do CSS de referência. */
    public static final float STRAIGHTEN_DURATION = 0.16f;
    /** Inclinação entre cartas vizinhas do leque (graus, no sentido do CSS). */
    public static final float FAN_TILT_STEP = 2.6f;
    /** Limite de inclinação das cartas das pontas. */
    public static final float FAN_MAX_TILT = 14f;

    /** Callback de hover compartilhado pelo tooltip global. */
    public interface HoverListener {
        void onHoverEnter(HandCardActor actor);

        void onHoverExit(HandCardActor actor);
    }

    protected final GameAssets assets;
    protected final boolean faceDown;
    protected final Particles sparkles;

    protected boolean selected;
    protected boolean interactive;
    protected boolean hoverable;
    protected boolean disabled;
    protected boolean hovered;
    protected float hoverLocalX;
    protected float lift;
    /** Deslocamento vertical temporário da subida da nevoa (nunca muda a posicao do leiaute). */
    protected float dealRise;
    protected float appear = 0.25f;
    protected float glowPulse;
    protected float reveal = 1f;
    protected float revealDelay;
    protected float revealSpeed = 3f;
    protected boolean revealing;
    protected boolean dealMotion = true;
    protected boolean sparkled = true;

    /** Ângulo base do leque; hover/seleção animam de volta para 0 e depois para cá. */
    private float fanAngle;
    private RotateToAction rotationAction;

    private HoverListener hoverListener;

    protected HandCardActor(GameAssets assets, boolean faceDown, Runnable onClick) {
        this.assets = assets;
        this.faceDown = faceDown;
        this.interactive = onClick != null;
        this.sparkles = new Particles(assets, true);
        setTouchable(this.interactive ? Touchable.enabled : Touchable.disabled);
        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (isHoverable()) {
                    hovered = true;
                    hoverLocalX = x;
                    // No leque, a carta sob o cursor precisa ficar visível por inteiro.
                    toFront();
                    onVisualStateChanged();
                    if (hoverListener != null) {
                        hoverListener.onHoverEnter(HandCardActor.this);
                    }
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                boolean wasHovered = hovered;
                hovered = false;
                if (wasHovered) {
                    onVisualStateChanged();
                }
                if (wasHovered && hoverListener != null) {
                    hoverListener.onHoverExit(HandCardActor.this);
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (isClickable() && onClick != null) {
                    onClick.run();
                }
            }
        });
    }

    private boolean isClickable() {
        return interactive && !disabled;
    }

    protected boolean isHoverable() {
        return (interactive || hoverable) && !disabled;
    }

    public void setHoverable(boolean hoverable) {
        this.hoverable = hoverable;
        updateTouchable();
    }

    public void setHoverListener(HoverListener hoverListener) {
        this.hoverListener = hoverListener;
    }

    /** QA/DevPreview: injeta hover determinístico sem depender de eventos do mouse. */
    public void demoHover(float localX, float localY) {
        if (!isHoverable()) {
            hoverable = true;
            updateTouchable();
        }
        hovered = true;
        hoverLocalX = localX;
        onVisualStateChanged();
        if (hoverListener != null) {
            hoverListener.onHoverEnter(this);
        }
    }

    public void demoUnhover() {
        boolean wasHovered = hovered;
        hovered = false;
        if (wasHovered) {
            onVisualStateChanged();
        }
        if (wasHovered && hoverListener != null) {
            hoverListener.onHoverExit(this);
        }
    }

    /**
     * Ângulo de leque (no palco do libGDX) para a carta {@code index} de uma fileira de
     * {@code count} cartas. O sinal é invertido em relação ao CSS, pois no libGDX a rotação
     * positiva é anti-horária.
     */
    public static float fanAngleAt(int index, int count) {
        if (count <= 1) {
            return 0f;
        }
        float offset = index - (count - 1) / 2f;
        float tilt = MathUtils.clamp(offset * FAN_TILT_STEP, -FAN_MAX_TILT, FAN_MAX_TILT);
        return -tilt;
    }

    /** Maior inclinação usada por uma fileira de {@code count} cartas. */
    public static float maxFanTilt(int count) {
        return Math.min(FAN_MAX_TILT, Math.abs(count - 1) / 2f * FAN_TILT_STEP);
    }

    /** Ângulo base de repouso no leque (em graus). */
    public float fanAngle() {
        return fanAngle;
    }

    /**
     * Define o ângulo de leque da carta. Durante o leiaute a rotação é aplicada imediatamente, mas
     * uma carta sob o cursor ou selecionada continua reta até o hover/seleção terminar.
     */
    public void setFanAngle(float degrees) {
        this.fanAngle = degrees;
        cancelRotationAction();
        setRotation(isStraightened() ? 0f : degrees);
    }

    /** Endireita a carta enquanto ela estiver sob o cursor ou selecionada. */
    protected void onVisualStateChanged() {
        float target = isStraightened() ? 0f : fanAngle;
        cancelRotationAction();
        if (Math.abs(getRotation() - target) < 0.01f) {
            setRotation(target);
            return;
        }
        rotationAction = Actions.rotateTo(target, STRAIGHTEN_DURATION, Interpolation.pow2Out);
        addAction(rotationAction);
    }

    private boolean isStraightened() {
        return hovered || selected;
    }

    private void cancelRotationAction() {
        if (rotationAction != null) {
            removeAction(rotationAction);
            rotationAction = null;
        }
    }

    private void updateTouchable() {
        setTouchable((interactive || hoverable) ? Touchable.enabled : Touchable.disabled);
    }

    public void setSelected(boolean selected) {
        boolean becameSelected = selected && !this.selected;
        boolean changed = selected != this.selected;
        this.selected = selected;
        if (becameSelected && getWidth() > 0f) {
            sparkles.sparkles(getX() + getWidth() / 2f, getY() + getHeight() * 0.58f,
                    getWidth() * 0.55f, 9);
        }
        if (changed) {
            onVisualStateChanged();
        }
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        updateTouchable();
    }

    /** Inicia a revelação da carta (verso → frente), com atraso e duração em segundos. */
    public void startDeal(float delay, float duration) {
        reveal = 0f;
        revealDelay = Math.max(0f, delay);
        revealSpeed = 1f / Math.max(0.08f, duration);
        revealing = true;
        sparkled = false;
        dealMotion = true;
    }

    /** Vira a carta na mesa (só o giro vertical, sem salto nem queda). */
    public void startFlip(float delay, float duration) {
        reveal = 0f;
        revealDelay = Math.max(0f, delay);
        revealSpeed = 1f / Math.max(0.08f, duration);
        revealing = true;
        sparkled = false;
        dealMotion = false;
    }

    /**
     * Emerge de baixo: a carta começa {@code rise} pixels abaixo do lugar final e sobe até ele.
     * O deslocamento é aplicado no desenho, então o leiaute nunca briga com a animação.
     */
    public void startRise(float delay, float duration) {
        dealRise = RISE_PIXELS;
        addAction(Actions.sequence(Actions.delay(Math.max(0f, delay)),
                new RiseAction(Math.max(0.01f, duration))));
    }

    /** Pixels que a carta percorre ao emergir da nevoa. */
    public static final float RISE_PIXELS = 40f;

    private final class RiseAction extends TemporalAction {

        RiseAction(float duration) {
            setDuration(duration);
            setInterpolation(Interpolation.swingOut);
        }

        @Override
        protected void update(float percent) {
            dealRise = RISE_PIXELS * (1f - percent);
        }
    }

    /** Mostra a carta sem animação de distribuição. */
    public void showInstantly() {
        reveal = 1f;
        revealDelay = 0f;
        revealing = false;
        dealMotion = false;
        sparkled = true;
        appear = 1f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float targetLift = selected ? 16f : (hovered && isHoverable() ? 7f : 0f);
        lift = MathUtils.lerp(lift, targetLift, Math.min(1f, delta * 14f));
        appear = MathUtils.lerp(appear, 1f, Math.min(1f, delta * 7f));
        glowPulse += delta;

        if (revealing) {
            if (revealDelay > 0f) {
                revealDelay -= delta;
            } else {
                reveal = Math.min(1f, reveal + delta * revealSpeed);
                if (reveal >= 1f) {
                    revealing = false;
                }
            }
            if (!sparkled && reveal >= 0.55f && getWidth() > 0f) {
                sparkled = true;
                int count = MathUtils.clamp((int) (getWidth() / 11f), 5, 12);
                sparkles.sparkles(getX() + getWidth() / 2f, getY() + getHeight() * 0.55f,
                        getWidth() * 0.5f, count);
            }
        }
        sparkles.update(delta);
    }
}
