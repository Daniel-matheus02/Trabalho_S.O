package com.game.server.network;

import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.ProtocolException;
import com.game.server.session.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.logging.Logger;

/** Decodifica quadros JSON e os encaminha ao gerenciador de sessões. */
public final class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger LOG = Logger.getLogger(ServerHandler.class.getName());

    private final SessionManager sessions;
    private final MessageCodec codec;

    public ServerHandler(SessionManager sessions) {
        this.sessions = sessions;
        this.codec = sessions.codec();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String message) {
        try {
            Envelope envelope = codec.decode(message);
            sessions.handleMessage(ctx.channel(), envelope);
        } catch (ProtocolException e) {
            ctx.channel().writeAndFlush(codec.encode(codec.message(MessageType.ERROR, null, 0,
                    new ErrorPayload("BAD_MESSAGE", e.getMessage()))));
            LOG.warning("Mensagem inválida de " + ctx.channel().remoteAddress() + ": " + e.getMessage());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            LOG.info("Fechando conexão ociosa " + ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        sessions.channelInactive(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.warning("Erro de conexão com " + ctx.channel().remoteAddress() + ": " + cause);
        ctx.close();
    }
}
