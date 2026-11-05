package com.game.core;

import com.game.client.controller.LocalMatchSession;
import com.game.core.state.GamePhase;
import com.game.core.rules.GameRules;
import com.game.core.state.GameSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercita a sessão de um jogador de ponta a ponta, incluindo as decisões de draft da IA. */
class LocalMatchSessionTest {

    @Test
    @DisplayName("partida de um jogador chega ao fim de jogo com o bot escolhendo curingas")
    void matchReachesGameOver() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                new GameRules(300, 8, 5, 3, 2), new Random(3));

        int guard = 0;
        while (session.snapshot().phase() != GamePhase.GAME_OVER && guard++ < 5000) {
            GameSnapshot state = session.snapshot();
            switch (state.phase()) {
                case BUILD -> {
                    if (state.currentPlayer() == 1) {
                        session.submitPlay(state.player(1).hand().subList(0, 5));
                    } else {
                        session.tick(1.2f);
                    }
                }
                case REVEAL -> {
                    session.advance();
                    session.tick(1.2f);
                }
                case DRAFT -> {
                    if (state.currentPlayer() == 1) {
                        session.pickJoker(0);
                    } else {
                        session.tick(1.2f);
                    }
                }
                default -> {
                }
            }
        }

        GameSnapshot finalState = session.snapshot();
        assertEquals(GamePhase.GAME_OVER, finalState.phase());
        assertTrue(finalState.winner() == 1 || finalState.winner() == 2);
        assertTrue(finalState.player(1).jokers().size() > 0, "o humano deveria ter feito o draft");
        assertTrue(finalState.player(2).jokers().size() > 0, "a CPU deveria ter feito o draft");
    }

    @Test
    @DisplayName("a CPU escolhe um curinga e a próxima rodada começa")
    void botDraftCompletesRound() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                new GameRules(300, 8, 5, 3, 2), new Random(11));

        session.submitPlay(session.snapshot().player(1).hand().subList(0, 3));
        for (int i = 0; i < 40 && session.snapshot().phase() != GamePhase.REVEAL; i++) {
            session.tick(1.2f);
        }
        assertEquals(GamePhase.REVEAL, session.snapshot().phase());
        session.advance();
        for (int i = 0; i < 40 && session.snapshot().phase() != GamePhase.DRAFT; i++) {
            session.tick(1.2f);
        }
        assertEquals(GamePhase.DRAFT, session.snapshot().phase());
        session.pickJoker(0);
        for (int i = 0; i < 40 && session.snapshot().phase() == GamePhase.DRAFT; i++) {
            session.tick(1.2f);
        }
        GameSnapshot state = session.snapshot();
        assertEquals(GamePhase.BUILD, state.phase());
        assertEquals(2, state.round());
        assertEquals(1, state.player(2).jokers().size());
    }
}
