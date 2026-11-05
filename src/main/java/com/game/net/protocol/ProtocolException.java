package com.game.net.protocol;

/** Lançada quando uma mensagem de rede não pode ser analisada ou validada. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
