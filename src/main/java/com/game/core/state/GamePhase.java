package com.game.core.state;

/** Fases explícitas do jogo. Não existe fase oculta: toda transição é validada. */
public enum GamePhase {
    WAITING,
    BUILD,
    SABOTAGE,
    REVEAL,
    SHOP,
    GAME_OVER
}
