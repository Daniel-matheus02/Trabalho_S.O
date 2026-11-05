package com.game.net.protocol;

/** Resposta de keep-alive. */
public record PongPayload(long clientTime, long serverTime) {
}
