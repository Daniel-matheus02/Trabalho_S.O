package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Acumulador mutável entregue aos modificadores globais e aos curingas durante a pontuação de uma jogada.
 * Espelha o objeto {@code c} da implementação de referência.
 */
public final class ScoreContext {

    private final List<Card> cards;
    private final HandType type;
    private final List<ScoreLine> lines = new ArrayList<>();
    private int chips;
    private int mult;

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

    public void addChips(int amount) {
        chips += amount;
    }

    public void addMult(int amount) {
        mult += amount;
    }

    public void multiplyMult(int factor) {
        mult *= factor;
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

    public int cardCount() {
        return cards.size();
    }

    public boolean typeAtLeast(HandType other) {
        return type.atLeast(other);
    }
}
