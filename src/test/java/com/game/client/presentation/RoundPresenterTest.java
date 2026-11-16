package com.game.client.presentation;

import com.game.core.model.Card;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRules;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundPresenterTest {

    private final ScoringEngine scoring = new ScoringEngine();
    private final GameRules rules = GameRules.DEFAULT;

    @Test
    void tableCountBlocksThePowerHandUntilItFinishes() {
        FakeCount count = new FakeCount();
        RoundPresenter presenter = presenter(count, false);
        presenter.onSnapshot(sabotageSnapshot(1));

        assertEquals(1, count.plays);
        assertFalse(presenter.powerHandReady());
        assertTrue(presenter.isCounting());

        count.complete();
        assertTrue(presenter.powerHandReady());
        assertFalse(presenter.isCounting());
    }

    @Test
    void aNewSnapshotFinishesTheRunningCountInstantly() {
        FakeCount count = new FakeCount();
        RoundPresenter presenter = presenter(count, false);
        presenter.onSnapshot(sabotageSnapshot(1));
        assertTrue(presenter.isCounting());

        presenter.onSnapshot(sabotageSnapshot(2));
        assertTrue(count.finishNowCalls >= 1);
        assertTrue(presenter.powerHandReady());
        assertFalse(presenter.isCounting());
    }

    @Test
    void finalCountHoldsThePreviousHpAndThenRunsDamage() {
        FakeCount count = new FakeCount();
        int[] damageRuns = {0};
        RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { },
                () -> damageRuns[0]++);

        presenter.onSnapshot(sabotageSnapshot(1));
        count.complete();
        presenter.onSnapshot(revealSnapshot(2));

        assertTrue(presenter.isFinalCounting());
        assertEquals(300, presenter.displayedHp(1, 250),
                "a vida exibida continua no valor anterior durante a contagem final");
        assertEquals(0, damageRuns[0]);

        count.complete();
        assertFalse(presenter.isFinalCounting());
        assertEquals(250, presenter.displayedHp(1, 250));
        assertEquals(1, damageRuns[0]);
    }

    @Test
    void directRevealReconstructsThePreviousHpFromTheOutcome() {
        FakeCount count = new FakeCount();
        int[] damageRuns = {0};
        RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { },
                () -> damageRuns[0]++);

        presenter.onSnapshot(revealSnapshot(1));
        assertTrue(presenter.isFinalCounting());
        assertEquals(300, presenter.displayedHp(1, 250),
                "sem instantâneo anterior, a vida é reconstruída pelo dano");
        count.complete();
        assertEquals(250, presenter.displayedHp(1, 250));
        assertEquals(1, damageRuns[0]);
    }

    @Test
    void reducedMotionFinishesBothCountsImmediately() {
        FakeCount count = new FakeCount();
        int[] damageRuns = {0};
        RoundPresenter presenter = new RoundPresenter(1, true, count, () -> { },
                () -> damageRuns[0]++);

        presenter.onSnapshot(sabotageSnapshot(1));
        assertTrue(presenter.powerHandReady());
        assertFalse(presenter.isCounting());

        presenter.onSnapshot(revealSnapshot(2));
        assertFalse(presenter.isFinalCounting());
        assertEquals(1, damageRuns[0]);
        assertEquals(250, presenter.displayedHp(1, 250));
    }

    @Test
    void replayDoesNotApplyDamageAgain() {
        FakeCount count = new FakeCount();
        int[] damageRuns = {0};
        RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { },
                () -> damageRuns[0]++);

        presenter.onSnapshot(sabotageSnapshot(1));
        count.complete();
        presenter.onSnapshot(revealSnapshot(2));
        count.complete();
        assertEquals(1, damageRuns[0]);

        presenter.replayLastCount();
        assertEquals(3, count.plays);
        count.complete();
        assertEquals(1, damageRuns[0], "o replay não deve reaplicar o dano");
    }

    @Test
    void burnStartsAfterTheTableCountAndRevealsThePowerHandOnlyAtTheEnd() {
        FakeCount count = new FakeCount();
        FakeBurn burn = new FakeBurn();
        RoundPresenter presenter = presenter(count, burn, false);
        presenter.onSnapshot(sabotageSnapshot("burn-start", 1));

        count.complete();
        assertTrue(presenter.isBurning());
        assertFalse(presenter.powerHandReady());
        assertEquals(1, burn.plays);

        burn.complete();
        assertFalse(presenter.isBurning());
        assertTrue(presenter.powerHandReady());
    }

    @Test
    void skippingTheBurnRevealsThePowerHandImmediately() {
        FakeCount count = new FakeCount();
        FakeBurn burn = new FakeBurn();
        RoundPresenter presenter = presenter(count, burn, false);
        presenter.onSnapshot(sabotageSnapshot("burn-skip", 1));
        count.complete();
        assertTrue(presenter.isBurning());

        presenter.skip();
        assertTrue(burn.finishNowCalls >= 1);
        assertFalse(presenter.isBurning());
        assertTrue(presenter.powerHandReady());
    }

    @Test
    void aNewSnapshotFinishesTheBurn() {
        FakeCount count = new FakeCount();
        FakeBurn burn = new FakeBurn();
        RoundPresenter presenter = presenter(count, burn, false);
        presenter.onSnapshot(sabotageSnapshot("burn-snapshot", 1));
        count.complete();
        assertTrue(presenter.isBurning());

        presenter.onSnapshot(sabotageSnapshot("burn-snapshot", 2));
        assertTrue(burn.finishNowCalls >= 1);
        assertTrue(presenter.powerHandReady());
        assertFalse(presenter.isBurning());
    }

    @Test
    void burnIsOncePerRoundAcrossPresenterInstances() {
        FakeCount count1 = new FakeCount();
        FakeBurn burn1 = new FakeBurn();
        RoundPresenter first = presenter(count1, burn1, false);
        first.onSnapshot(sabotageSnapshot("burn-guard", 1));
        count1.complete();
        burn1.complete();

        FakeCount count2 = new FakeCount();
        FakeBurn burn2 = new FakeBurn();
        RoundPresenter second = presenter(count2, burn2, false);
        second.onSnapshot(sabotageSnapshot("burn-guard", 2));
        count2.complete();

        assertTrue(second.powerHandReady());
        assertEquals(0, burn2.plays, "a rodada já queimou; redraw/reconexão não repete");
    }

    @Test
    void reducedMotionSkipsTheBurn() {
        FakeCount count = new FakeCount();
        FakeBurn burn = new FakeBurn();
        RoundPresenter presenter = presenter(count, burn, true);
        presenter.onSnapshot(sabotageSnapshot("burn-reduced", 1));

        assertTrue(presenter.powerHandReady());
        assertFalse(presenter.isBurning());
        assertEquals(0, burn.plays);
    }

    @Test
    void resolutionRunsBetweenInputAndFinalCount() {
        FakeCount count = new FakeCount();
        FakeBurn burn = new FakeBurn();
        FakeResolution resolution = new FakeResolution();
        RoundPresenter presenter = presenter(count, burn, resolution, false);
        presenter.onSnapshot(sabotageSnapshot("res-start", 1));
        count.complete();
        burn.complete();

        presenter.onSnapshot(revealSnapshot(2));
        assertTrue(presenter.isResolving());
        assertFalse(presenter.isFinalCounting());
        assertEquals(1, resolution.plays);

        resolution.complete();
        assertFalse(presenter.isResolving());
        assertTrue(presenter.isFinalCounting());
    }

    @Test
    void skippingResolutionFinishesItAndTheFinalCount() {
        FakeCount count = new FakeCount();
        FakeResolution resolution = new FakeResolution();
        RoundPresenter presenter = presenter(count, new FakeBurn(), resolution, false);
        presenter.onSnapshot(revealSnapshot(1));
        assertTrue(presenter.isResolving());

        presenter.skip();
        assertFalse(presenter.isResolving());
        assertFalse(presenter.isFinalCounting());
        assertTrue(resolution.finishNowCalls >= 1);
    }

    @Test
    void aNewSnapshotFinishesResolution() {
        FakeCount count = new FakeCount();
        FakeResolution resolution = new FakeResolution();
        RoundPresenter presenter = presenter(count, new FakeBurn(), resolution, false);
        presenter.onSnapshot(revealSnapshot(1));
        assertTrue(presenter.isResolving());

        presenter.onSnapshot(revealSnapshot(2));
        assertTrue(resolution.finishNowCalls >= 1);
        assertFalse(presenter.isResolving());
        assertTrue(presenter.isFinalCounting());
    }

    @Test
    void reducedMotionSkipsResolution() {
        FakeCount count = new FakeCount();
        FakeResolution resolution = new FakeResolution();
        RoundPresenter presenter = presenter(count, new FakeBurn(), resolution, true);
        presenter.onSnapshot(revealSnapshot(1));

        assertEquals(0, resolution.plays);
        assertFalse(presenter.isResolving());
        assertFalse(presenter.isFinalCounting(),
                "com movimento reduzido a contagem final também termina na hora");
    }

    @Test
    void replayResolutionRunsThePresentationAgain() {
        FakeCount count = new FakeCount();
        FakeResolution resolution = new FakeResolution();
        RoundPresenter presenter = presenter(count, new FakeBurn(), resolution, false);
        presenter.onSnapshot(revealSnapshot(1));
        resolution.complete();
        count.complete();
        assertFalse(presenter.isFinalCounting());

        presenter.replayResolution();
        assertTrue(presenter.isResolving());
        assertEquals(2, resolution.plays);
        resolution.complete();
        assertTrue(presenter.isFinalCounting());
    }

    @Test
    void presentationIsIdenticalForAllTwentyFourMessageOrders() {
        List<int[]> orders = permutations(new int[]{0, 1, 2, 3});
        assertEquals(24, orders.size());
        for (int[] order : orders) {
            FakeCount count = new FakeCount();
            int[] damageRuns = {0};
            RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { },
                    () -> damageRuns[0]++);

            // Entra na fase de poderes e conclui a contagem da mesa.
            presenter.onSnapshot(sabotageSnapshot("order", 1));
            count.complete();
            assertTrue(presenter.powerHandReady());

            GameSnapshot reveal = revealSnapshot(5);
            for (int message : order) {
                switch (message) {
                    // TURN_CHANGED não altera a apresentação.
                    case 0 -> { }
                    // ROUND_RESULT anuncia o resultado.
                    case 1 -> presenter.onOutcome(reveal.lastOutcome());
                    // PLAYER_DAMAGE também não altera a apresentação.
                    case 2 -> { }
                    // GAME_STATE_UPDATE traz o instantâneo de revelação (versão pulada: 5).
                    default -> presenter.onSnapshot(reveal);
                }
            }

            assertTrue(presenter.isFinalCounting(),
                    "ordem " + java.util.Arrays.toString(order) + " não iniciou a contagem final");
            assertEquals(300, presenter.displayedHp(1, 250),
                    "ordem " + java.util.Arrays.toString(order) + " perdeu a vida segurada");
            assertEquals(0, damageRuns[0]);

            count.complete();
            assertFalse(presenter.isFinalCounting());
            assertEquals(250, presenter.displayedHp(1, 250));
            assertEquals(1, damageRuns[0], "ordem " + java.util.Arrays.toString(order));
        }
    }

    @Test
    void duplicateMessagesDoNotRepeatThePresentation() {
        FakeCount count = new FakeCount();
        int[] damageRuns = {0};
        RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { },
                () -> damageRuns[0]++);
        presenter.onSnapshot(sabotageSnapshot("dup", 1));
        count.complete();
        GameSnapshot reveal = revealSnapshot(4);
        presenter.onSnapshot(reveal);
        presenter.onSnapshot(reveal);
        presenter.onOutcome(reveal.lastOutcome());
        presenter.onOutcome(reveal.lastOutcome());

        assertTrue(presenter.isFinalCounting());
        count.complete();
        count.complete();
        assertEquals(1, damageRuns[0], "mensagens duplicadas não podem repetir o dano");
        assertEquals(250, presenter.displayedHp(1, 250));
    }

    @Test
    void announcedOutcomeIsUsedWhenTheSnapshotOmitsIt() {
        FakeCount count = new FakeCount();
        RoundPresenter presenter = new RoundPresenter(1, false, count, () -> { }, () -> { });
        presenter.onSnapshot(sabotageSnapshot("announced", 1));
        count.complete();

        GameSnapshot reveal = revealSnapshot(3);
        GameSnapshot withoutOutcome = new GameSnapshot(reveal.sessionId(), reveal.version(),
                reveal.phase(), reveal.round(), reveal.currentPlayer(), reveal.rules(),
                reveal.globalModifierId(), reveal.globalModifierName(),
                reveal.globalModifierDescription(), reveal.firstResolver(),
                reveal.player1(), reveal.player2(), null, 0, List.of());

        presenter.onOutcome(reveal.lastOutcome());
        presenter.onSnapshot(withoutOutcome);
        assertTrue(presenter.isFinalCounting(),
                "o resultado anunciado por ROUND_RESULT deve alimentar a contagem final");
    }

    private static List<int[]> permutations(int[] values) {
        List<int[]> result = new java.util.ArrayList<>();
        permute(values.clone(), 0, result);
        return result;
    }

    private static void permute(int[] values, int index, List<int[]> result) {
        if (index == values.length) {
            result.add(values.clone());
            return;
        }
        for (int i = index; i < values.length; i++) {
            int tmp = values[index];
            values[index] = values[i];
            values[i] = tmp;
            permute(values, index + 1, result);
            tmp = values[index];
            values[index] = values[i];
            values[i] = tmp;
        }
    }

    private RoundPresenter presenter(FakeCount count, boolean reducedMotion) {
        return new RoundPresenter(1, reducedMotion, count, () -> { }, () -> { });
    }

    private RoundPresenter presenter(FakeCount count, FakeResolution resolution,
                                     boolean reducedMotion) {
        return new RoundPresenter(1, reducedMotion, count, null, resolution, () -> { },
                () -> { });
    }

    private RoundPresenter presenter(FakeCount count, FakeBurn burn,
                                     FakeResolution resolution, boolean reducedMotion) {
        return new RoundPresenter(1, reducedMotion, count, burn, resolution, () -> { },
                () -> { });
    }

    private RoundPresenter presenter(FakeCount count, FakeBurn burn, boolean reducedMotion) {
        return new RoundPresenter(1, reducedMotion, count, burn, () -> { }, () -> { });
    }

    private GameSnapshot sabotageSnapshot(long version) {
        return sabotageSnapshot("s", version);
    }

    private GameSnapshot sabotageSnapshot(String sessionId, long version) {
        ScoreResult table = scoring.score(cards("AE", "AC"), List.of(), null);
        PlayerView one = view(1, 300, table);
        PlayerView two = view(2, 300, table);
        return new GameSnapshot(sessionId, version, GamePhase.SABOTAGE, 1, 0, rules,
                null, "", "", 1, one, two, null, 0, List.of());
    }

    private GameSnapshot revealSnapshot(long version) {
        ScoreResult table = scoring.score(cards("AE", "AC"), List.of(), null);
        ScoreResult better = scoring.score(cards("AE", "AC", "AO"), List.of(), null);
        // Jogador 2 vence: o jogador 1 (myId) é quem deve ter a vida segurada.
        com.game.core.rules.RoundOutcome outcome = CombatResolver.resolve(1, table, better,
                cards("AE", "AC"), cards("AE", "AC", "AO"), List.of(), List.of(), List.of(), rules);
        int loserHp = 300 - outcome.damage();
        PlayerView one = view(1, outcome.loser() == 1 ? loserHp : 300, table);
        PlayerView two = view(2, outcome.loser() == 2 ? loserHp : 300, better);
        return new GameSnapshot("s", version, GamePhase.REVEAL, 1, 0, rules,
                null, "", "", 1, one, two, outcome, 0, List.of());
    }

    private static PlayerView view(int id, int hp, ScoreResult tableScore) {
        return new PlayerView(id, "P" + id, hp, 300, 3, List.of(), true,
                cards("AE", "AC"), tableScore, List.of(), 0, List.of(), false, false, 2);
    }

    private static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(Card::fromCode).toList();
    }

    private static final class FakeResolution implements RoundPresenter.ResolutionPlayer {

        private int plays;
        private int finishNowCalls;
        private Runnable finished;
        private boolean completed;
        private com.game.core.rules.RoundOutcome lastOutcome;

        @Override
        public void play(com.game.core.rules.RoundOutcome outcome, Runnable onFinished) {
            plays++;
            lastOutcome = outcome;
            finished = onFinished;
            completed = false;
        }

        @Override
        public void finishNow() {
            finishNowCalls++;
            complete();
        }

        void complete() {
            if (completed || finished == null) {
                return;
            }
            completed = true;
            finished.run();
        }
    }

    private static final class FakeBurn implements RoundPresenter.BurnPlayer {

        private int plays;
        private int finishNowCalls;
        private Runnable finished;
        private boolean completed;

        @Override
        public void play(Runnable onFinished) {
            plays++;
            finished = onFinished;
            completed = false;
        }

        @Override
        public void finishNow() {
            finishNowCalls++;
            complete();
        }

        void complete() {
            if (completed || finished == null) {
                return;
            }
            completed = true;
            finished.run();
        }
    }

    private static final class FakeCount implements RoundPresenter.CountPlayer {

        private int plays;
        private int finishNowCalls;
        private Runnable finished;
        private boolean completed;

        @Override
        public void play(ScoreResult own, ScoreResult opponent, Runnable onFinished) {
            plays++;
            finished = onFinished;
            completed = false;
        }

        @Override
        public void finishNow() {
            finishNowCalls++;
            complete();
        }

        void complete() {
            if (completed || finished == null) {
                return;
            }
            completed = true;
            finished.run();
        }
    }
}
