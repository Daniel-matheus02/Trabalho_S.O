package com.game.server.session;

/** Lançada quando uma requisição em nível de sessão não pode ser atendida. */
public class SessionException extends RuntimeException {

    private final String code;

    public SessionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
