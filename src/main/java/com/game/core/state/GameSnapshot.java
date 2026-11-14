package com.game.core.state;

import com.game.core.model.Card;
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
        List<Card> community,
        int firstResolver,
        PlayerView player1,
        PlayerView player2,
        RoundOutcome lastOutcome,
        int winner,
        List<LogEntry> log
) {
    public GameSnapshot {
        community = List.copyOf(community);
        log = List.copyOf(log);
    }

    public PlayerView player(int id) {
        return id == 1 ? player1 : player2;
    }

    public PlayerView opponentOf(int id) {
        return id == 1 ? player2 : player1;
    }

    /** O turno alternado existe apenas na escolha de cartas do BUILD. */
    public boolean isTurnOf(int id) {
        return phase == GamePhase.BUILD && currentPlayer == id;
    }
}
