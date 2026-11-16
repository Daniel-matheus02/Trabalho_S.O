package com.game.ai;

import com.game.core.model.Card;
import com.game.core.model.Deck;
import com.game.core.model.HandEvaluator;
import com.game.core.model.HandType;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.model.Suit;
import com.game.core.scoring.ScoreModifiers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Oponente controlado pelo computador (CPU). Ele avalia toda seleção legal com o mesmo
 * {@link ScoringEngine} usado pelos humanos e escolhe a mais forte, portanto nunca pode "trapacear"
 * com cálculos próprios.
 */
public final class BotBrain {

    /** Sementes fixas para as sabotagens aleatórias simuladas (Troca de Carta). */
    private static final int RANDOM_SAMPLES = 8;
    private static final long POWER_SEED = 0x50EEDL;

    private final ScoringEngine scoringEngine;
    private final int maxPlay;

    public BotBrain(ScoringEngine scoringEngine, int maxPlay) {
        this.scoringEngine = scoringEngine;
        this.maxPlay = maxPlay;
    }

    /** Escolhe a melhor jogada legal entre as cartas da mão. */
    public List<Card> choosePlay(List<Card> hand, String globalModifierId) {
        if (hand == null || hand.isEmpty()) {
            return List.of();
        }
        List<Card> best = null;
        ScoreResult bestScore = null;
        int limit = Math.min(maxPlay, hand.size());
        for (List<Card> combo : HandEvaluator.combinations(hand, limit)) {
            ScoreResult score = scoringEngine.score(combo, List.of(), globalModifierId);
            if (bestScore == null || isBetter(score, combo.size(), bestScore, best.size())) {
                bestScore = score;
                best = combo;
            }
        }
        return best == null ? List.of(hand.get(0)) : best;
    }

    /**
     * Escolhe os poderes da rodada a partir do instantâneo já filtrado do próprio jogador.
     *
     * <p>Enumera todos os subconjuntos de tamanho 0 até {@code maxPowersPlayed} da mão de poderes
     * (no máximo 22), simula cada um contra a mesa visível do oponente e escolhe o que maximiza a
     * diferença entre a pontuação própria e a do oponente. Não escolhe nada quando nenhum
     * subconjunto melhora a diferença base.</p>
     */
    public List<Integer> choosePowers(GameSnapshot state, int playerId) {
        if (state == null) {
            return List.of();
        }
        PlayerView me = state.player(playerId);
        PlayerView opponent = state.opponentOf(playerId);
        List<PowerCard> hand = me.powerHand();
        List<Card> ownCards = me.playedCards();
        List<Card> opponentCards = opponent.playedCards();
        if (hand.isEmpty() || ownCards.isEmpty()) {
            return List.of();
        }

        int max = Math.min(state.rules().maxPowersPlayed(), hand.size());
        Random random = new Random(POWER_SEED + 31L * state.round() + playerId);
        int baseline = ownScore(ownCards, List.of(), state.globalModifierId())
                - opponentScore(opponentCards, ownCards,
                state.globalModifierId(), ScoreModifiers.NONE);

        int bestValue = baseline;
        List<Integer> best = List.of();
        for (List<Integer> subset : subsets(hand.size(), max)) {
            if (subset.isEmpty()) {
                continue;
            }
            int value = evaluate(hand, subset, ownCards, opponentCards,
                    state.globalModifierId(), random);
            if (value > bestValue) {
                bestValue = value;
                best = subset;
            }
        }
        return best;
    }

    private int evaluate(List<PowerCard> hand, List<Integer> subset,
                         List<Card> ownCards, List<Card> opponentCards,
                         String globalModifierId, Random random) {
        boolean randomSabotage = false;
        for (int index : subset) {
            if (hand.get(index).kind() == PowerKind.SABOTAGE
                    && "troca_carta".equals(hand.get(index).id())) {
                randomSabotage = true;
            }
        }
        if (!randomSabotage) {
            return evaluateOnce(hand, subset, ownCards, opponentCards,
                    globalModifierId, random);
        }
        int total = 0;
        for (int sample = 0; sample < RANDOM_SAMPLES; sample++) {
            total += evaluateOnce(hand, subset, ownCards, opponentCards,
                    globalModifierId, random);
        }
        return Math.round(total / (float) RANDOM_SAMPLES);
    }

    /** Uma simulação completa: aplica as sabotagens do subconjunto e pontua os dois lados. */
    private int evaluateOnce(List<PowerCard> hand, List<Integer> subset,
                             List<Card> ownCards, List<Card> opponentCards,
                             String globalModifierId, Random random) {
        List<String> jokers = new ArrayList<>();
        ScoreModifiers modifiers = ScoreModifiers.NONE;
        List<Card> opponentFinal = opponentCards;

        for (int index : subset) {
            PowerCard card = hand.get(index);
            if (card.kind() == PowerKind.JOKER) {
                jokers.add(card.id());
                continue;
            }
            switch (card.id()) {
                case "anti_flush" -> modifiers = modifiers.withBlockFlush();
                case "peso_chumbo" -> modifiers = modifiers.withMultPenalty(5);
                case "imposto_par" -> modifiers = modifiers.withPairTax();
                case "nevoa" -> modifiers = modifiers.withNullifyJokers();
                case "virada_naipes" -> opponentFinal = mirrorSuits(opponentFinal);
                case "troca_carta" -> opponentFinal = swapRandomCard(
                        opponentFinal, ownCards, random);
                default -> {
                    // Sabotagem desconhecida: não faz nada.
                }
            }
        }

        int own = ownScore(ownCards, jokers, globalModifierId);
        int opponent = opponentScore(opponentFinal, ownCards, globalModifierId, modifiers);
        return own - opponent;
    }

    private int ownScore(List<Card> cards, List<String> jokers, String globalModifierId) {
        return scoringEngine.score(cards, jokers, globalModifierId).finalScore();
    }

    private int opponentScore(List<Card> cards, List<Card> ownCards,
                              String globalModifierId, ScoreModifiers modifiers) {
        return scoringEngine.score(cards, List.of(), globalModifierId, modifiers).finalScore();
    }

    private static List<Card> mirrorSuits(List<Card> cards) {
        List<Card> flipped = new ArrayList<>(cards.size());
        for (Card card : cards) {
            flipped.add(new Card(card.rank(), switch (card.suit()) {
                case SPADES -> Suit.HEARTS;
                case HEARTS -> Suit.SPADES;
                case DIAMONDS -> Suit.CLUBS;
                case CLUBS -> Suit.DIAMONDS;
            }));
        }
        return flipped;
    }

    /** Substitui uma carta aleatória por outra do baralho restante, como a sabotagem real. */
    private static List<Card> swapRandomCard(List<Card> cards, List<Card> ownCards, Random random) {
        if (cards.isEmpty()) {
            return cards;
        }
        List<Card> updated = new ArrayList<>(cards);
        int index = random.nextInt(updated.size());
        List<Card> known = new ArrayList<>(cards);
        known.addAll(ownCards);
        Card replacement = null;
        for (Card candidate : Deck.shuffled(random)) {
            if (!known.contains(candidate)) {
                replacement = candidate;
                break;
            }
        }
        if (replacement == null) {
            replacement = Deck.shuffled(random).get(0);
        }
        updated.set(index, replacement);
        return updated;
    }

    /** Todos os subconjuntos de índices com tamanho entre 0 e {@code max}. */
    static List<List<Integer>> subsets(int size, int max) {
        List<List<Integer>> result = new ArrayList<>();
        collectSubsets(0, size, max, new ArrayList<>(), result);
        return result;
    }

    private static void collectSubsets(int start, int size, int max,
                                       List<Integer> current, List<List<Integer>> result) {
        if (!current.isEmpty()) {
            result.add(List.copyOf(current));
        }
        if (current.size() == max) {
            return;
        }
        for (int i = start; i < size; i++) {
            current.add(i);
            collectSubsets(i + 1, size, max, current, result);
            current.remove(current.size() - 1);
        }
    }

    private boolean isBetter(ScoreResult candidate, int candidateCards, ScoreResult current, int currentCards) {
        if (candidate.finalScore() != current.finalScore()) {
            return candidate.finalScore() > current.finalScore();
        }
        HandType candidateType = candidate.type();
        HandType currentType = current.type();
        if (candidateType.ordinal() != currentType.ordinal()) {
            return candidateType.ordinal() > currentType.ordinal();
        }
        return candidateCards < currentCards;
    }

    /** Todas as combinações de {@code size} cartas de {@code hand}. */
    static List<List<Card>> combinations(List<Card> hand, int size) {
        return HandEvaluator.combinations(hand, size);
    }
}
