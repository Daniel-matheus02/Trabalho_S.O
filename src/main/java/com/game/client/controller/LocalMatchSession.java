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
 * do mesmo motor, com atrasos semelhantes aos humanos. A renderização nunca vê as cartas ocultas do bot.
 */
public final class LocalMatchSession implements MatchSession {

    private static final float THINK_TIME = 0.85f;
    private static final float REVEAL_TIME = 1.1f;

    private final GameEngine engine;
    private final BotBrain bot = new BotBrain(new ScoringEngine(), 5);

    private float timer;
    private String signature = "";
    private boolean botRerolledThisDraft;

    public LocalMatchSession(String playerName) {
        this(playerName, GameRules.DEFAULT, new java.util.Random());
    }

    public LocalMatchSession(String playerName, GameRules rules, java.util.Random random) {
        this.engine = new GameEngine(UUID.randomUUID().toString(),
                playerName == null || playerName.isBlank() ? "Jogador" : playerName, "CPU",
                random, rules, new ScoringEngine());
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
    public void pickJoker(int offerIndex) {
        engine.pickJoker(GameEngine.PLAYER_ONE, offerIndex);
    }

    @Override
    public void reroll() {
        engine.reroll(GameEngine.PLAYER_ONE);
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
        // O bot precisa usar sua própria visão filtrada: o instantâneo do humano oculta as ofertas da CPU.
        GameSnapshot state = engine.snapshotFor(GameEngine.PLAYER_TWO);
        String currentSignature = state.phase() + ":" + state.round() + ":" + state.currentPlayer();
        if (!currentSignature.equals(signature)) {
            signature = currentSignature;
            timer = state.phase() == GamePhase.REVEAL ? REVEAL_TIME : THINK_TIME;
            if (state.phase() == GamePhase.DRAFT) {
                botRerolledThisDraft = false;
            }
        }

        switch (state.phase()) {
            case BUILD -> {
                if (state.currentPlayer() == GameEngine.PLAYER_TWO) {
                    timer -= delta;
                    if (timer <= 0) {
                        List<Card> hand = engine.handOf(GameEngine.PLAYER_TWO);
                        List<Card> play = bot.choosePlay(hand,
                                engine.jokersOf(GameEngine.PLAYER_TWO), state.globalModifierId());
                        engine.submitPlay(GameEngine.PLAYER_TWO, play);
                    }
                }
            }
            case DRAFT -> {
                if (state.currentPlayer() == GameEngine.PLAYER_TWO) {
                    timer -= delta;
                    if (timer <= 0) {
                        PlayerView view = state.player(GameEngine.PLAYER_TWO);
                        List<Card> hand = engine.handOf(GameEngine.PLAYER_TWO);
                        List<String> jokers = engine.jokersOf(GameEngine.PLAYER_TWO);
                        if (!botRerolledThisDraft
                                && bot.shouldReroll(view.offers(), hand, jokers,
                                state.globalModifierId(), view.rerolls())) {
                            botRerolledThisDraft = true;
                            engine.reroll(GameEngine.PLAYER_TWO);
                            timer = THINK_TIME;
                            return;
                        }
                        int index = bot.chooseJoker(view.offers(), hand, jokers, state.globalModifierId());
                        if (index >= 0) {
                            engine.pickJoker(GameEngine.PLAYER_TWO, index);
                        }
                    }
                }
            }
            case REVEAL -> {
                if (!state.player(GameEngine.PLAYER_TWO).ready()) {
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
        if (state.phase() == GamePhase.BUILD && state.currentPlayer() == 2) {
            return "CPU pensando...";
        }
        if (state.phase() == GamePhase.DRAFT && state.currentPlayer() == 2) {
            return "CPU escolhendo um coringa...";
        }
        return "";
    }

    @Override
    public void close() {
        // Nenhum recurso a liberar.
    }
}
