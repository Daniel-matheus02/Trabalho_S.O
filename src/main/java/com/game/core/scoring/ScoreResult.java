package com.game.core.scoring;

import com.game.core.model.HandType;

import java.util.List;

/**
 * Resultado imutável da pontuação de uma jogada: o valor final é {@code round(chips * mult)}.
 */
public record ScoreResult(HandType type, int chips, int mult, int finalScore, List<ScoreLine> lines) {

    public ScoreResult {
        lines = List.copyOf(lines);
    }
}
