package com.game.net.protocol;

import com.game.core.state.GamePhase;

/** Evento leve informando aos clientes de quem é o turno; o snapshot continua sendo a fonte da verdade. */
public record TurnChangedPayload(int round, GamePhase phase, int currentPlayer, long version) {
}
