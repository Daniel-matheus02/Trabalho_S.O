package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandEvaluator;
import com.game.core.model.HandType;

import java.util.List;

/**
 * Motor de pontuação determinístico compartilhado pelo jogador humano, pela IA e pelo
 * servidor autoritativo. A pontuação final é {@code Math.round(chips * mult)} exatamente como no protótipo.
 */
public final class ScoringEngine {

    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId) {
        List<Card> played = cards == null ? List.of() : List.copyOf(cards);
        HandType type = HandEvaluator.evaluate(played);

        int cardSum = played.stream().mapToInt(card -> card.rank().chipValue()).sum();
        ScoreContext context = new ScoreContext(
                played,
                type,
                type.baseChips() + cardSum,
                type.baseMultiplier()
        );
        context.addLine(ScoreLine.normal("base " + type.displayName() + ": "
                + type.baseChips() + " fichas x " + type.baseMultiplier() + " mult"));
        context.addLine(ScoreLine.plus("cartas: +" + cardSum + " fichas"));

        GlobalModifierDef global = GlobalModifiers.byId(globalModifierId);
        if (global != null) {
            global.effect().apply(context);
        }
        if (jokerIds != null) {
            for (String id : jokerIds) {
                JokerDef joker = Jokers.byId(id);
                if (joker != null) {
                    joker.effect().apply(context);
                }
            }
        }

        int finalScore = (int) Math.round((double) context.chips() * context.mult());
        return new ScoreResult(type, context.chips(), context.mult(), finalScore, context.lines());
    }

    /** Sobrecarga de conveniência para pré-visualizações sem modificadores. */
    public ScoreResult score(List<Card> cards) {
        return score(cards, List.of(), null);
    }
}
