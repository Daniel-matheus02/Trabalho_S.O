package com.game.net.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope compartilhado por todas as mensagens: tipo, id da mensagem (para detecção de
 * duplicatas), sessão, remetente, timestamp e um payload JSON de formato livre.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope(
        MessageType type,
        String messageId,
        String sessionId,
        int playerId,
        long timestamp,
        JsonNode payload
) {
    public boolean hasPayload() {
        return payload != null && !payload.isNull();
    }
}
