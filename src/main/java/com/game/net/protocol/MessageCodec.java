package com.game.net.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Codificação/decodificação JSON, além da construção de envelope e do acesso a payload tipado. */
public final class MessageCodec {

    private final ObjectMapper mapper;

    public MessageCodec() {
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String encode(Envelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Não foi possível codificar a mensagem: " + e.getMessage(), e);
        }
    }

    public Envelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new ProtocolException("Mensagem vazia");
        }
        try {
            Envelope envelope = mapper.readValue(json, Envelope.class);
            if (envelope.type() == null) {
                throw new ProtocolException("A mensagem não tem tipo");
            }
            if (envelope.messageId() == null || envelope.messageId().isBlank()) {
                throw new ProtocolException("A mensagem não tem messageId");
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Mensagem malformada: " + e.getOriginalMessage(), e);
        }
    }

    public <T> T payload(Envelope envelope, Class<T> type) {
        JsonNode node = envelope.payload();
        if (node == null || node.isNull()) {
            throw new ProtocolException("A mensagem " + envelope.type() + " não tem payload");
        }
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Payload inválido para " + envelope.type() + ": " + e.getMessage(), e);
        }
    }

    public Envelope message(MessageType type, String sessionId, int playerId, Object payload) {
        return new Envelope(type, UUID.randomUUID().toString(), sessionId, playerId,
                System.currentTimeMillis(), payload == null ? null : mapper.valueToTree(payload));
    }

    public Envelope error(String sessionId, int playerId, String code, String message) {
        return message(MessageType.ERROR, sessionId, playerId, new ErrorPayload(code, message));
    }
}
