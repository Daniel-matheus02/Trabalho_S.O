package com.game.net.protocol;

/** Um cliente pedindo para entrar em uma sessão existente, opcionalmente recuperando sua vaga anterior. */
public record JoinSessionPayload(String sessionCode, String playerName, String reconnectToken) {
}
