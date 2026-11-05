package com.game.net.protocol;

/** Resposta do servidor descrevendo a sessão e a identidade do solicitante dentro dela. */
public record SessionInfoPayload(
        String sessionId,
        String sessionCode,
        int playerId,
        String playerName,
        String opponentName,
        int maxPlayers,
        boolean started,
        String reconnectToken
) {
}
