package com.game.core.rules;

/** Lançada quando uma ação viola as regras do jogo ou a fase atual. */
public class GameRuleException extends RuntimeException {

    private final String code;

    public GameRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
