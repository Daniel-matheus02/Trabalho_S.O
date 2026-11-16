package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandType;

import java.util.ArrayList;
import java.util.List;

/**
 * Acumulador mutável entregue aos modificadores globais e aos curingas durante a pontuação de
 * uma jogada. Além dos totais e das linhas legíveis, registra um {@link ScoreStep} por fonte:
 * o motor define a fonte atual antes de cada efeito e os mutadores acumulam os deltas.
 */
public final class ScoreContext {

    private final List<Card> cards;
    private final HandType type;
    private final List<ScoreLine> lines = new ArrayList<>();
    private final List<ScoreStep> steps = new ArrayList<>();

    private int chips;
    private int mult;

    private boolean stepOpen;
    private StepKind stepKind;
    private int stepCardIndex;
    private String stepSourceId;
    private String stepLabel = "";
    private int pendingChipsDelta;
    private int pendingMultDelta;
    private double pendingMultFactor = 1.0;

    ScoreContext(List<Card> cards, HandType type, int chips, int mult) {
        this.cards = List.copyOf(cards);
        this.type = type;
        this.chips = chips;
        this.mult = mult;
    }

    public List<Card> cards() {
        return cards;
    }

    public HandType type() {
        return type;
    }

    public int chips() {
        return chips;
    }

    public int mult() {
        return mult;
    }

    /** Abre uma fonte de pontuação; os próximos mutadores acumulam nela até {@link #commitStep()}. */
    public void beginStep(StepKind kind, int cardIndex, String sourceId, String label) {
        if (stepOpen) {
            commitStep();
        }
        stepOpen = true;
        stepKind = kind;
        stepCardIndex = cardIndex;
        stepSourceId = sourceId;
        stepLabel = label == null ? "" : label;
        pendingChipsDelta = 0;
        pendingMultDelta = 0;
        pendingMultFactor = 1.0;
    }

    /** Fecha a fonte atual e registra um passo com os totais acumulados. */
    public void commitStep() {
        if (!stepOpen) {
            return;
        }
        steps.add(new ScoreStep(stepKind, stepCardIndex, stepSourceId, pendingChipsDelta,
                pendingMultDelta, pendingMultFactor, chips, mult, stepLabel));
        stepOpen = false;
        pendingChipsDelta = 0;
        pendingMultDelta = 0;
        pendingMultFactor = 1.0;
    }

    public void addChips(int amount) {
        chips = Math.max(0, chips + amount);
        if (stepOpen) {
            pendingChipsDelta += amount;
        }
    }

    public void addMult(int amount) {
        mult = Math.max(0, mult + amount);
        if (stepOpen) {
            pendingMultDelta += amount;
        }
    }

    public void multiplyMult(int factor) {
        int previous = mult;
        mult = Math.max(0, mult * factor);
        if (stepOpen) {
            pendingMultDelta += mult - previous;
            pendingMultFactor *= factor;
        }
    }

    public void addLine(ScoreLine line) {
        lines.add(line);
    }

    public void addPlusLine(String text) {
        lines.add(ScoreLine.plus(text));
    }

    public List<ScoreLine> lines() {
        return List.copyOf(lines);
    }

    public List<ScoreStep> steps() {
        commitStep();
        return List.copyOf(steps);
    }

    public void addMultLine(String text) {
        lines.add(ScoreLine.mult(text));
    }

    public int countSuit(com.game.core.model.Suit suit) {
        int count = 0;
        for (Card card : cards) {
            if (card.suit() == suit) {
                count++;
            }
        }
        return count;
    }

    public boolean typeAtLeast(HandType other) {
        return type.atLeast(other);
    }
}
