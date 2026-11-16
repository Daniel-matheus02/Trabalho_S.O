package com.game.net.protocol;

/** Ações que um cliente pode solicitar. O servidor valida todas elas. */
public enum ActionType {
    SUBMIT_PLAY,
    DISCARD_CARDS,
    LOCK_POWERS,
    ADVANCE,
    REMATCH
}
