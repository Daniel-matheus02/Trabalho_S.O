package com.game.client.network;

import com.game.net.protocol.Envelope;
import com.game.net.protocol.ProtocolException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.logging.Logger;

/** Encaminha os envelopes decodificados do servidor para o cliente e informa perdas de conexão. */
final class ClientHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final GameClient client;

    ClientHandler(GameClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String message) {
        try {
            Envelope envelope = client.codec().decode(message);
            client.onEnvelope(envelope);
        } catch (ProtocolException e) {
            LOG.warning("Mensagem malformada do servidor: " + e.getMessage());
            client.onEnvelopeError("BAD_MESSAGE", e.getMessage());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            ctx.close();
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        client.onConnectionLost("Conexão encerrada");
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.warning("Erro na conexão do cliente: " + cause);
        client.onConnectionLost(cause.getMessage() == null ? "Erro de conexão" : cause.getMessage());
        ctx.close();
    }
}
