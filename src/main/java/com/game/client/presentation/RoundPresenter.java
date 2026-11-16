package com.game.client.presentation;

import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.PlayerView;

import java.util.HashSet;
import java.util.Set;

/**
 * Sequencia a apresentação da rodada: contagem da mesa &rarr; queima da mão &rarr; mão de poderes
 * &rarr; entrada do jogador &rarr; contagem final &rarr; dano. Mantém a vida EXIBIDA congelada no
 * valor anterior até a contagem final terminar.
 *
 * <p>Não conhece libGDX: recebe um {@link CountPlayer} e um {@link BurnPlayer} (as implementações
 * reais são {@code ScoreCountAnimator} e {@code HandBurnEffect}) e callbacks. Assim a sequência é
 * testável com falsos callbacks.</p>
 */
public final class RoundPresenter {

    /** Executa (ou conclui imediatamente) as duas contagens. */
    public interface CountPlayer {
        void play(ScoreResult own, ScoreResult opponent, Runnable onFinished);

        void finishNow();
    }

    /** Executa (ou conclui imediatamente) a queima da mão remanescente. */
    public interface BurnPlayer {
        void play(Runnable onFinished);

        void finishNow();
    }

    /** Executa (ou conclui imediatamente) a apresentação da resolução. */
    public interface ResolutionPlayer {
        void play(RoundOutcome outcome, Runnable onFinished);

        void finishNow();
    }

    /** Guarda de processo: uma rodada só queima uma vez, mesmo após redraw/reconexão. */
    private static final Set<String> BURNED_ROUNDS = new HashSet<>();

    private final int myId;
    private final boolean reducedMotion;
    private final CountPlayer countPlayer;
    private final BurnPlayer burnPlayer;
    private final ResolutionPlayer resolutionPlayer;
    private final Runnable onPresentationChanged;
    private final Runnable damageAnimation;

    private final int[] heldHp = new int[3];
    private GameSnapshot current;
    private int trackedRound = -1;
    private boolean tableCounted;
    private boolean finalCounted;
    private boolean powerHandReady;
    private boolean counting;
    private boolean finalCounting;
    private boolean burning;
    private boolean resolving;
    private boolean resolutionPlayed;
    private boolean holdingHp;
    private long countVersion = -1;

    public RoundPresenter(int myId, boolean reducedMotion, CountPlayer countPlayer,
                          Runnable onPresentationChanged, Runnable damageAnimation) {
        this(myId, reducedMotion, countPlayer, null, null, onPresentationChanged,
                damageAnimation);
    }

    public RoundPresenter(int myId, boolean reducedMotion, CountPlayer countPlayer,
                          BurnPlayer burnPlayer, Runnable onPresentationChanged,
                          Runnable damageAnimation) {
        this(myId, reducedMotion, countPlayer, burnPlayer, null, onPresentationChanged,
                damageAnimation);
    }

    public RoundPresenter(int myId, boolean reducedMotion, CountPlayer countPlayer,
                          BurnPlayer burnPlayer, ResolutionPlayer resolutionPlayer,
                          Runnable onPresentationChanged, Runnable damageAnimation) {
        this.myId = myId;
        this.reducedMotion = reducedMotion;
        this.countPlayer = countPlayer;
        this.burnPlayer = burnPlayer;
        this.resolutionPlayer = resolutionPlayer;
        this.onPresentationChanged = onPresentationChanged;
        this.damageAnimation = damageAnimation;
        heldHp[1] = -1;
        heldHp[2] = -1;
    }

    /** Chamado pelo GameScreen a cada instantâneo recebido. */
    public void onSnapshot(GameSnapshot state) {
        if (state == null) {
            return;
        }
        boolean newSnapshot = current == null || current.version() != state.version();
        current = state;

        if (counting && newSnapshot) {
            // Um instantâneo novo no meio da contagem conclui a contagem imediatamente.
            countPlayer.finishNow();
        }
        if (burning && newSnapshot && burnPlayer != null) {
            // Um instantâneo novo no meio da queima entrega a mão de poderes imediatamente.
            burnPlayer.finishNow();
        }
        if (resolving && newSnapshot && resolutionPlayer != null) {
            // Um instantâneo novo no meio da resolução conclui a apresentação imediatamente.
            resolutionPlayer.finishNow();
        }

        if (state.round() != trackedRound) {
            trackedRound = state.round();
            tableCounted = false;
            finalCounted = false;
            powerHandReady = false;
            counting = false;
            finalCounting = false;
            burning = false;
            resolving = false;
            resolutionPlayed = false;
            holdingHp = false;
            countVersion = -1;
        }

        if (!counting && !resolving) {
            if (state.phase() == GamePhase.SABOTAGE && !tableCounted) {
                startTableCount(state);
            } else if (state.phase() == GamePhase.REVEAL && state.lastOutcome() != null
                    && !finalCounted) {
                if (resolutionPlayer != null && !reducedMotion && !resolutionPlayed) {
                    startResolution(state);
                } else {
                    startFinalCount(state);
                }
            }
        }

        if (!holdingHp && state.phase() != GamePhase.REVEAL) {
            heldHp[1] = state.player(1).hp();
            heldHp[2] = state.player(2).hp();
        }
    }

    private void startTableCount(GameSnapshot state) {
        PlayerView me = state.player(myId);
        PlayerView opponent = state.opponentOf(myId);
        ScoreResult own = me.tableScore();
        ScoreResult theirs = opponent.tableScore();
        tableCounted = true;
        if (own == null || theirs == null) {
            powerHandReady = true;
            return;
        }
        counting = true;
        finalCounting = false;
        countVersion = state.version();
        if (reducedMotion) {
            countPlayer.play(own, theirs, () -> { });
            countPlayer.finishNow();
            counting = false;
            powerHandReady = true;
            notifyChanged();
            return;
        }
        countPlayer.play(own, theirs, () -> {
            counting = false;
            startHandBurn(current);
        });
    }

    private void startHandBurn(GameSnapshot state) {
        if (state == null || state.phase() != GamePhase.SABOTAGE
                || reducedMotion || burnPlayer == null) {
            burning = false;
            powerHandReady = true;
            notifyChanged();
            return;
        }
        String key = state.sessionId() + ":" + state.round();
        if (BURNED_ROUNDS.contains(key)) {
            burning = false;
            powerHandReady = true;
            notifyChanged();
            return;
        }
        BURNED_ROUNDS.add(key);
        burning = true;
        burnPlayer.play(() -> {
            burning = false;
            powerHandReady = true;
            notifyChanged();
        });
    }

    private void startResolution(GameSnapshot state) {
        resolutionPlayed = true;
        resolving = true;
        finalCounting = false;
        resolutionPlayer.play(state.lastOutcome(), () -> {
            resolving = false;
            startFinalCount(current);
        });
    }

    private void startFinalCount(GameSnapshot state) {
        resolving = false;
        ScoreResult own = myId == 1
                ? state.lastOutcome().player1Score() : state.lastOutcome().player2Score();
        ScoreResult theirs = myId == 1
                ? state.lastOutcome().player2Score() : state.lastOutcome().player1Score();
        // Numa sessão criada já em REVEAL não houve instantâneo anterior: reconstrói a vida
        // anterior a partir do dano registrado no resultado.
        if (heldHp[1] < 0) {
            int damage = state.lastOutcome().loser() == 1 ? state.lastOutcome().damage() : 0;
            heldHp[1] = state.player(1).hp() + damage;
        }
        if (heldHp[2] < 0) {
            int damage = state.lastOutcome().loser() == 2 ? state.lastOutcome().damage() : 0;
            heldHp[2] = state.player(2).hp() + damage;
        }
        finalCounted = true;
        holdingHp = true;
        counting = true;
        finalCounting = true;
        countVersion = state.version();
        if (reducedMotion) {
            countPlayer.play(own, theirs, () -> { });
            countPlayer.finishNow();
            releaseHpAndDamage(state);
            return;
        }
        countPlayer.play(own, theirs, () -> releaseHpAndDamage(state));
    }

    private void releaseHpAndDamage(GameSnapshot state) {
        counting = false;
        finalCounting = false;
        holdingHp = false;
        heldHp[1] = state.player(1).hp();
        heldHp[2] = state.player(2).hp();
        if (damageAnimation != null) {
            damageAnimation.run();
        }
        notifyChanged();
    }

    /** Vida a exibir: a anterior enquanto a contagem final não termina. */
    public int displayedHp(int playerId, int actualHp) {
        if (holdingHp && playerId >= 1 && playerId <= 2 && heldHp[playerId] >= 0) {
            return heldHp[playerId];
        }
        return actualHp;
    }

    /** Na fase SABOTAGE, a mão de poderes só aparece depois da contagem da mesa. */
    public boolean powerHandReady() {
        return powerHandReady;
    }

    public boolean isCounting() {
        return counting;
    }

    public boolean isFinalCounting() {
        return finalCounting;
    }

    public boolean isBurning() {
        return burning;
    }

    public boolean isResolving() {
        return resolving;
    }

    public boolean canAdvance() {
        return !counting && !resolving;
    }

    /** Clique/SPACE durante qualquer etapa conclui a apresentação imediatamente. */
    public void skip() {
        if (resolving && resolutionPlayer != null) {
            resolutionPlayer.finishNow();
        }
        if (counting) {
            countPlayer.finishNow();
        }
        if (burning && burnPlayer != null) {
            burnPlayer.finishNow();
        }
    }

    /** DevPreview (F7): repete a contagem sobre o último instantâneo, sem reaplicar dano. */
    public void replayLastCount() {
        if (current == null) {
            return;
        }
        if (counting) {
            countPlayer.finishNow();
        }
        if (current.phase() == GamePhase.REVEAL && current.lastOutcome() != null) {
            ScoreResult own = myId == 1
                    ? current.lastOutcome().player1Score()
                    : current.lastOutcome().player2Score();
            ScoreResult theirs = myId == 1
                    ? current.lastOutcome().player2Score()
                    : current.lastOutcome().player1Score();
            counting = true;
            finalCounting = true;
            holdingHp = false;
            countPlayer.play(own, theirs, () -> {
                counting = false;
                finalCounting = false;
                notifyChanged();
            });
        } else if (current.phase() == GamePhase.SABOTAGE) {
            PlayerView me = current.player(myId);
            PlayerView opponent = current.opponentOf(myId);
            if (me.tableScore() == null || opponent.tableScore() == null) {
                return;
            }
            counting = true;
            finalCounting = false;
            powerHandReady = false;
            countPlayer.play(me.tableScore(), opponent.tableScore(), () -> {
                counting = false;
                powerHandReady = true;
                notifyChanged();
            });
        }
    }

    /** DevPreview (F10): repete a resolução inteira sobre o último resultado. */
    public void replayResolution() {
        if (current == null || current.phase() != GamePhase.REVEAL
                || current.lastOutcome() == null || resolutionPlayer == null) {
            return;
        }
        if (resolving) {
            resolutionPlayer.finishNow();
        }
        resolving = true;
        resolutionPlayed = true;
        finalCounted = false;
        finalCounting = false;
        counting = false;
        holdingHp = false;
        notifyChanged();
        resolutionPlayer.play(current.lastOutcome(), () -> {
            resolving = false;
            startFinalCount(current);
        });
    }

    /** DevPreview (F9): repete a queima da mão sobre o último instantâneo. */
    public void replayBurn() {
        if (current == null || current.phase() != GamePhase.SABOTAGE
                || burnPlayer == null || burning) {
            return;
        }
        powerHandReady = false;
        burning = true;
        notifyChanged();
        burnPlayer.play(() -> {
            burning = false;
            powerHandReady = true;
            notifyChanged();
        });
    }

    private void notifyChanged() {
        if (onPresentationChanged != null) {
            onPresentationChanged.run();
        }
    }
}
