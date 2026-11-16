package com.game.core.state;

/**
 * Fases explícitas do jogo. Não existe fase oculta: toda transição é validada.
 *
 * <p>{@link #SABOTAGE} é a fase de poderes: cada jogador recebe uma única mão embaralhada de
 * jokers e sabotagens desta rodada, escolhe até o limite de cartas dela e trava a escolha.</p>
 */
public enum GamePhase {
    WAITING,
    BUILD,
    SABOTAGE,
    REVEAL,
    GAME_OVER
}
