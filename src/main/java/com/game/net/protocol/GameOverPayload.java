package com.game.net.protocol;

/** Resultado final de uma partida. */
public record GameOverPayload(int winner, int player1Hp, int player2Hp, int rounds) {
}
