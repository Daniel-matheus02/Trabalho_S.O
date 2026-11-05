package com.game.server.network;

import com.game.net.protocol.MessageCodec;
import com.game.server.ServerConfig;
import com.game.server.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.logging.Logger;

/**
 * Servidor TCP Netty. Pode rodar de forma autônoma (ServerLauncher) ou embutido no cliente do jogo
 * quando um jogador hospeda uma partida na própria máquina.
 */
public final class GameServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final ServerConfig config;
    private final MessageCodec codec = new MessageCodec();
    private final SessionManager sessions;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public GameServer(ServerConfig config) {
        this.config = config;
        this.sessions = new SessionManager(config, codec);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("duelo-accept"));
        workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("duelo-netty"));
        sessions.start();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ServerChannelInitializer(sessions, config));
        serverChannel = bootstrap.bind(config.bindAddress(), config.port()).sync().channel();
        LOG.info(() -> "Servidor Duelo de Fichas escutando em " + config.bindAddress() + ":"
                + boundPort());
    }

    public int boundPort() {
        if (serverChannel == null) {
            return -1;
        }
        return ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public ServerConfig config() {
        return config;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public MessageCodec codec() {
        return codec;
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        sessions.close();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS);
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS);
            workerGroup = null;
        }
        LOG.info("Servidor parado");
    }
}
