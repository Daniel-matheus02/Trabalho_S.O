package com.game.net.protocol;

/** Um cliente pedindo ao servidor para abrir uma nova sessão. */
public record CreateSessionPayload(String playerName) {
}
