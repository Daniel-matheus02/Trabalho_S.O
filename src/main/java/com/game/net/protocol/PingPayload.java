package com.game.net.protocol;

/** Requisição de keep-alive. */
public record PingPayload(long clientTime) {
}
