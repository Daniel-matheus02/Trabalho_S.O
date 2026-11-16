package com.game.client.controller;

import com.game.ai.BotBrain;
import com.game.core.model.Card;
import com.game.core.rules.GameRules;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GameEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.List;
import java.util.UUID;

/**
 * Sessão para um jogador. Possui um motor autoritativo local além de um bot que age através
 * do mesmo motor, com atrasos semelhantes aos humanos. A renderização nunca vê as cartas ou os
 * poderes ocultos do bot.
 */
public final class LocalMatchSession implements MatchSession {

    private static final float THINK_TIME = 0.85f;
    private static final float REVEAL_TIME = 1.1f;

    private final GameEngine engine;
    private final BotBrain bot;

    private float timer;
    private String signature = "";

    public LocalMatchSession(String playerName) {
        this(playerName, GameRules.DEFAULT, new java.util.Random());
    }

    public LocalMatchSession(String playerName, GameRules rules, java.util.Random random) {
        this.engine = new GameEngine(UUID.randomUUID().toString(),
                playerName == null || playerName.isBlank() ? "Jogador" : playerName, "CPU",
                random, rules, new ScoringEngine());
        this.bot = new BotBrain(new ScoringEngine(), rules.maxPlay());
        this.engine.start();
    }

    @Override
    public int myPlayerId() {
        return GameEngine.PLAYER_ONE;
    }

    @Override
    public GameSnapshot snapshot() {
        return engine.snapshotFor(GameEngine.PLAYER_ONE);
    }

    @Override
    public boolean isSinglePlayer() {
        return true;
    }

    @Override
    public void submitPlay(List<Card> cards) {
        engine.submitPlay(GameEngine.PLAYER_ONE, cards);
    }

    @Override
    public void discardCards(List<Card> cards) {
        engine.discardCards(GameEngine.PLAYER_ONE, cards);
    }

    @Override
    public void lockPowers(List<Integer> powerIndices) {
        engine.lockPowers(GameEngine.PLAYER_ONE, powerIndices);
    }

    /** DevPreview: redistribui a mão de poderes do humano sem travar a escolha. */
    public void redealPowers() {
        engine.redealPowersForPreview(GameEngine.PLAYER_ONE);
    }

    @Override
    public void advance() {
        engine.advance(GameEngine.PLAYER_ONE);
    }

    @Override
    public void requestRematch() {
        if (engine.phase() == GamePhase.GAME_OVER) {
            engine.requestRematch(GameEngine.PLAYER_ONE);
            engine.requestRematch(GameEngine.PLAYER_TWO);
        }
    }

    @Override
    public void tick(float delta) {
        // O bot precisa usar sua própria visão filtrada: o instantâneo do humano oculta os poderes da CPU.
        GameSnapshot state = engine.snapshotFor(GameEngine.PLAYER_TWO);
        PlayerView cpu = state.player(GameEngine.PLAYER_TWO);
        String currentSignature = state.phase() + ":" + state.round() + ":"
                + state.currentPlayer() + ":" + cpu.powersLocked() + ":"
                + cpu.powerCount() + ":" + cpu.discardsRemaining();
        if (!currentSignature.equals(signature)) {
            signature = currentSignature;
            timer = state.phase() == GamePhase.REVEAL ? REVEAL_TIME : THINK_TIME;
        }

        switch (state.phase()) {
            case BUILD -> {
                if (state.currentPlayer() == GameEngine.PLAYER_TWO) {
                    timer -= delta;
                    if (timer <= 0) {
                        List<Card> discards = bot.chooseDiscard(state, GameEngine.PLAYER_TWO);
                        if (!discards.isEmpty()) {
                            engine.discardCards(GameEngine.PLAYER_TWO, discards);
                        }
                        List<Card> hand = engine.handOf(GameEngine.PLAYER_TWO);
                        engine.submitPlay(GameEngine.PLAYER_TWO,
                                bot.choosePlay(hand, state.globalModifierId()));
                    }
                }
            }
            case SABOTAGE -> {
                if (!cpu.powersLocked()) {
                    timer -= delta;
                    if (timer <= 0) {
                        List<Integer> powers = bot.choosePowers(state, GameEngine.PLAYER_TWO);
                        engine.lockPowers(GameEngine.PLAYER_TWO, powers);
                    }
                }
            }
            case REVEAL -> {
                if (!cpu.ready()) {
                    timer -= delta;
                    if (timer <= 0) {
                        engine.advance(GameEngine.PLAYER_TWO);
                    }
                }
            }
            default -> {
                // Nada para o bot fazer.
            }
        }
    }

    @Override
    public String statusMessage() {
        GameSnapshot state = snapshot();
        if (state.phase() == GamePhase.BUILD && state.currentPlayer() == GameEngine.PLAYER_TWO) {
            return "CPU pensando...";
        }
        if (state.phase() == GamePhase.SABOTAGE
                && !state.player(GameEngine.PLAYER_TWO).powersLocked()) {
            return "CPU escolhendo poderes...";
        }
        return "";
    }

    @Override
    public void close() {
        // Nenhum recurso a liberar.
    }
}
