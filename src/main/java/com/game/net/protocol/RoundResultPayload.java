package com.game.net.protocol;

import com.game.core.rules.RoundOutcome;

/** Resultado de combate transmitido após ambas as jogadas serem travadas. */
public record RoundResultPayload(RoundOutcome outcome, int player1Hp, int player2Hp) {
}
