package com.game.client.network;

import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.CreateSessionPayload;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.GameOverPayload;
import com.game.net.protocol.JoinSessionPayload;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.PingPayload;
import com.game.net.protocol.PlayerStatusPayload;
import com.game.net.protocol.ProtocolException;
import com.game.net.protocol.RoundResultPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.net.protocol.StatePayload;
import com.game.net.protocol.TurnChangedPayload;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Cliente TCP Netty. Mantém a renderização completamente desacoplada: todos os eventos de
 * entrada caem em uma fila concorrente, e as telas do jogo os consultam na thread de renderização.
 */
public final class GameClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GameClient.class.getName());
    private static final int MAX_FRAME_BYTES = 1 << 20;
    private static final int RECONNECT_ATTEMPTS = 30;
    private static final long RECONNECT_DELAY_MS = 2_000L;

    private final MessageCodec codec = new MessageCodec();
    private final EventLoopGroup group = new NioEventLoopGroup(1, new DefaultThreadFactory("duelo-client"));
    private final ConcurrentLinkedQueue<ClientEvent> events = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "duelo-client-timer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closing = new AtomicBoolean();

    private volatile Channel channel;
    private volatile SessionInfoPayload sessionInfo;
    private volatile GameSnapshot latestSnapshot;
    private volatile String host;
    private volatile int port;
    private volatile String playerName = "Jogador";
    private volatile boolean reconnecting;
    private volatile CompletableFuture<SessionInfoPayload> pendingRequest;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> reconnectTask;

    public CompletableFuture<Void> connect(String host, int port) {
        this.host = host;
        this.port = port;
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("idle", new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS))
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                                            MAX_FRAME_BYTES, 0, 4, 0, 4))
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
                                    .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
                                    .addLast("handler", new ClientHandler(GameClient.this));
                        }
                    });
            ChannelFuture connectFuture = bootstrap.connect(host, port);
            connectFuture.addListener(result -> {
                if (result.isSuccess()) {
                    channel = connectFuture.channel();
                    startPing();
                    emit(new ClientEvent.Connected());
                    future.complete(null);
                } else {
                    String reason = "Não foi possível conectar a " + host + ":" + port;
                    if (result.cause() != null && result.cause().getMessage() != null) {
                        reason = result.cause().getMessage();
                    }
                    emit(new ClientEvent.ErrorReceived("CONNECT_FAILED", reason));
                    future.completeExceptionally(result.cause() == null
                            ? new ProtocolException(reason) : result.cause());
                }
            });
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public boolean isConnected() {
        Channel ch = channel;
        return ch != null && ch.isActive();
    }

    public CompletableFuture<SessionInfoPayload> createSession(String playerName) {
        this.playerName = playerName == null ? "Jogador" : playerName;
        return request(MessageType.CREATE_SESSION, new CreateSessionPayload(this.playerName));
    }

    public CompletableFuture<SessionInfoPayload> joinSession(String code, String playerName, String token) {
        this.playerName = playerName == null ? "Jogador" : playerName;
        return request(MessageType.JOIN_SESSION, new JoinSessionPayload(code, this.playerName, token));
    }

    private CompletableFuture<SessionInfoPayload> request(MessageType type, Object payload) {
        CompletableFuture<SessionInfoPayload> future = new CompletableFuture<>();
        if (!isConnected()) {
            future.completeExceptionally(new ProtocolException("Não conectado a um servidor"));
            return future;
        }
        pendingRequest = future;
        sessionInfo = null;
        write(codec.message(type, null, 0, payload));
        return future;
    }

    public void sendAction(ActionPayload action) {
        SessionInfoPayload info = sessionInfo;
        if (info == null || !isConnected()) {
            emit(new ClientEvent.ErrorReceived("NOT_CONNECTED", "A conexão com o servidor foi perdida"));
            return;
        }
        Envelope envelope = codec.message(MessageType.PLAYER_ACTION, info.sessionId(), info.playerId(), action);
        write(envelope);
    }

    private void write(Envelope envelope) {
        Channel ch = channel;
        if (ch == null || !ch.isActive()) {
            return;
        }
        ch.writeAndFlush(codec.encode(envelope)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warning("Falha ao enviar " + envelope.type() + ": " + future.cause());
            }
        });
    }

    public ClientEvent pollEvent() {
        return events.poll();
    }

    MessageCodec codec() {
        return codec;
    }

    void onEnvelopeError(String code, String message) {
        emit(new ClientEvent.ErrorReceived(code, message));
    }

    public SessionInfoPayload sessionInfo() {
        return sessionInfo;
    }

    public GameSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    // ---------------------------------------------------------------- entrada

    void onEnvelope(Envelope envelope) {
        try {
            switch (envelope.type()) {
                case SESSION_CREATED, JOIN_ACCEPTED -> {
                    SessionInfoPayload info = codec.payload(envelope, SessionInfoPayload.class);
                    sessionInfo = info;
                    playerName = info.playerName();
                    boolean created = envelope.type() == MessageType.SESSION_CREATED;
                    completeRequest(info);
                    emit(new ClientEvent.SessionInfoReceived(info, created));
                    if (reconnecting) {
                        reconnecting = false;
                        cancelReconnect();
                        emit(new ClientEvent.Reconnected());
                    }
                }
                case GAME_START -> {
                    GameSnapshot snapshot = codec.payload(envelope, StatePayload.class).snapshot();
                    latestSnapshot = snapshot;
                    emit(new ClientEvent.GameStarted(snapshot));
                }
                case GAME_STATE_UPDATE -> {
                    GameSnapshot snapshot = codec.payload(envelope, StatePayload.class).snapshot();
                    if (latestSnapshot == null || snapshot.version() >= latestSnapshot.version()) {
                        latestSnapshot = snapshot;
                        emit(new ClientEvent.StateUpdated(snapshot));
                    }
                }
                case TURN_CHANGED -> emit(new ClientEvent.TurnChanged(
                        codec.payload(envelope, TurnChangedPayload.class)));
                case ROUND_RESULT -> emit(new ClientEvent.RoundResolved(
                        codec.payload(envelope, RoundResultPayload.class)));
                case PLAYER_DAMAGE -> emit(new ClientEvent.Damage(
                        codec.payload(envelope, com.game.net.protocol.DamagePayload.class)));
                case GAME_OVER -> emit(new ClientEvent.GameOver(
                        codec.payload(envelope, GameOverPayload.class)));
                case PLAYER_CONNECTED, PLAYER_DISCONNECTED -> emit(new ClientEvent.PlayerStatus(
                        codec.payload(envelope, PlayerStatusPayload.class)));
                case ERROR -> {
                    ErrorPayload error = codec.payload(envelope, ErrorPayload.class);
                    CompletableFuture<SessionInfoPayload> pending = pendingRequest;
                    if (pending != null && !pending.isDone()) {
                        pending.completeExceptionally(new ProtocolException(error.message()));
                        pendingRequest = null;
                    }
                    if (reconnecting) {
                        reconnecting = false;
                        cancelReconnect();
                        emit(new ClientEvent.ReconnectFailed(error.message()));
                    }
                    emit(new ClientEvent.ErrorReceived(error.code(), error.message()));
                }
                case PONG, PING -> {
                    // O tráfego de keep-alive não tem significado para o jogo.
                }
                default -> LOG.fine("Ignorando mensagem " + envelope.type());
            }
        } catch (ProtocolException e) {
            emit(new ClientEvent.ErrorReceived("BAD_MESSAGE", e.getMessage()));
        }
    }

    private void completeRequest(SessionInfoPayload info) {
        CompletableFuture<SessionInfoPayload> pending = pendingRequest;
        if (pending != null) {
            pending.complete(info);
            pendingRequest = null;
        }
    }

    private void emit(ClientEvent event) {
        events.add(event);
    }

    // ---------------------------------------------------------------- atividade

    private void startPing() {
        cancelPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                write(codec.message(MessageType.PING, sessionInfo == null ? null : sessionInfo.sessionId(),
                        sessionInfo == null ? 0 : sessionInfo.playerId(),
                        new PingPayload(System.currentTimeMillis())));
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void cancelPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    void onConnectionLost(String reason) {
        if (closing.get()) {
            return;
        }
        emit(new ClientEvent.ConnectionLost(reason));
        cancelPing();
        channel = null;
        SessionInfoPayload info = sessionInfo;
        if (info == null) {
            return;
        }
        scheduleReconnect(info);
    }

    private void scheduleReconnect(SessionInfoPayload info) {
        if (reconnecting) {
            return;
        }
        reconnecting = true;
        int[] attempt = {0};
        reconnectTask = scheduler.scheduleAtFixedRate(() -> {
            if (closing.get() || isConnected()) {
                return;
            }
            if (attempt[0] >= RECONNECT_ATTEMPTS) {
                reconnecting = false;
                cancelReconnect();
                emit(new ClientEvent.ReconnectFailed("Não foi possível alcançar o servidor"));
                return;
            }
            attempt[0]++;
            int currentAttempt = attempt[0];
            emit(new ClientEvent.Reconnecting(currentAttempt, RECONNECT_ATTEMPTS));
            String h = host;
            int p = port;
            if (h == null || p <= 0) {
                return;
            }
            connectInternal(h, p, info);
        }, 0, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void connectInternal(String host, int port, SessionInfoPayload info) {
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("idle", new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS))
                                    .addLast("frameDecoder", new LengthFieldFrameDecoder())
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8))
                                    .addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8))
                                    .addLast("handler", new ClientHandler(GameClient.this));
                        }
                    });
            ChannelFuture future = bootstrap.connect(host, port);
            future.addListener(result -> {
                if (!result.isSuccess()) {
                    return;
                }
                channel = future.channel();
                startPing();
                write(codec.message(MessageType.JOIN_SESSION, info.sessionId(), info.playerId(),
                        new JoinSessionPayload(info.sessionCode(), info.playerName(), info.reconnectToken())));
            });
        } catch (RuntimeException e) {
            LOG.fine("Tentativa de reconexão falhou: " + e.getMessage());
        }
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        cancelPing();
        cancelReconnect();
        scheduler.shutdownNow();
        Channel ch = channel;
        if (ch != null) {
            ch.close();
        }
        channel = null;
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }

    /** Subclasse nomeada simples para manter curta a declaração da pipeline de reconexão. */
    private static final class LengthFieldFrameDecoder extends LengthFieldBasedFrameDecoder {
        LengthFieldFrameDecoder() {
            super(MAX_FRAME_BYTES, 0, 4, 0, 4);
        }
    }
}
