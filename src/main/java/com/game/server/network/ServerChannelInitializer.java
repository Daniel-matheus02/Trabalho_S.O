package com.game.server.network;

import com.game.server.ServerConfig;
import com.game.server.session.SessionManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Pipeline TCP: quadros JSON UTF-8 com prefixo de comprimento em torno do handler de sessão. */
public final class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    public static final int MAX_FRAME_BYTES = 1 << 20;

    private final SessionManager sessions;
    private final ServerConfig config;

    public ServerChannelInitializer(SessionManager sessions, ServerConfig config) {
        this.sessions = sessions;
        this.config = config;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline()
                .addLast("idle", new IdleStateHandler(config.connectionTimeoutSeconds(), 0, 0, TimeUnit.SECONDS))
                .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(MAX_FRAME_BYTES, 0, 4, 0, 4))
                .addLast("frameEncoder", new LengthFieldPrepender(4))
                .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
                .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
                .addLast("handler", new ServerHandler(sessions));
    }
}
