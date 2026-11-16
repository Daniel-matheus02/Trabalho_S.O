package com.game.core;

import com.game.ai.BotBrain;
import com.game.core.model.Card;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoreStep;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GameEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Varredura de estabilidade: 200 partidas CPU contra CPU com sementes fixas, jogadas apenas com
 * as APIs públicas de {@link GameEngine} e {@link BotBrain}. Cada partida precisa terminar, a vida
 * nunca pode ultrapassar o máximo, nenhum jogador pode jogar mais que
 * {@link GameRules#maxPowersPlayed()}, o motor não pode lançar {@link GameRuleException} e os
 * {@link ScoreStep} de toda mão pontuada precisam fechar no placar real.
 */
class CpuSweepTest {

    private static final int MATCHES = 200;
    private static final int MAX_STEPS = 5_000;

    @Test
    void twoHundredSeededCpuMatchesKeepAllInvariants() {
        for (int seed = 1; seed <= MATCHES; seed++) {
            try {
                playSeededMatch(seed);
            } catch (GameRuleException e) {
                fail("seed " + seed + " lançou GameRuleException: " + e.getMessage(), e);
            }
        }
    }

    private static void playSeededMatch(int seed) {
        GameRules rules = GameRules.DEFAULT;
        GameEngine engine = new GameEngine("sweep-" + seed, "CPU 1", "CPU 2",
                new Random(seed), rules, new ScoringEngine());
        BotBrain bot = new BotBrain(new ScoringEngine(), rules.maxPlay());
        engine.start();
        assertHpWithinBounds(seed, engine);

        int steps = 0;
        while (engine.phase() != GamePhase.GAME_OVER) {
            if (++steps > MAX_STEPS) {
                fail("seed " + seed + " não terminou após " + MAX_STEPS + " passos (rodada "
                        + engine.round() + ")");
            }
            GameSnapshot state = engine.snapshot();
            switch (state.phase()) {
                case BUILD -> {
                    int player = state.currentPlayer();
                    List<Card> hand = engine.handOf(player);
                    engine.submitPlay(player, bot.choosePlay(hand, state.globalModifierId()));
                }
                case SABOTAGE -> {
                    for (int player : new int[]{GameEngine.PLAYER_ONE, GameEngine.PLAYER_TWO}) {
                        if (engine.snapshot().player(player).powersLocked()) {
                            continue;
                        }
                        engine.lockPowers(player,
                                bot.choosePowers(engine.snapshotFor(player), player));
                    }
                }
                case REVEAL -> {
                    assertOutcomeInvariants(seed, state, rules);
                    for (int player : new int[]{GameEngine.PLAYER_ONE, GameEngine.PLAYER_TWO}) {
                        if (!engine.snapshot().player(player).ready()) {
                            engine.advance(player);
                        }
                    }
                }
                default -> fail("seed " + seed + " parou na fase " + state.phase());
            }
            assertHpWithinBounds(seed, engine);
        }

        assertHpWithinBounds(seed, engine);
        assertNotEquals(0, engine.winner(), "seed " + seed + " terminou sem vencedor");
    }

    private static void assertOutcomeInvariants(int seed, GameSnapshot state, GameRules rules) {
        RoundOutcome outcome = state.lastOutcome();
        assertNotNull(outcome, "seed " + seed + ": resultado ausente na revelação");
        assertTrue(outcome.player1Powers().size() <= rules.maxPowersPlayed(),
                "seed " + seed + ": jogador 1 jogou " + outcome.player1Powers().size()
                        + " poderes (máx. " + rules.maxPowersPlayed() + ")");
        assertTrue(outcome.player2Powers().size() <= rules.maxPowersPlayed(),
                "seed " + seed + ": jogador 2 jogou " + outcome.player2Powers().size()
                        + " poderes (máx. " + rules.maxPowersPlayed() + ")");
        assertScoreInvariant(seed, outcome.player1Score(), "jogador 1");
        assertScoreInvariant(seed, outcome.player2Score(), "jogador 2");
        assertScoreInvariant(seed, state.player(1).tableScore(), "mesa do jogador 1");
        assertScoreInvariant(seed, state.player(2).tableScore(), "mesa do jogador 2");
    }

    private static void assertScoreInvariant(int seed, ScoreResult result, String label) {
        assertNotNull(result, "seed " + seed + ": " + label + " sem placar");
        List<ScoreStep> steps = result.steps();
        assertFalse(steps.isEmpty(), "seed " + seed + ": " + label + " sem passos");
        ScoreStep last = steps.get(steps.size() - 1);
        assertEquals(result.chips(), last.chipsAfter(),
                "seed " + seed + ": " + label + " fichas divergem do último passo");
        assertEquals(result.mult(), last.multAfter(),
                "seed " + seed + ": " + label + " mult diverge do último passo");
        assertEquals((int) Math.round((double) result.chips() * result.mult()),
                result.finalScore(), "seed " + seed + ": " + label + " finalScore inválido");
    }

    private static void assertHpWithinBounds(int seed, GameEngine engine) {
        int maxHp = engine.rules().startingHp();
        for (int player : new int[]{GameEngine.PLAYER_ONE, GameEngine.PLAYER_TWO}) {
            int hp = engine.hpOf(player);
            assertTrue(hp <= maxHp,
                    "seed " + seed + ": HP " + hp + " acima do máximo " + maxHp);
            assertTrue(hp >= 0, "seed " + seed + ": HP negativo (" + hp + ")");
        }
    }
}
