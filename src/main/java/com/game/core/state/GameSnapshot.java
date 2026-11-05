package com.game.core.state;

import com.game.core.rules.GameRules;
import com.game.core.rules.RoundOutcome;

import java.util.List;

/**
 * Instantâneo imutável do estado autoritativo, filtrado para um visualizador.
 * Estes são os únicos dados do jogo que atravessam a rede.
 */
public record GameSnapshot(
        String sessionId,
        long version,
        GamePhase phase,
        int round,
        int currentPlayer,
        GameRules rules,
        String globalModifierId,
        String globalModifierName,
        String globalModifierDescription,
        PlayerView player1,
        PlayerView player2,
        RoundOutcome lastOutcome,
        int winner,
        List<LogEntry> log
) {
    public GameSnapshot {
        log = List.copyOf(log);
    }

    public PlayerView player(int id) {
        return id == 1 ? player1 : player2;
    }

    public PlayerView opponentOf(int id) {
        return id == 1 ? player2 : player1;
    }

    public boolean isTurnOf(int id) {
        return (phase == GamePhase.BUILD || phase == GamePhase.DRAFT) && currentPlayer == id;
    }
}
