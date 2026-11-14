package com.game.net.protocol;

/** Ações que um cliente pode solicitar. O servidor valida todas elas. */
public enum ActionType {
    SUBMIT_PLAY,
    USE_SABOTAGE,
    PASS_SABOTAGE,
    PICK_JOKER,
    REROLL,
    BUY_SABOTAGE,
    READY,
    ADVANCE,
    REMATCH
}
