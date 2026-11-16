package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandEvaluator;
import com.game.core.model.HandType;
import com.game.core.sabotage.Sabotages;

import java.util.List;

/**
 * Motor de pontuação determinístico compartilhado pelo jogador humano, pela IA e pelo
 * servidor autoritativo. A pontuação final é {@code Math.round(chips * mult)} exatamente como no
 * protótipo.
 *
 * <p>O motor recebe as cartas jogadas e sempre avalia a melhor combinação de cinco antes de
 * aplicar modificadores e curingas. A partida usa exatamente as 5 cartas enviadas por jogador.
 * Cada etapa também produz um {@link ScoreStep} com os totais acumulados.</p>
 */
public final class ScoringEngine {

    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId) {
        return score(cards, jokerIds, globalModifierId, ScoreModifiers.NONE);
    }

    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId,
                             ScoreModifiers modifiers) {
        return score(cards, jokerIds, globalModifierId, modifiers, List.of());
    }

    /**
     * Variante usada pelo motor no resultado final: além dos modificadores agregados, recebe os
     * ids das sabotagens jogadas contra o lado, para que cada carta de sabotagem gere seu próprio
     * passo com {@code sourceId}.
     */
    public ScoreResult score(List<Card> cards, List<String> jokerIds, String globalModifierId,
                             ScoreModifiers modifiers, List<String> sabotageIds) {
        List<Card> pool = cards == null ? List.of() : List.copyOf(cards);
        ScoreModifiers mods = modifiers == null ? ScoreModifiers.NONE : modifiers;
        List<Card> played = HandEvaluator.bestFive(pool, !mods.blockFlush());
        HandType type = HandEvaluator.evaluate(played, !mods.blockFlush());

        ScoreContext context = new ScoreContext(played, type, 0, 0);

        context.beginStep(StepKind.HAND_BASE, -1, null, type.displayName());
        context.addChips(type.baseChips());
        context.addMult(type.baseMultiplier());
        context.commitStep();
        context.addLine(ScoreLine.normal("base " + type.displayName() + ": "
                + type.baseChips() + " fichas x " + type.baseMultiplier() + " mult"));

        int cardSum = 0;
        for (int i = 0; i < played.size(); i++) {
            Card card = played.get(i);
            int chips = card.rank().chipValue();
            cardSum += chips;
            context.beginStep(StepKind.CARD, i, card.code(), "carta " + card.code());
            context.addChips(chips);
            context.commitStep();
        }
        context.addLine(ScoreLine.plus("cartas: +" + cardSum + " fichas"));

        GlobalModifierDef global = GlobalModifiers.byId(globalModifierId);
        if (global != null) {
            context.beginStep(StepKind.GLOBAL, -1, global.id(), global.name());
            global.effect().apply(context);
            context.commitStep();
        }

        if (jokerIds != null) {
            for (String jokerId : jokerIds) {
                JokerDef joker = Jokers.byId(jokerId);
                context.beginStep(StepKind.JOKER, -1, jokerId,
                        joker == null ? jokerId : joker.name());
                if (!mods.nullifyJokers() && joker != null) {
                    joker.effect().apply(context);
                }
                context.commitStep();
            }
        }

        applySabotageSteps(context, type, mods, sabotageIds);

        if (mods.blockFlush()) {
            context.addLine(ScoreLine.normal("Anti-Flush: flush anulado"));
        }

        int chips = context.chips();
        int mult = context.mult();
        int finalScore = Math.max(0, (int) Math.round((double) chips * mult));
        return new ScoreResult(type, chips, mult, finalScore, context.lines(),
                played, context.steps());
    }

    private void applySabotageSteps(ScoreContext context, HandType type, ScoreModifiers mods,
                                    List<String> sabotageIds) {
        boolean pairTaxApplied = false;
        boolean multPenaltyApplied = false;
        if (sabotageIds != null) {
            for (String sabotageId : sabotageIds) {
                context.beginStep(StepKind.SABOTAGE, -1, sabotageId,
                        Sabotages.nameOf(sabotageId));
                if ("peso_chumbo".equals(sabotageId)) {
                    context.addMult(-5);
                    context.addMultLine("Peso de Chumbo: -5 mult");
                    multPenaltyApplied = true;
                } else if ("imposto_par".equals(sabotageId)) {
                    if (type.atLeast(HandType.PAIR)) {
                        context.addChips(-25);
                        context.addLine(ScoreLine.normal("Imposto do Par: -25 fichas"));
                    }
                    pairTaxApplied = true;
                }
                context.commitStep();
            }
        }
        // Compatibilidade (IA/prévias sem ids): aplica o agregado que ainda não foi atribuído.
        if (mods.pairTax() && !pairTaxApplied) {
            context.beginStep(StepKind.SABOTAGE, -1, null, "Imposto do Par");
            if (type.atLeast(HandType.PAIR)) {
                context.addChips(-25);
                context.addLine(ScoreLine.normal("Imposto do Par: -25 fichas"));
            }
            context.commitStep();
        }
        if (mods.multPenalty() > 0 && !multPenaltyApplied) {
            context.beginStep(StepKind.SABOTAGE, -1, null, "Peso de Chumbo");
            context.addMult(-mods.multPenalty());
            context.addMultLine("Peso de Chumbo: -" + mods.multPenalty() + " mult");
            context.commitStep();
        }
    }
}
