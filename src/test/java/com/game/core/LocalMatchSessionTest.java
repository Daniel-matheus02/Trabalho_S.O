package com.game.core;

import com.game.client.controller.LocalMatchSession;
import com.game.core.rules.GameRules;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercita a sessão de um jogador de ponta a ponta, incluindo as decisões de loja da IA. */
class LocalMatchSessionTest {

    @Test
    @DisplayName("partida de um jogador chega ao fim de jogo com o bot jogando e comprando")
    void matchReachesGameOver() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                new GameRules(300, 8, 2, 3, 2), new Random(3));

        int guard = 0;
        while (session.snapshot().phase() != GamePhase.GAME_OVER && guard++ < 10000) {
            GameSnapshot state = session.snapshot();
            switch (state.phase()) {
                case BUILD -> {
                    if (state.currentPlayer() == 1) {
                        session.submitPlay(state.player(1).hand().subList(0, 2));
                    } else {
                        session.tick(1.2f);
                    }
                }
                case SABOTAGE -> {
                    if (!state.player(1).sabotageChosen()) {
                        session.passSabotage();
                    } else {
                        session.tick(1.2f);
                    }
                }
                case REVEAL -> {
                    if (!state.player(1).ready()) {
                        session.advance();
                    } else {
                        session.tick(1.2f);
                    }
                }
                case SHOP -> {
                    if (!state.player(1).jokerPicked()) {
                        session.pickJoker(0);
                    } else if (!state.player(1).shopReady()) {
                        session.ready();
                    } else {
                        session.tick(1.2f);
                    }
                }
                default -> session.tick(1.2f);
            }
        }

        GameSnapshot finalState = session.snapshot();
        assertEquals(GamePhase.GAME_OVER, finalState.phase());
        assertTrue(finalState.winner() == 1 || finalState.winner() == 2);
        assertTrue(finalState.player(1).jokers().size() > 0, "o humano deveria ter passado pela loja");
        assertTrue(finalState.player(2).jokers().size() > 0, "a CPU deveria ter passado pela loja");
    }

    @Test
    @DisplayName("a CPU escolhe um joker e a próxima rodada começa")
    void botShopCompletesRound() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                new GameRules(300, 8, 2, 3, 2), new Random(11));

        session.submitPlay(session.snapshot().player(1).hand().subList(0, 2));
        for (int i = 0; i < 60 && session.snapshot().phase() != GamePhase.SABOTAGE; i++) {
            session.tick(1.2f);
        }
        assertEquals(GamePhase.SABOTAGE, session.snapshot().phase());
        session.passSabotage();
        for (int i = 0; i < 60 && session.snapshot().phase() != GamePhase.REVEAL; i++) {
            session.tick(1.2f);
        }
        assertEquals(GamePhase.REVEAL, session.snapshot().phase());
        session.advance();
        for (int i = 0; i < 60 && session.snapshot().phase() != GamePhase.SHOP; i++) {
            session.tick(1.2f);
        }
        assertEquals(GamePhase.SHOP, session.snapshot().phase());
        session.pickJoker(0);
        session.ready();
        for (int i = 0; i < 120 && session.snapshot().phase() == GamePhase.SHOP; i++) {
            session.tick(1.2f);
        }
        GameSnapshot state = session.snapshot();
        assertEquals(GamePhase.BUILD, state.phase());
        assertEquals(2, state.round());
        assertEquals(1, state.player(2).jokers().size());
    }
}
