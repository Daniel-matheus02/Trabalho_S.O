package com.game.core.state;

/** Fases explícitas do jogo. Não existe fase oculta: toda transição é validada. */
public enum GamePhase {
    WAITING,
    BUILD,
    REVEAL,
    DRAFT,
    GAME_OVER
}
