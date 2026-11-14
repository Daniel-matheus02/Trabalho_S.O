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
    private final BotBrain bot = new BotBrain(new ScoringEngine(), 2);

    private float timer;
    private String signature = "";
    private String shopVisit = "";
    private boolean botRerolledThisShop;

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
    public void useSabotage(int handIndex) {
        engine.useSabotage(GameEngine.PLAYER_ONE, handIndex);
    }

    @Override
    public void passSabotage() {
        engine.passSabotage(GameEngine.PLAYER_ONE);
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
    public void buySabotage(int offerIndex) {
        engine.buySabotage(GameEngine.PLAYER_ONE, offerIndex);
    }

    @Override
    public void ready() {
        engine.ready(GameEngine.PLAYER_ONE);
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
        PlayerView cpu = state.player(GameEngine.PLAYER_TWO);
        String currentSignature = state.phase() + ":" + state.round() + ":"
                + state.currentPlayer() + ":" + cpu.sabotageChosen() + ":"
                + cpu.sabotageCount() + ":" + cpu.jokerPicked() + ":" + cpu.shopReady();
        if (!currentSignature.equals(signature)) {
            signature = currentSignature;
            timer = state.phase() == GamePhase.REVEAL ? REVEAL_TIME : THINK_TIME;
            if (state.phase() == GamePhase.SHOP) {
                String visit = state.round() + ":" + cpu.offers().hashCode();
                if (!visit.equals(shopVisit)) {
                    shopVisit = visit;
                    botRerolledThisShop = false;
                }
            }
        }

        switch (state.phase()) {
            case BUILD -> {
                if (state.currentPlayer() == GameEngine.PLAYER_TWO) {
                    timer -= delta;
                    if (timer <= 0) {
                        List<Card> hand = engine.handOf(GameEngine.PLAYER_TWO);
                        List<Card> play = bot.choosePlay(hand,
                                engine.jokersOf(GameEngine.PLAYER_TWO),
                                state.globalModifierId(), state.community());
                        engine.submitPlay(GameEngine.PLAYER_TWO, play);
                    }
                }
            }
            case SABOTAGE -> {
                if (!cpu.sabotageChosen()) {
                    timer -= delta;
                    if (timer <= 0) {
                        List<String> hand = engine.sabotageHandOf(GameEngine.PLAYER_TWO);
                        engine.useSabotage(GameEngine.PLAYER_TWO, bot.chooseSabotage(hand));
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
            case SHOP -> tickShop(state, cpu, delta);
            default -> {
                // Nada para o bot fazer.
            }
        }
    }

    private void tickShop(GameSnapshot state, PlayerView cpu, float delta) {
        timer -= delta;
        if (timer > 0) {
            return;
        }
        if (!cpu.jokerPicked()) {
            List<Card> hand = engine.handOf(GameEngine.PLAYER_TWO);
            List<String> jokers = engine.jokersOf(GameEngine.PLAYER_TWO);
            List<String> offers = cpu.offers();
            if (!botRerolledThisShop
                    && bot.shouldReroll(offers, hand, jokers, state.globalModifierId(),
                    cpu.rerolls(), state.community())) {
                botRerolledThisShop = true;
                engine.reroll(GameEngine.PLAYER_TWO);
                return;
            }
            int index = bot.chooseJoker(offers, hand, jokers, state.globalModifierId(),
                    state.community());
            if (index >= 0) {
                engine.pickJoker(GameEngine.PLAYER_TWO, index);
            }
            return;
        }
        if (bot.shouldBuySabotage(cpu.coins(), state.rules().sabotagePrice(),
                cpu.sabotageCount(), state.rules().maxSabotages())
                && !cpu.sabotageOffers().isEmpty()) {
            engine.buySabotage(GameEngine.PLAYER_TWO, 0);
            return;
        }
        if (!cpu.shopReady()) {
            engine.ready(GameEngine.PLAYER_TWO);
        }
    }

    @Override
    public String statusMessage() {
        GameSnapshot state = snapshot();
        if (state.phase() == GamePhase.BUILD && state.currentPlayer() == GameEngine.PLAYER_TWO) {
            return "CPU pensando...";
        }
        if (state.phase() == GamePhase.SABOTAGE && !state.player(GameEngine.PLAYER_TWO).sabotageChosen()) {
            return "CPU escolhendo uma sabotagem...";
        }
        if (state.phase() == GamePhase.SHOP && !state.player(GameEngine.PLAYER_TWO).shopReady()) {
            return "CPU comprando na loja...";
        }
        return "";
    }

    @Override
    public void close() {
        // Nenhum recurso a liberar.
    }
}
