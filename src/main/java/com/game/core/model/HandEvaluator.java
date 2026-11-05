package com.game.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Avalia a categoria de pôquer de uma seleção de uma a cinco cartas. */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static HandType evaluate(List<Card> cards) {
        int size = cards.size();
        if (size == 0) {
            return HandType.HIGH_CARD;
        }

        Map<Rank, Integer> frequency = new TreeMap<>();
        for (Card card : cards) {
            frequency.merge(card.rank(), 1, Integer::sum);
        }
        int[] counts = frequency.values().stream()
                .sorted((a, b) -> Integer.compare(b, a))
                .mapToInt(Integer::intValue)
                .toArray();

        boolean flush = size == 5 && new HashSet<>(cards.stream().map(Card::suit).toList()).size() == 1;
        boolean straight = isStraight(cards);

        if (flush && straight) {
            return HandType.STRAIGHT_FLUSH;
        }
        if (counts[0] == 4) {
            return HandType.FOUR_OF_A_KIND;
        }
        if (counts[0] == 3 && counts.length > 1 && counts[1] == 2) {
            return HandType.FULL_HOUSE;
        }
        if (flush) {
            return HandType.FLUSH;
        }
        if (straight) {
            return HandType.STRAIGHT;
        }
        if (counts[0] == 3) {
            return HandType.THREE_OF_A_KIND;
        }
        if (counts[0] == 2 && counts.length > 1 && counts[1] == 2) {
            return HandType.TWO_PAIR;
        }
        if (counts[0] == 2) {
            return HandType.PAIR;
        }
        return HandType.HIGH_CARD;
    }

    /** Uma sequência exige exatamente cinco cartas; A-2-3-4-5 (a roda) também conta. */
    public static boolean isStraight(List<Card> cards) {
        if (cards.size() != 5) {
            return false;
        }
        Set<Integer> values = new HashSet<>();
        for (Card card : cards) {
            values.add(card.rank().value());
        }
        if (values.size() != 5) {
            return false;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        if (sorted.get(4) - sorted.get(0) == 4) {
            return true;
        }
        return sorted.equals(Arrays.asList(2, 3, 4, 5, 14));
    }
}
