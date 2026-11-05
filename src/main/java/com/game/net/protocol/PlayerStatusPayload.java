package com.game.net.protocol;

/** Informação de presença do jogador para a sala de espera e o painel do oponente durante o jogo. */
public record PlayerStatusPayload(
        int playerId,
        String playerName,
        int hp,
        boolean connected,
        boolean ready
) {
}
