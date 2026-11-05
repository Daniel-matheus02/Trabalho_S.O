package com.game.net.protocol;

/** Erro de protocolo/servidor. */
public record ErrorPayload(String code, String message) {
}
