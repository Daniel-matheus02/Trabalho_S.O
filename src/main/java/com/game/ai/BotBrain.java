package com.game.ai;

import com.game.core.model.Card;
import com.game.core.model.HandEvaluator;
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

    private static final List<String> SABOTAGE_PRIORITY = List.of(
            "nevoa", "bloqueio_joker", "peso_chumbo", "anti_flush",
            "imposto_par", "troca_river", "virada_naipes");

    private final ScoringEngine scoringEngine;
    private final int maxPlay;

    public BotBrain(ScoringEngine scoringEngine, int maxPlay) {
        this.scoringEngine = scoringEngine;
        this.maxPlay = maxPlay;
    }

    public BotBrain() {
        this(new ScoringEngine(), 2);
    }

    /** Escolhe a melhor jogada legal combinando as cartas escolhidas com a mesa. */
    public List<Card> choosePlay(List<Card> hand, List<String> jokers, String globalModifierId,
                                 List<Card> community) {
        if (hand == null || hand.isEmpty()) {
            return List.of();
        }
        List<Card> table = community == null ? List.of() : community;
        List<Card> best = null;
        ScoreResult bestScore = null;
        int limit = Math.min(maxPlay, hand.size());
        for (List<Card> combo : HandEvaluator.combinations(hand, limit)) {
            ScoreResult score = scoringEngine.score(combine(combo, table), jokers, globalModifierId);
            if (bestScore == null || isBetter(score, combo.size(), bestScore, best.size())) {
                bestScore = score;
                best = combo;
            }
        }
        return best == null ? List.of(hand.get(0)) : best;
    }

    public List<Card> choosePlay(List<Card> hand, List<String> jokers, String globalModifierId) {
        return choosePlay(hand, jokers, globalModifierId, List.of());
    }

    /**
     * Pontua cada curinga oferecido pela sua contribuição marginal à melhor jogada da mão que o
     * bot possui no momento. Retorna o índice do curinga que ele quer (0 quando não há ofertas).
     */
    public int chooseJoker(List<String> offers, List<Card> hand, List<String> ownedJokers,
                           String globalModifierId, List<Card> community) {
        if (offers == null || offers.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        int bestGain = Integer.MIN_VALUE;
        int baseline = bestScoreFor(hand, ownedJokers, globalModifierId, community);
        for (int i = 0; i < offers.size(); i++) {
            List<String> withJoker = new ArrayList<>(ownedJokers);
            withJoker.add(offers.get(i));
            int gain = bestScoreFor(hand, withJoker, globalModifierId, community) - baseline;
            if (gain > bestGain) {
                bestGain = gain;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public int chooseJoker(List<String> offers, List<Card> hand, List<String> ownedJokers,
                           String globalModifierId) {
        return chooseJoker(offers, hand, ownedJokers, globalModifierId, List.of());
    }

    /** Só rerrola quando toda oferta parece fraca para a mão atual e ainda restam rerollagens grátis. */
    public boolean shouldReroll(List<String> offers, List<Card> hand, List<String> ownedJokers,
                                String globalModifierId, int rerollsLeft) {
        return shouldReroll(offers, hand, ownedJokers, globalModifierId, rerollsLeft, List.of());
    }

    public boolean shouldReroll(List<String> offers, List<Card> hand, List<String> ownedJokers,
                                String globalModifierId, int rerollsLeft, List<Card> community) {
        if (rerollsLeft <= 0 || offers == null || offers.isEmpty()) {
            return false;
        }
        int baseline = bestScoreFor(hand, ownedJokers, globalModifierId, community);
        int bestGain = Integer.MIN_VALUE;
        for (String offer : offers) {
            List<String> withJoker = new ArrayList<>(ownedJokers);
            withJoker.add(offer);
            bestGain = Math.max(bestGain,
                    bestScoreFor(hand, withJoker, globalModifierId, community) - baseline);
        }
        return bestGain < Math.max(60, baseline / 10);
    }

    /**
     * Escolhe uma sabotagem da mão. Retorna o índice, ou -1 para passar quando não há cartas.
     * A prioridade favorece desligar jokers e reduzir multiplicadores.
     */
    public int chooseSabotage(List<String> hand) {
        if (hand == null || hand.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        int bestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < hand.size(); i++) {
            int priority = SABOTAGE_PRIORITY.indexOf(hand.get(i));
            if (priority < 0) {
                priority = SABOTAGE_PRIORITY.size();
            }
            if (priority < bestPriority) {
                bestPriority = priority;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /** Compra enquanto houver espaço na mão e moedas suficientes. */
    public boolean shouldBuySabotage(int coins, int price, int owned, int maxSabotages) {
        return owned < maxSabotages && coins >= price;
    }

    /** Resumo legível por humanos, usado por logs/testes. */
    public ScoreResult preview(List<Card> hand, List<String> jokers, String globalModifierId) {
        return scoringEngine.score(hand, jokers, globalModifierId);
    }

    public String jokerName(String id) {
        return Jokers.nameOf(id);
    }

    private int bestScoreFor(List<Card> hand, List<String> jokers, String globalModifierId,
                             List<Card> community) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }
        int best = 0;
        List<Card> table = community == null ? List.of() : community;
        int limit = Math.min(maxPlay, hand.size());
        for (List<Card> combo : HandEvaluator.combinations(hand, limit)) {
            best = Math.max(best,
                    scoringEngine.score(combine(combo, table), jokers, globalModifierId).finalScore());
        }
        return best;
    }

    private static List<Card> combine(List<Card> played, List<Card> community) {
        List<Card> pool = new ArrayList<>(played);
        pool.addAll(community);
        return pool;
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
