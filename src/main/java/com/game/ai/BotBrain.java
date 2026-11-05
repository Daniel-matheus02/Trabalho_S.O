package com.game.ai;

import com.game.core.model.Card;
import com.game.core.model.HandType;
import com.game.core.scoring.Jokers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Oponente controlado pelo computador (CPU). Ele avalia toda seleção legal com o mesmo
 * {@link ScoringEngine} usado pelos humanos e escolhe a mais forte, portanto nunca pode "trapacear"
 * com cálculos próprios.
 */
public final class BotBrain {

    private final ScoringEngine scoringEngine;
    private final int maxPlay;

    public BotBrain(ScoringEngine scoringEngine, int maxPlay) {
        this.scoringEngine = scoringEngine;
        this.maxPlay = maxPlay;
    }

    public BotBrain() {
        this(new ScoringEngine(), 5);
    }

    /** Escolhe a jogada legal de maior pontuação. Empates preferem o tipo de mão mais forte e depois menos cartas. */
    public List<Card> choosePlay(List<Card> hand, List<String> jokers, String globalModifierId) {
        if (hand == null || hand.isEmpty()) {
            return List.of();
        }
        List<Card> best = null;
        ScoreResult bestScore = null;
        int limit = Math.min(maxPlay, hand.size());
        for (int size = 1; size <= limit; size++) {
            for (List<Card> combo : combinations(hand, size)) {
                ScoreResult score = scoringEngine.score(combo, jokers, globalModifierId);
                if (bestScore == null || isBetter(score, combo.size(), bestScore, best.size())) {
                    bestScore = score;
                    best = combo;
                }
            }
        }
        return best == null ? List.of(hand.get(0)) : best;
    }

    /**
     * Pontua cada curinga oferecido pela sua contribuição marginal à melhor jogada da mão que o
     * bot possui no momento. Retorna o índice do curinga que ele quer (0 quando não há ofertas).
     */
    public int chooseJoker(List<String> offers, List<Card> hand, List<String> ownedJokers,
                           String globalModifierId) {
        if (offers == null || offers.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        int bestGain = Integer.MIN_VALUE;
        int baseline = bestScoreFor(hand, ownedJokers, globalModifierId);
        for (int i = 0; i < offers.size(); i++) {
            List<String> withJoker = new ArrayList<>(ownedJokers);
            withJoker.add(offers.get(i));
            int gain = bestScoreFor(hand, withJoker, globalModifierId) - baseline;
            if (gain > bestGain) {
                bestGain = gain;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /** Só rerrola quando toda oferta parece fraca para a mão atual e ainda restam rerrolagens. */
    public boolean shouldReroll(List<String> offers, List<Card> hand, List<String> ownedJokers,
                                String globalModifierId, int rerollsLeft) {
        if (rerollsLeft <= 0 || offers == null || offers.isEmpty()) {
            return false;
        }
        int baseline = bestScoreFor(hand, ownedJokers, globalModifierId);
        int bestGain = Integer.MIN_VALUE;
        for (String offer : offers) {
            List<String> withJoker = new ArrayList<>(ownedJokers);
            withJoker.add(offer);
            bestGain = Math.max(bestGain, bestScoreFor(hand, withJoker, globalModifierId) - baseline);
        }
        return bestGain < Math.max(60, baseline / 10);
    }

    /** Resumo legível por humanos, usado por logs/testes. */
    public ScoreResult preview(List<Card> hand, List<String> jokers, String globalModifierId) {
        return scoringEngine.score(hand, jokers, globalModifierId);
    }

    public String jokerName(String id) {
        return Jokers.nameOf(id);
    }

    private int bestScoreFor(List<Card> hand, List<String> jokers, String globalModifierId) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }
        int best = 0;
        int limit = Math.min(maxPlay, hand.size());
        for (int size = 1; size <= limit; size++) {
            for (List<Card> combo : combinations(hand, size)) {
                best = Math.max(best, scoringEngine.score(combo, jokers, globalModifierId).finalScore());
            }
        }
        return best;
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
        List<List<Card>> result = new ArrayList<>();
        collect(hand, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void collect(List<Card> hand, int remaining, int start, List<Card> current,
                                List<List<Card>> result) {
        if (remaining == 0) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = start; i <= hand.size() - remaining; i++) {
            current.add(hand.get(i));
            collect(hand, remaining - 1, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
