package com.game.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Avalia a categoria de pôquer de uma seleção de cartas e escolhe a melhor mão de cinco. */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static HandType evaluate(List<Card> cards) {
        return evaluate(cards, true);
    }

    /**
     * Avalia uma seleção de até cinco cartas. Com {@code flushAllowed = false}, um flush não conta
     * (usado por sabotagens anti-flush); a mão é reclassificada apenas pelas repetições/sequência.
     */
    public static HandType evaluate(List<Card> cards, boolean flushAllowed) {
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

        boolean flush = flushAllowed && size == 5
                && new HashSet<>(cards.stream().map(Card::suit).toList()).size() == 1;
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

    /** Escolhe a melhor combinação de cinco cartas de um pool de até sete. */
    public static List<Card> bestFive(List<Card> pool) {
        return bestFive(pool, true);
    }

    /**
     * Escolhe a melhor combinação de cinco cartas. Com {@code flushAllowed = false}, combinações
     * de flush são descartadas quando existe alguma alternativa; se o pool inteiro for do mesmo
     * naipe, a melhor combinação é reclassificada sem flush.
     */
    public static List<Card> bestFive(List<Card> pool, boolean flushAllowed) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        if (pool.size() <= 5) {
            return List.copyOf(pool);
        }

        List<Card> best = null;
        HandType bestType = null;
        int bestCardChips = Integer.MIN_VALUE;
        for (List<Card> combo : combinations(pool, 5)) {
            if (!flushAllowed && isFlush(combo)) {
                continue;
            }
            HandType type = evaluate(combo, flushAllowed);
            int chips = chipSum(combo);
            if (best == null || type.ordinal() > bestType.ordinal()
                    || (type.ordinal() == bestType.ordinal() && chips > bestCardChips)) {
                best = combo;
                bestType = type;
                bestCardChips = chips;
            }
        }
        if (best == null) {
            // Pool inteiro do mesmo naipe: não há como evitar o flush, então reclassifique.
            for (List<Card> combo : combinations(pool, 5)) {
                HandType type = evaluate(combo, false);
                int chips = chipSum(combo);
                if (best == null || type.ordinal() > bestType.ordinal()
                        || (type.ordinal() == bestType.ordinal() && chips > bestCardChips)) {
                    best = combo;
                    bestType = type;
                    bestCardChips = chips;
                }
            }
        }
        return List.copyOf(best);
    }

    private static boolean isFlush(List<Card> cards) {
        return cards.size() == 5
                && new HashSet<>(cards.stream().map(Card::suit).toList()).size() == 1;
    }

    private static int chipSum(List<Card> cards) {
        int sum = 0;
        for (Card card : cards) {
            sum += card.rank().chipValue();
        }
        return sum;
    }

    /** Todas as combinações de {@code size} cartas do pool. */
    public static List<List<Card>> combinations(List<Card> cards, int size) {
        List<List<Card>> result = new ArrayList<>();
        collect(cards, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void collect(List<Card> cards, int remaining, int start,
                                List<Card> current, List<List<Card>> result) {
        if (remaining == 0) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = start; i <= cards.size() - remaining; i++) {
            current.add(cards.get(i));
            collect(cards, remaining - 1, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
