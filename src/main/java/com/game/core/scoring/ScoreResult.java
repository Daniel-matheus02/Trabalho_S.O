package com.game.core.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandType;

import java.util.List;

/**
 * Resultado imutável da pontuação de uma jogada: o valor final é {@code round(chips * mult)}.
 *
 * <p>{@code scoringCards} contém as cinco cartas efetivamente usadas na melhor mão (duas do
 * jogador + três da mesa, por exemplo), o que permite à interface destacar o que pontuou.</p>
 */
public record ScoreResult(
        HandType type,
        int chips,
        int mult,
        int finalScore,
        List<ScoreLine> lines,
        List<Card> scoringCards
) {

    public ScoreResult {
        lines = List.copyOf(lines);
        scoringCards = List.copyOf(scoringCards);
    }

    /** Construtor de compatibilidade para resultados sem destaque de cartas. */
    public ScoreResult(HandType type, int chips, int mult, int finalScore, List<ScoreLine> lines) {
        this(type, chips, mult, finalScore, lines, List.of());
    }
}
