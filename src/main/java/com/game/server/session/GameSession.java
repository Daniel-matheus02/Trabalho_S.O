package com.game.server.session;

import com.game.core.model.Card;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.RoundOutcome;
import com.game.core.state.GameEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.DamagePayload;
import com.game.net.protocol.Envelope;
import com.game.net.protocol.ErrorPayload;
import com.game.net.protocol.GameOverPayload;
import com.game.net.protocol.ActionType;
import com.game.net.protocol.MessageCodec;
import com.game.net.protocol.MessageType;
import com.game.net.protocol.PlayerStatusPayload;
import com.game.net.protocol.RoundResultPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.net.protocol.StatePayload;
import com.game.net.protocol.TurnChangedPayload;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Uma sessão de partida autoritativa. Todas as mutações do jogo passam pelo engine
 * sincronizado, de modo que chamadas vindas de diferentes threads do Netty são serializadas.
 */
public final class GameSession {

    private static final Logger LOG = Logger.getLogger(GameSession.class.getName());
    private static final int MAX_RECENT_MESSAGES = 256;

    private final String sessionId;
    private final String code;
    private final MessageCodec codec;
    private final int maxPlayers;

    private final Channel[] channels = new Channel[3];
    private final String[] playerNames = new String[3];
    private final String[] tokens = new String[3];
    private final boolean[] connected = new boolean[3];
    private final long[] disconnectedAt = new long[3];

    private final Set<String> processedMessages = new LinkedHashSet<>();

    private GameEngine engine;
    private boolean started;
    private long lastActivity;
    private int lastBroadcastRound;
    private GamePhase lastBroadcastPhase = GamePhase.WAITING;

    public GameSession(String code, int maxPlayers, MessageCodec codec) {
        this.sessionId = UUID.randomUUID().toString();
        this.code = code;
        this.maxPlayers = maxPlayers;
        this.codec = codec;
        this.lastActivity = System.currentTimeMillis();
    }

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized String code() {
        return code;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized int maxPlayers() {
        return maxPlayers;
    }

    public synchronized String nameOf(int playerId) {
        return playerNames[playerId];
    }

    public synchronized boolean isConnected(int playerId) {
        return connected[playerId];
    }

    public synchronized int playerCount() {
        int count = 0;
        for (int i = 1; i <= maxPlayers; i++) {
            if (playerNames[i] != null) {
                count++;
            }
        }
        return count;
    }

    public synchronized long lastActivity() {
        return lastActivity;
    }

    /**
     * Associa um canal a um jogador. Com um token de reconexão válido, um assento existente é
     * retomado; caso contrário, o primeiro assento livre é utilizado.
     */
    public synchronized SessionInfoPayload join(Channel channel, String playerName, String reconnectToken) {
        return join(channel, playerName, reconnectToken, MessageType.JOIN_ACCEPTED);
    }

    public synchronized SessionInfoPayload join(Channel channel, String playerName, String reconnectToken,
                                                MessageType ackType) {
        int playerId = -1;

        if (reconnectToken != null && !reconnectToken.isBlank()) {
            for (int i = 1; i <= maxPlayers; i++) {
                if (reconnectToken.equals(tokens[i])) {
                    playerId = i;
                    break;
                }
            }
            if (playerId == -1) {
                throw new SessionException("INVALID_TOKEN", "O token de reconexão não pertence a esta sessão");
            }
        }

        boolean reconnecting = playerId != -1;
        if (!reconnecting) {
            for (int i = 1; i <= maxPlayers; i++) {
                if (playerNames[i] == null) {
                    playerId = i;
                    break;
                }
            }
            if (playerId == -1) {
                throw new SessionException("SESSION_FULL", "A sessão " + code + " está cheia");
            }
            playerNames[playerId] = sanitiseName(playerName, "Jogador " + playerId);
            tokens[playerId] = UUID.randomUUID().toString();
        } else {
            String requested = sanitiseName(playerName, playerNames[playerId]);
            if (requested != null && !requested.isBlank()) {
                playerNames[playerId] = requested;
            }
        }

        channels[playerId] = channel;
        connected[playerId] = true;
        disconnectedAt[playerId] = 0;
        lastActivity = System.currentTimeMillis();

        send(playerId, ackType, infoFor(playerId));
        broadcastPresence();
        if (reconnecting && started) {
            send(playerId, MessageType.GAME_START, new StatePayload(engine.snapshotFor(playerId)));
            broadcastState();
        }

        if (!started && playerCount() == maxPlayers) {
            startMatch();
        }
        return infoFor(playerId);
    }

    public synchronized SessionInfoPayload infoFor(int playerId) {
        int opponentId = playerId == 1 ? 2 : 1;
        return new SessionInfoPayload(
                sessionId,
                code,
                playerId,
                playerNames[playerId],
                playerNames[opponentId],
                maxPlayers,
                started,
                tokens[playerId]
        );
    }

    private void startMatch() {
        engine = new GameEngine(sessionId, playerNames[1], playerNames[2]);
        engine.start();
        started = true;
        lastBroadcastRound = 0;
        lastBroadcastPhase = GamePhase.WAITING;
        for (int p = 1; p <= maxPlayers; p++) {
            send(p, MessageType.GAME_START, new StatePayload(engine.snapshotFor(p)));
        }
        broadcastTurn(engine.snapshot());
        broadcastState();
        LOG.info(() -> "Sessão " + code + " iniciada (" + sessionId + ")");
    }

    /** Aplica uma ação de protocolo já validada e transmite as consequências. */
    public synchronized void handleAction(int playerId, Envelope envelope, ActionPayload action) {
        if (!started) {
            sendError(playerId, "SESSION_NOT_STARTED", "A partida ainda não começou");
            return;
        }
        if (processedMessages.contains(envelope.messageId())) {
            // Comando duplicado: não aplicá-lo duas vezes, apenas reenviar o estado atual.
            send(playerId, MessageType.GAME_STATE_UPDATE, new StatePayload(engine.snapshotFor(playerId)));
            return;
        }
        remember(envelope.messageId());

        GameSnapshot before = engine.snapshot();
        try {
            apply(playerId, action);
        } catch (GameRuleException e) {
            sendError(playerId, e.code(), e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            sendError(playerId, "INVALID_CARD", e.getMessage());
            return;
        }

        lastActivity = System.currentTimeMillis();
        GameSnapshot after = engine.snapshot();
        broadcastEvents(before, after);

        if (after.phase() != before.phase() || after.round() != before.round()
                || after.currentPlayer() != before.currentPlayer()) {
            broadcastTurn(after);
        }
        broadcastState();

        if (before.phase() == GamePhase.GAME_OVER && after.phase() == GamePhase.BUILD
                && after.round() == 1) {
            // Revanche aceita: enviar um GAME_START novo para ambos os clientes.
            lastBroadcastRound = 0;
            for (int p = 1; p <= maxPlayers; p++) {
                send(p, MessageType.GAME_START, new StatePayload(engine.snapshotFor(p)));
            }
        }
        lastBroadcastPhase = after.phase();
    }

    private void apply(int playerId, ActionPayload action) {
        switch (action.action()) {
            case SUBMIT_PLAY -> {
                List<Card> cards = new ArrayList<>();
                for (String code : action.cards()) {
                    cards.add(Card.fromCode(code));
                }
                engine.submitPlay(playerId, cards);
            }
            case PICK_JOKER -> {
                if (action.offerIndex() == null) {
                    throw new GameRuleException("INVALID_OFFER", "Índice de oferta ausente");
                }
                engine.pickJoker(playerId, action.offerIndex());
            }
            case REROLL -> engine.reroll(playerId);
            case ADVANCE -> engine.advance(playerId);
            case REMATCH -> engine.requestRematch(playerId);
        }
    }

    private void broadcastEvents(GameSnapshot before, GameSnapshot after) {
        RoundOutcome outcome = after.lastOutcome();
        if (outcome != null && outcome.round() != lastBroadcastRound
                && outcome.round() == after.round()) {
            lastBroadcastRound = outcome.round();
            broadcast(MessageType.ROUND_RESULT, new RoundResultPayload(outcome,
                    after.player1().hp(), after.player2().hp()));
            if (outcome.damage() > 0) {
                int remaining = after.player(outcome.loser()).hp();
                broadcast(MessageType.PLAYER_DAMAGE,
                        new DamagePayload(outcome.loser(), outcome.damage(), remaining));
            }
            if (after.phase() == GamePhase.GAME_OVER) {
                broadcast(MessageType.GAME_OVER, new GameOverPayload(after.winner(),
                        after.player1().hp(), after.player2().hp(), after.round()));
            }
        }
    }

    private void broadcastTurn(GameSnapshot state) {
        broadcast(MessageType.TURN_CHANGED, new TurnChangedPayload(
                state.round(), state.phase(), state.currentPlayer(), state.version()));
    }

    public synchronized void broadcastState() {
        for (int p = 1; p <= maxPlayers; p++) {
            if (channels[p] != null && connected[p]) {
                send(p, MessageType.GAME_STATE_UPDATE, new StatePayload(engine.snapshotFor(p)));
            }
        }
    }

    private void broadcastPresence() {
        for (int p = 1; p <= maxPlayers; p++) {
            if (playerNames[p] == null) {
                continue;
            }
            int hp = engine != null ? engine.hpOf(p) : 0;
            PlayerStatusPayload status = new PlayerStatusPayload(p, playerNames[p], hp, connected[p], false);
            for (int target = 1; target <= maxPlayers; target++) {
                if (channels[target] != null && connected[target]) {
                    send(target, connected[p] ? MessageType.PLAYER_CONNECTED : MessageType.PLAYER_DISCONNECTED, status);
                }
            }
        }
    }

    public synchronized void playerDisconnected(int playerId) {
        if (playerId < 1 || playerId > maxPlayers) {
            return;
        }
        connected[playerId] = false;
        channels[playerId] = null;
        disconnectedAt[playerId] = System.currentTimeMillis();
        lastActivity = System.currentTimeMillis();
        int hp = engine != null ? engine.hpOf(playerId) : 0;
        PlayerStatusPayload status = new PlayerStatusPayload(playerId, playerNames[playerId], hp, false, false);
        broadcast(MessageType.PLAYER_DISCONNECTED, status);
        LOG.info(() -> "Jogador " + playerId + " desconectado da sessão " + code);
    }

    /** Retorna true quando a sessão deve ser removida pelo gerenciador. */
    public synchronized boolean expireIfIdle(long now, long timeoutMillis) {
        for (int i = 1; i <= maxPlayers; i++) {
            if (playerNames[i] != null && !connected[i]) {
                if (now - disconnectedAt[i] > timeoutMillis) {
                    close("SESSION_TIMEOUT", "A sessão expirou após um jogador permanecer desconectado");
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized void close(String code, String message) {
        LOG.info(() -> "Encerrando a sessão " + this.code + ": " + message);
        for (int p = 1; p <= maxPlayers; p++) {
            if (channels[p] != null && channels[p].isActive()) {
                send(p, MessageType.ERROR, new ErrorPayload(code, message));
                channels[p].close();
            }
            channels[p] = null;
            connected[p] = false;
        }
    }

    public synchronized void send(int playerId, MessageType type, Object payload) {
        Channel channel = channels[playerId];
        if (channel == null || !channel.isActive()) {
            return;
        }
        channel.writeAndFlush(codec.encode(codec.message(type, sessionId, playerId, payload)))
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        LOG.warning(() -> "Falha ao enviar " + type + " para o jogador " + playerId
                                + ": " + future.cause());
                    }
                });
    }

    private void broadcast(MessageType type, Object payload) {
        for (int p = 1; p <= maxPlayers; p++) {
            if (connected[p]) {
                send(p, type, payload);
            }
        }
    }

    private void sendError(int playerId, String code, String message) {
        send(playerId, MessageType.ERROR, new ErrorPayload(code, message));
    }

    private void remember(String messageId) {
        processedMessages.add(messageId);
        if (processedMessages.size() > MAX_RECENT_MESSAGES) {
            var iterator = processedMessages.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static String sanitiseName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String trimmed = name.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }
}
