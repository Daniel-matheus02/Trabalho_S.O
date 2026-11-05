package com.game.net.protocol;

/** Todos os tipos de mensagem do protocolo de duelo. */
public enum MessageType {
    CREATE_SESSION,
    SESSION_CREATED,
    JOIN_SESSION,
    JOIN_ACCEPTED,
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    GAME_START,
    PLAYER_ACTION,
    GAME_STATE_UPDATE,
    TURN_CHANGED,
    ROUND_RESULT,
    PLAYER_DAMAGE,
    GAME_OVER,
    REMATCH_REQUEST,
    PING,
    PONG,
    ERROR
}
