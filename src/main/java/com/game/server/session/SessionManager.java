package com.game.server.session;

import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.PongPayload;
import com.game.net.protocol.ProtocolException;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.MessageCodec;
import com.game.server.ServerConfig;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Controla todas as sessões e valida as questões de transporte: busca de sessão, identidade,
 * mensagens duplicadas, limites de tamanho e limpeza. As regras do jogo permanecem em {@link GameSession}.
 */
public final class SessionManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    public static final AttributeKey<String> SESSION_ID = AttributeKey.valueOf("duelo.sessionId");
    public static final AttributeKey<Integer> PLAYER_ID = AttributeKey.valueOf("duelo.playerId");

    private final ServerConfig config;
    private final MessageCodec codec;
    private final Map<String, GameSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, GameSession> sessionsByCode = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "session-cleanup");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicInteger sessionCounter = new AtomicInteger();

    public SessionManager(ServerConfig config, MessageCodec codec) {
        this.config = config;
        this.codec = codec;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.SECONDS);
    }

    public int activeSessions() {
        return sessionsById.size();
    }

    public GameSession sessionByCode(String code) {
        return code == null ? null : sessionsByCode.get(code.toUpperCase(Locale.ROOT));
    }

    public MessageCodec codec() {
        return codec;
    }

    /** Ponto de entrada para toda mensagem decodificada vinda de um canal conectado. */
    public void handleMessage(Channel channel, Envelope envelope) {
        try {
            switch (envelope.type()) {
                case CREATE_SESSION -> handleCreate(channel, envelope);
                case JOIN_SESSION -> handleJoin(channel, envelope);
                case PLAYER_ACTION -> handleAction(channel, envelope);
                case PING -> handlePing(channel, envelope);
                default -> error(channel, envelope.sessionId(), envelope.playerId(),
                        "UNKNOWN_MESSAGE", "Os clientes não podem enviar " + envelope.type());
            }
        } catch (ProtocolException e) {
            error(channel, envelope.sessionId(), envelope.playerId(), "BAD_MESSAGE", e.getMessage());
        } catch (SessionException e) {
            error(channel, envelope.sessionId(), 0, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.warning("Erro inesperado ao tratar " + envelope.type() + ": " + e);
            error(channel, envelope.sessionId(), envelope.playerId(), "SERVER_ERROR",
                    "Erro inesperado no servidor");
        }
    }

    private void handleCreate(Channel channel, Envelope envelope) {
        if (channel.attr(SESSION_ID).get() != null) {
            throw new SessionException("ALREADY_IN_SESSION", "Esta conexão já pertence a uma sessão");
        }
        if (sessionsById.size() >= config.maxSessions()) {
            throw new SessionException("SERVER_FULL", "O servidor atingiu seu limite de sessões");
        }
        String name = codec.payload(envelope, com.game.net.protocol.CreateSessionPayload.class).playerName();
        GameSession session = new GameSession(nextCode(), config.maxPlayersPerSession(), codec);
        sessionsById.put(session.sessionId(), session);
        sessionsByCode.put(session.code(), session);
        sessionCounter.incrementAndGet();

        var info = session.join(channel, name, null, MessageType.SESSION_CREATED);
        channel.attr(SESSION_ID).set(session.sessionId());
        channel.attr(PLAYER_ID).set(info.playerId());
        LOG.info(() -> "Sessão " + session.code() + " criada por " + info.playerName());
    }

    private void handleJoin(Channel channel, Envelope envelope) {
        if (channel.attr(SESSION_ID).get() != null) {
            throw new SessionException("ALREADY_IN_SESSION", "Esta conexão já pertence a uma sessão");
        }
        var payload = codec.payload(envelope, com.game.net.protocol.JoinSessionPayload.class);
        GameSession session = sessionsByCode.get(normaliseCode(payload.sessionCode()));
        if (session == null) {
            throw new SessionException("SESSION_NOT_FOUND", "Nenhuma sessão com o código " + payload.sessionCode());
        }
        var info = session.join(channel, payload.playerName(), payload.reconnectToken());
        channel.attr(SESSION_ID).set(session.sessionId());
        channel.attr(PLAYER_ID).set(info.playerId());
        LOG.info(() -> "Jogador " + info.playerName() + " entrou na sessão " + session.code());
    }

    private void handleAction(Channel channel, Envelope envelope) {
        GameSession session = requireSession(channel);
        int playerId = requirePlayer(channel);
        if (envelope.sessionId() != null && !envelope.sessionId().equals(session.sessionId())) {
            throw new SessionException("SESSION_MISMATCH", "O id da sessão não corresponde a esta conexão");
        }
        if (envelope.playerId() != 0 && envelope.playerId() != playerId) {
            throw new SessionException("PLAYER_MISMATCH", "O id do jogador não corresponde a esta conexão");
        }
        ActionPayload action = codec.payload(envelope, ActionPayload.class);
        if (action.action() == null) {
            throw new SessionException("BAD_ACTION", "Tipo de ação ausente");
        }
        session.handleAction(playerId, envelope, action);
    }

    private void handlePing(Channel channel, Envelope envelope) {
        var ping = codec.payload(envelope, com.game.net.protocol.PingPayload.class);
        channel.writeAndFlush(codec.encode(codec.message(MessageType.PONG,
                envelope.sessionId(), envelope.playerId(),
                new PongPayload(ping.clientTime(), System.currentTimeMillis()))));
    }

    private GameSession requireSession(Channel channel) {
        String sessionId = channel.attr(SESSION_ID).get();
        if (sessionId == null) {
            throw new SessionException("NOT_IN_SESSION", "Crie ou entre em uma sessão primeiro");
        }
        GameSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new SessionException("SESSION_NOT_FOUND", "A sessão não existe mais");
        }
        return session;
    }

    private int requirePlayer(Channel channel) {
        Integer playerId = channel.attr(PLAYER_ID).get();
        if (playerId == null) {
            throw new SessionException("NOT_IN_SESSION", "Crie ou entre em uma sessão primeiro");
        }
        return playerId;
    }

    public void channelInactive(Channel channel) {
        String sessionId = channel.attr(SESSION_ID).get();
        Integer playerId = channel.attr(PLAYER_ID).get();
        channel.attr(SESSION_ID).set(null);
        channel.attr(PLAYER_ID).set(null);
        if (sessionId == null || playerId == null) {
            return;
        }
        GameSession session = sessionsById.get(sessionId);
        if (session != null) {
            session.playerDisconnected(playerId);
        }
    }

    private void cleanup() {
        try {
            long now = System.currentTimeMillis();
            long timeout = config.sessionTimeoutSeconds() * 1000L;
            List<GameSession> expired = new ArrayList<>();
            for (GameSession session : sessionsById.values()) {
                if (session.expireIfIdle(now, timeout)) {
                    expired.add(session);
                }
            }
            for (GameSession session : expired) {
                sessionsById.remove(session.sessionId());
                sessionsByCode.remove(session.code());
            }
        } catch (RuntimeException e) {
            LOG.warning("Falha na limpeza de sessões: " + e);
        }
    }

    private void error(Channel channel, String sessionId, int playerId, String code, String message) {
        channel.writeAndFlush(codec.encode(codec.message(MessageType.ERROR, sessionId, playerId,
                new ErrorPayload(code, message))));
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt((int) (Math.random() * CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!sessionsByCode.containsKey(code)) {
                return code;
            }
        }
        return "S" + sessionCounter.incrementAndGet();
    }

    private static String normaliseCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        for (GameSession session : sessionsById.values()) {
            session.close("SERVER_SHUTDOWN", "O servidor está sendo encerrado");
        }
        sessionsById.clear();
        sessionsByCode.clear();
    }
}
