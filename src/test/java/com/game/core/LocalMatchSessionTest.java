package com.game.core;

import com.game.client.controller.LocalMatchSession;
import com.game.core.rules.GameRules;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercita a sessão de um jogador de ponta a ponta, incluindo as decisões de poder da IA. */
class LocalMatchSessionTest {

    @Test
    @DisplayName("partida de um jogador chega ao fim de jogo com o bot jogando e escolhendo poderes")
    void matchReachesGameOver() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                GameRules.DEFAULT, new Random(3));

        int guard = 0;
        while (session.snapshot().phase() != GamePhase.GAME_OVER && guard++ < 10000) {
            GameSnapshot state = session.snapshot();
            switch (state.phase()) {
                case BUILD -> {
                    if (state.currentPlayer() == 1) {
                        session.submitPlay(state.player(1).hand().subList(0, 5));
                    } else {
                        session.tick(1.2f);
                    }
                }
                case SABOTAGE -> {
                    if (!state.player(1).powersLocked()) {
                        session.lockPowers(List.of());
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
                default -> session.tick(1.2f);
            }
        }

        GameSnapshot finalState = session.snapshot();
        assertEquals(GamePhase.GAME_OVER, finalState.phase());
        assertTrue(finalState.winner() == 1 || finalState.winner() == 2);
        assertEquals(0, finalState.player(finalState.winner() == 1 ? 2 : 1).hp());
    }

    @Test
    @DisplayName("a CPU trava os poderes e a próxima rodada começa sem poderes antigos")
    void botLocksPowersAndNextRoundStarts() {
        LocalMatchSession session = new LocalMatchSession("Tester",
                new GameRules(300, 8, 5, 3, 4, 3, 2), new Random(11));

        session.submitPlay(session.snapshot().player(1).hand().subList(0, 5));
        tickUntil(session, GamePhase.SABOTAGE);

        GameSnapshot powers = session.snapshot();
        assertEquals(7, powers.player(1).powerHand().size());
        assertEquals(7, powers.player(1).powerCount());
        assertEquals(7, powers.player(2).powerCount());
        assertTrue(powers.player(2).powerHand().isEmpty(), "a CPU oculta seus poderes");

        session.lockPowers(List.of(0, 1));
        tickUntil(session, GamePhase.REVEAL);

        GameSnapshot reveal = session.snapshot();
        assertNotNull(reveal.lastOutcome());
        assertEquals(2, reveal.lastOutcome().player1Powers().size());

        session.advance();
        tickUntil(session, GamePhase.BUILD);
        GameSnapshot next = session.snapshot();
        assertEquals(2, next.round());
        assertEquals(8, next.player(1).hand().size());
        assertEquals(0, next.player(1).powerCount());
        assertTrue(next.player(1).playedPowers().isEmpty());
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase) {
        for (int i = 0; i < 120 && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
