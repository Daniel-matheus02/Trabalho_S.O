package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandEvaluator;
import com.game.core.model.HandType;

import java.util.List;

/**
 * Motor de pontuação determinístico compartilhado pelo jogador humano, pela IA e pelo
 * servidor autoritativo. A pontuação final é {@code Math.round(chips * mult)} exatamente como no
 * protótipo.
 *
 * <p>A partir da v0.3 o pool de cartas pode ter até sete cartas (duas do jogador + cinco da
 * mesa); o motor escolhe a melhor combinação de cinco antes de aplicar modificadores e curaingas.</p>
 */
public final class ScoringEngine {

    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId) {
        return score(cards, jokerIds, globalModifierId, ScoreModifiers.NONE);
    }

    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId,
                             ScoreModifiers modifiers) {
        List<Card> pool = cards == null ? List.of() : List.copyOf(cards);
        ScoreModifiers mods = modifiers == null ? ScoreModifiers.NONE : modifiers;
        List<Card> played = HandEvaluator.bestFive(pool, !mods.blockFlush());
        HandType type = HandEvaluator.evaluate(played, !mods.blockFlush());

        int cardSum = 0;
        for (Card card : played) {
            cardSum += card.rank().chipValue();
        }
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
        if (jokerIds != null && !mods.nullifyJokers()) {
            for (int i = 0; i < jokerIds.size(); i++) {
                if (i == mods.disabledJokerIndex()) {
                    continue;
                }
                JokerDef joker = Jokers.byId(jokerIds.get(i));
                if (joker != null) {
                    joker.effect().apply(context);
                }
            }
        }

        if (mods.blockFlush()) {
            context.addLine(ScoreLine.normal("Anti-Flush: flush anulado"));
        }
        if (mods.pairTax() && type.atLeast(HandType.PAIR)) {
            context.addChips(-25);
            context.addLine(ScoreLine.normal("Imposto do Par: -25 fichas"));
        }
        if (mods.multPenalty() > 0) {
            context.addMult(-mods.multPenalty());
            context.addMultLine("Peso de Chumbo: -" + mods.multPenalty() + " mult");
        }

        int chips = Math.max(0, context.chips());
        int mult = Math.max(0, context.mult());
        int finalScore = Math.max(0, (int) Math.round((double) chips * mult));
        return new ScoreResult(type, chips, mult, finalScore, context.lines(), played);
    }

    /** Sobrecarga de conveniência para pré-visualizações sem modificadores. */
    public ScoreResult score(List<Card> cards) {
        return score(cards, List.of(), null);
    }
}
