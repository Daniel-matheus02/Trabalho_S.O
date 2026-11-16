package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandType;

import java.util.List;

/**
 * Resultado imutável da pontuação de uma jogada: o valor final é {@code round(chips * mult)}.
 *
 * <p>{@code scoringCards} contém as cartas efetivamente usadas na melhor mão de cinco (a partida
 * sempre envia exatamente cinco cartas). {@code steps} contém a decomposição atômica que alimenta
 * a contagem animada; o último passo sempre coincide com {@code chips}/{@code mult}.</p>
 */
public record ScoreResult(
        HandType type,
        int chips,
        int mult,
        int finalScore,
        List<ScoreLine> lines,
        List<Card> scoringCards,
        List<ScoreStep> steps
) {

    public ScoreResult {
        lines = List.copyOf(lines);
        scoringCards = List.copyOf(scoringCards);
        steps = List.copyOf(steps);
    }
}
