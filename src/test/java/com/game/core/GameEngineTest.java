package com.game.core;

import com.game.ai.BotBrain;
import com.game.core.model.Card;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.model.Suit;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
import com.game.core.rules.PowerEvent;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.state.GameEngine;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.core.state.LogEntry;
import com.game.core.state.PlayerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {

    /** Regras com poderes: 4 jokers + 3 sabotagens, até 2 cartas jogadas. */
    private static final GameRules POWERS = new GameRules(300, 8, 5, 3, 4, 3, 2);

    private GameEngine engine;
    private final ScoringEngine scoring = new ScoringEngine();

    @BeforeEach
    void setUp() {
        engine = new GameEngine("session-1", "Ana", "Beto", new Random(1234),
                GameRules.DEFAULT, scoring);
    }

    private GameEngine powersEngine() {
        return new GameEngine("session-powers", "Ana", "Beto", new Random(1234), POWERS, scoring);
    }

    @Test
    @DisplayName("o início distribui oito cartas para cada jogador")
    void startDealsRound() {
        assertEquals(GamePhase.WAITING, engine.phase());
        engine.start();

        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(1, engine.round());
        assertEquals(1, engine.currentPlayer());
        assertEquals(300, engine.hpOf(1));
        assertEquals(300, engine.hpOf(2));
        assertNotNull(engine.globalModifierId());

        List<Card> hand1 = engine.handOf(1);
        List<Card> hand2 = engine.handOf(2);
        assertEquals(8, hand1.size());
        assertEquals(8, hand2.size());
        assertEquals(16, new HashSet<>(concat(hand1, hand2)).size(),
                "os jogadores não podem compartilhar cartas");
        assertTrue(engine.snapshot().log().stream()
                .anyMatch(entry -> entry.text().startsWith("GLOBAL|")));
    }

    @Test
    @DisplayName("após as duas mãos cada jogador recebe uma única mão com 4 jokers e 3 sabotagens")
    void fiveCardFlowDealsPowerHands() {
        GameEngine engine = powersEngine();
        engine.start();
        submitBoth(engine);

        assertEquals(GamePhase.SABOTAGE, engine.phase());
        assertEquals(0, engine.currentPlayer());
        for (int player : new int[]{1, 2}) {
            List<PowerCard> hand = engine.powerHandOf(player);
            assertEquals(7, hand.size());
            assertEquals(7, new HashSet<>(hand).size(), "as cartas da mão não se repetem");
            assertEquals(4, countKind(hand, PowerKind.JOKER));
            assertEquals(3, countKind(hand, PowerKind.SABOTAGE));
            assertFalse(groupedByKind(hand), "jokers e sabotagens devem estar intercalados");
        }

        GameSnapshot forPlayer1 = engine.snapshotFor(1);
        assertEquals(7, forPlayer1.player(1).powerHand().size());
        assertEquals(7, forPlayer1.player(1).powerCount());
        assertTrue(forPlayer1.player(2).powerHand().isEmpty(),
                "o oponente não vê a mão de poderes");
        assertEquals(7, forPlayer1.player(2).powerCount());

        GameSnapshot spectator = engine.snapshot();
        assertEquals(7, spectator.player(1).powerHand().size());
        assertEquals(7, spectator.player(2).powerHand().size());
    }

    @Test
    @DisplayName("a mão de poderes é determinística e independente para cada jogador")
    void powerHandsAreDeterministicAndIndependent() {
        GameEngine a = new GameEngine("x", "A", "B", new Random(99), POWERS, scoring);
        GameEngine b = new GameEngine("x", "A", "B", new Random(99), POWERS, scoring);
        a.start();
        b.start();
        submitBoth(a);
        submitBoth(b);

        assertEquals(a.powerHandOf(1), b.powerHandOf(1));
        assertEquals(a.powerHandOf(2), b.powerHandOf(2));
        assertNotEquals(a.powerHandOf(1), a.powerHandOf(2),
                "cada jogador sorteia a própria mão de forma independente");
        assertFalse(groupedByKind(a.powerHandOf(1)));
        assertFalse(groupedByKind(a.powerHandOf(2)));
    }

    @Test
    @DisplayName("lockPowers rejeita índices repetidos/fora do intervalo e mais de duas cartas")
    void lockPowersValidation() {
        GameEngine engine = powersEngine();
        engine.start();
        assertCode("WRONG_PHASE", () -> engine.lockPowers(1, List.of()));

        submitBoth(engine);
        assertCode("INVALID_PLAYER", () -> engine.lockPowers(3, List.of()));
        assertCode("INVALID_POWER", () -> engine.lockPowers(1, List.of(0, 0)));
        assertCode("INVALID_POWER", () -> engine.lockPowers(1, List.of(9)));
        assertCode("INVALID_POWER", () -> engine.lockPowers(1, List.of(-1)));
        assertCode("TOO_MANY_POWERS", () -> engine.lockPowers(1, List.of(0, 1, 2)));

        engine.lockPowers(1, List.of(0, 1));
        assertCode("ALREADY_LOCKED", () -> engine.lockPowers(1, List.of()));

        GameRules noPowers = new GameRules(300, 8, 5, 3, 0, 0, 0);
        GameEngine capped = new GameEngine("cap", "A", "B", new Random(4), noPowers, scoring);
        capped.start();
        submitBoth(capped);
        assertEquals(GamePhase.REVEAL, capped.phase(),
                "sem poderes a rodada resolve direto, sem fase SABOTAGE");
    }

    @Test
    @DisplayName("lockPowers aceita 0, 1 e 2 cartas em qualquer combinação de tipos")
    void lockPowersAcceptsEveryLegalCombination() {
        for (int seed = 0; seed < 20; seed++) {
            GameEngine game = new GameEngine("combo-" + seed, "A", "B", new Random(seed),
                    POWERS, scoring);
            game.start();
            submitBoth(game);
            List<PowerCard> hand = game.powerHandOf(1);
            List<Integer> jokers = indicesOf(hand, PowerKind.JOKER);
            List<Integer> sabotages = indicesOf(hand, PowerKind.SABOTAGE);

            game.lockPowers(1, List.of());
            game.lockPowers(2, List.of());
            assertEquals(GamePhase.REVEAL, game.phase());

            GameEngine one = new GameEngine("one-" + seed, "A", "B", new Random(seed),
                    POWERS, scoring);
            one.start();
            submitBoth(one);
            one.lockPowers(1, List.of(jokers.get(0)));
            one.lockPowers(2, List.of());
            assertEquals(1, one.playedPowersOf(1).size());

            GameEngine oneSabotage = new GameEngine("sab-" + seed, "A", "B",
                    new Random(seed), POWERS, scoring);
            oneSabotage.start();
            submitBoth(oneSabotage);
            oneSabotage.lockPowers(1, List.of(sabotages.get(0)));
            oneSabotage.lockPowers(2, List.of());
            assertEquals(1, oneSabotage.playedPowersOf(1).size());

            GameEngine twoJokers = new GameEngine("twoj-" + seed, "A", "B",
                    new Random(seed), POWERS, scoring);
            twoJokers.start();
            submitBoth(twoJokers);
            twoJokers.lockPowers(1, List.of(jokers.get(0), jokers.get(1)));
            twoJokers.lockPowers(2, List.of());
            assertEquals(2, twoJokers.playedPowersOf(1).size());

            GameEngine mixed = new GameEngine("mix-" + seed, "A", "B",
                    new Random(seed), POWERS, scoring);
            mixed.start();
            submitBoth(mixed);
            mixed.lockPowers(1, List.of(sabotages.get(0), jokers.get(0)));
            mixed.lockPowers(2, List.of());
            assertEquals(2, mixed.playedPowersOf(1).size());
        }
    }

    @Test
    @DisplayName("os PowerEvents seguem a ordem alternada e o índice da mão dentro de cada jogador")
    void powerEventsFollowResolutionOrder() {
        GameEngine mixed = powersEngine();
        mixed.start();
        submitBoth(mixed);
        List<PowerCard> hand = mixed.powerHandOf(1);
        int joker = indicesOf(hand, PowerKind.JOKER).get(0);
        int sabotage = indicesOf(hand, PowerKind.SABOTAGE).get(0);
        List<Integer> chosen = joker < sabotage
                ? List.of(joker, sabotage) : List.of(sabotage, joker);
        mixed.lockPowers(1, chosen);
        mixed.lockPowers(2, List.of());

        RoundOutcome outcome = mixed.snapshot().lastOutcome();
        List<PowerCard> expected = chosen.stream().map(hand::get).toList();
        assertEquals(expected, outcome.player1Powers());
        assertEquals(2, outcome.powerEvents().size());
        for (int i = 0; i < 2; i++) {
            PowerEvent event = outcome.powerEvents().get(i);
            assertEquals(1, event.player());
            assertEquals(expected.get(i).kind(), event.kind());
            assertEquals(expected.get(i).id(), event.id());
        }

        GameEngine alternating = powersEngine();
        alternating.start();
        submitBoth(alternating);
        alternating.lockPowers(1, List.of(0));
        alternating.lockPowers(2, List.of(0));
        RoundOutcome both = alternating.snapshot().lastOutcome();
        assertEquals(2, both.powerEvents().size());
        assertEquals(alternating.firstResolver(), both.powerEvents().get(0).player());
        assertEquals(alternating.firstResolver() == 1 ? 2 : 1,
                both.powerEvents().get(1).player());
    }

    @Test
    @DisplayName("a pontuação usa os jokers jogados e o resultado carrega os poderes escolhidos")
    void resolutionScoresWithPlayedJokers() {
        GameEngine engine = powersEngine();
        engine.start();
        List<Card> play1 = engine.handOf(1).subList(0, 5);
        engine.submitPlay(1, play1);
        engine.submitPlay(2, engine.handOf(2).subList(0, 5));

        List<PowerCard> hand1 = engine.powerHandOf(1);
        List<Integer> jokers1 = indicesOf(hand1, PowerKind.JOKER);
        List<Integer> selected = List.of(jokers1.get(0), jokers1.get(1));
        engine.lockPowers(1, selected);
        engine.lockPowers(2, List.of());

        assertEquals(GamePhase.REVEAL, engine.phase());
        RoundOutcome outcome = engine.snapshot().lastOutcome();
        List<PowerCard> expectedPowers = selected.stream().map(hand1::get).toList();
        assertEquals(expectedPowers, outcome.player1Powers());
        assertTrue(outcome.player2Powers().isEmpty());
        assertTrue(outcome.player1Cards().equals(play1));

        List<String> jokerIds = expectedPowers.stream().map(PowerCard::id).toList();
        ScoreResult expected = scoring.score(play1, jokerIds, engine.globalModifierId());
        assertEquals(expected.chips(), outcome.player1Score().chips());
        assertEquals(expected.mult(), outcome.player1Score().mult());
        assertEquals(expected.finalScore(), outcome.player1Score().finalScore());

        assertEquals(2, outcome.powerEvents().size(),
                "cada joker jogado gera um PowerEvent");
        assertFalse(outcome.player1Score().steps().isEmpty(),
                "o resultado final carrega os passos da contagem");
        assertFalse(outcome.player2Score().steps().isEmpty());
        assertTrue(engine.powerHandOf(1).isEmpty(), "poderes não usados são descartados");
        assertEquals(0, engine.snapshot().player(1).powerCount());
        assertEquals(2, engine.snapshot().player(1).playedPowers().size());
    }

    @Test
    @DisplayName("a ordem de resolução alterna e o log registra ORDEM antes de aplicar")
    void sabotageOrderAlternates() {
        GameEngine engine = powersEngine();
        engine.start();
        submitBoth(engine);
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());
        assertOrderBeforeResult(engine, "ORDEM|1|1");

        engine.advance(1);
        engine.advance(2);
        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(2, engine.round());
        assertEquals(2, engine.firstResolver(), "na rodada 2 o jogador 2 resolve primeiro");

        submitBoth(engine);
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());
        assertOrderBeforeResult(engine, "ORDEM|2|2");
    }

    @Test
    @DisplayName("o perdedor sofre ceil(diferença/3) de dano")
    void loserTakesCeilOfThird() {
        engine.start();
        submitBoth(engine);
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());

        RoundOutcome outcome = engine.snapshot().lastOutcome();
        int difference = Math.abs(outcome.player1Score().finalScore()
                - outcome.player2Score().finalScore());
        assertEquals((int) Math.ceil(difference / 3.0), outcome.damage());
        if (outcome.winner() != 0) {
            assertEquals(300 - outcome.damage(), engine.hpOf(outcome.loser()));
        }
    }

    @Test
    @DisplayName("confirmar a revelação inicia uma rodada nova sem carregar poderes")
    void nextRoundHasFreshHandsAndNoPowers() {
        GameEngine engine = powersEngine();
        engine.start();
        submitBoth(engine);
        engine.lockPowers(1, List.of(0, 2));
        engine.lockPowers(2, List.of());
        engine.advance(1);
        engine.advance(2);

        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(2, engine.round());
        assertEquals(1, engine.currentPlayer());
        assertEquals(8, engine.handOf(1).size());
        assertEquals(8, engine.handOf(2).size());
        assertTrue(engine.powerHandOf(1).isEmpty());
        assertTrue(engine.powerHandOf(2).isEmpty());
        assertTrue(engine.playedPowersOf(1).isEmpty());
        assertTrue(engine.playedPowersOf(2).isEmpty());
        assertTrue(engine.pendingCardsOf(1).isEmpty());
        PlayerView view = engine.snapshot().player(1);
        assertFalse(view.powersLocked());
        assertEquals(0, view.powerCount());
        assertTrue(view.playedPowers().isEmpty());
    }

    @Test
    @DisplayName("Virada de Naipes reescreve apenas as 5 cartas jogadas pelo oponente")
    void viradaNaipesRewritesOnlyOpponentPlayedCards() {
        GameEngine game = engineWithSabotage("virada_naipes");
        List<Card> play1 = List.copyOf(game.pendingCardsOf(1));
        List<Card> play2 = List.copyOf(game.pendingCardsOf(2));
        int index = indexOf(game.powerHandOf(1), PowerKind.SABOTAGE, "virada_naipes");

        game.lockPowers(1, List.of(index));
        game.lockPowers(2, List.of());

        RoundOutcome outcome = game.snapshot().lastOutcome();
        assertEquals(play1, outcome.player1Cards(), "a jogada do usuário não pode mudar");
        assertEquals(mirrored(play2), outcome.player2Cards(),
                "as 5 cartas do oponente devem ter os naipes espelhados");
        PowerEvent event = outcome.powerEvents().get(0);
        assertEquals(2, event.targetPlayer());
        assertEquals(play2, event.cardsBefore());
        assertEquals(mirrored(play2), event.cardsAfter());
        assertTrue(game.snapshot().log().stream().map(LogEntry::text)
                .anyMatch(text -> text.startsWith("NIAPES|2|")));
    }

    @Test
    @DisplayName("Troca de Carta substitui exatamente uma carta da jogada do oponente")
    void trocaCartaReplacesOneOpponentCard() {
        GameEngine game = engineWithSabotage("troca_carta");
        List<Card> play1 = List.copyOf(game.pendingCardsOf(1));
        List<Card> play2 = List.copyOf(game.pendingCardsOf(2));
        int index = indexOf(game.powerHandOf(1), PowerKind.SABOTAGE, "troca_carta");

        game.lockPowers(1, List.of(index));
        game.lockPowers(2, List.of());

        RoundOutcome outcome = game.snapshot().lastOutcome();
        assertEquals(play1, outcome.player1Cards(), "a jogada do usuário não pode mudar");
        List<Card> finalCards = outcome.player2Cards();
        assertEquals(5, finalCards.size());
        int changed = 0;
        for (int i = 0; i < play2.size(); i++) {
            if (!play2.get(i).equals(finalCards.get(i))) {
                changed++;
            }
        }
        assertEquals(1, changed, "apenas uma carta do oponente deve ser substituída");
        assertNotEquals(new HashSet<>(play2), new HashSet<>(finalCards),
                "a carta nova vem do baralho restante");
        PowerEvent event = outcome.powerEvents().get(0);
        assertNotNull(event.cardsBefore());
        assertNotNull(event.cardsAfter());
        assertTrue(game.snapshot().log().stream().map(LogEntry::text)
                .anyMatch(text -> text.startsWith("TROCA_CARTA|2|")));
    }

    @Test
    @DisplayName("uma partida completa entre bots chega ao fim de jogo e permite revanche")
    void fullMatchToGameOver() {
        BotBrain brain = new BotBrain(scoring, POWERS.maxPlay());
        GameEngine engine = powersEngine();
        engine.start();
        int guard = 0;

        while (engine.phase() != GamePhase.GAME_OVER && guard++ < 5000) {
            GameSnapshot state = engine.snapshot();
            switch (state.phase()) {
                case BUILD -> {
                    int player = state.currentPlayer();
                    engine.submitPlay(player, brain.choosePlay(engine.handOf(player),
                            state.globalModifierId()));
                }
                case SABOTAGE -> {
                    for (int player : new int[]{1, 2}) {
                        if (engine.snapshot().player(player).powersLocked()) {
                            continue;
                        }
                        engine.lockPowers(player,
                                brain.choosePowers(engine.snapshotFor(player), player));
                    }
                }
                case REVEAL -> {
                    engine.advance(1);
                    engine.advance(2);
                }
                default -> throw new IllegalStateException("Unexpected phase " + engine.phase());
            }
        }

        assertEquals(GamePhase.GAME_OVER, engine.phase());
        assertTrue(engine.winner() == 1 || engine.winner() == 2);
        int loser = engine.winner() == 1 ? 2 : 1;
        assertEquals(0, engine.hpOf(loser));
        assertTrue(engine.hpOf(engine.winner()) > 0);
        assertEquals(engine.winner(), engine.snapshot().winner());
        assertTrue(engine.snapshot().log().stream().anyMatch(entry -> entry.text().startsWith("FIM|")));

        engine.requestRematch(1);
        assertEquals(GamePhase.GAME_OVER, engine.phase());
        engine.requestRematch(2);
        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(1, engine.round());
        assertEquals(300, engine.hpOf(1));
        assertEquals(300, engine.hpOf(2));
        assertTrue(engine.powerHandOf(1).isEmpty());
        assertTrue(engine.playedPowersOf(1).isEmpty());
    }

    @Test
    @DisplayName("sem poderes configurados, a segunda jogada resolve a rodada na mesma chamada")
    void noPowerRulesResolveImmediately() {
        GameEngine engine = new GameEngine("no-powers", "A", "B", new Random(7),
                new GameRules(300, 8, 5, 3, 0, 0, 0), scoring);
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 5));
        assertEquals(GamePhase.BUILD, engine.phase(), "a primeira jogada não resolve a rodada");
        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        assertEquals(GamePhase.REVEAL, engine.phase());
        assertNotNull(engine.snapshot().lastOutcome());
        assertTrue(engine.snapshot().player(1).powerHand().isEmpty());
        assertTrue(engine.snapshot().player(2).powerHand().isEmpty());
    }

    @Test
    @DisplayName("submitPlay aceita de 1 a 5 cartas distintas da própria mão")
    void submitRequiresOneToFiveCardsFromHand() {
        engine.start();
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of()));
        assertCode("INVALID_SELECTION",
                () -> engine.submitPlay(1, engine.handOf(1).subList(0, 6)));

        Card repeated = engine.handOf(1).get(0);
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of(repeated, repeated,
                engine.handOf(1).get(1), engine.handOf(1).get(2), engine.handOf(1).get(3))));

        assertCode("CARD_NOT_IN_HAND",
                () -> engine.submitPlay(1, engine.handOf(2).subList(0, 5)));
    }

    @Test
    @DisplayName("a pontuação usa só as cinco cartas jogadas e as três restantes ficam na mão")
    void scoringUsesOnlyPlayedCardsAndUnplayedStayInHand() {
        engine.start();
        List<Card> play1 = List.copyOf(engine.handOf(1).subList(0, 5));
        List<Card> rest = List.copyOf(engine.handOf(1).subList(5, 8));

        engine.submitPlay(1, play1);
        assertEquals(rest, engine.handOf(1), "as cartas não jogadas permanecem na mão");
        assertEquals(3, engine.snapshotFor(1).player(1).handCount());

        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());
        RoundOutcome outcome = engine.snapshot().lastOutcome();
        assertEquals(play1, outcome.player1Cards());
        ScoreResult expected = scoring.score(play1, List.of(), engine.globalModifierId());
        assertEquals(expected.chips(), outcome.player1Score().chips());
        assertEquals(expected.mult(), outcome.player1Score().mult());
        assertEquals(expected.finalScore(), outcome.player1Score().finalScore());
    }

    @Test
    @DisplayName("as duas mãos só aparecem para os dois jogadores quando ambos enviam")
    void bothHandsVisibleOnlyAfterBothSubmit() {
        engine.start();
        List<Card> play1 = List.copyOf(engine.handOf(1).subList(0, 5));
        engine.submitPlay(1, play1);

        assertEquals(play1, engine.snapshotFor(1).player(1).playedCards(),
                "o jogador vê a própria jogada na mesa");
        assertTrue(engine.snapshotFor(1).player(2).playedCards().isEmpty());
        assertTrue(engine.snapshotFor(2).player(1).playedCards().isEmpty(),
                "o oponente ainda não vê a jogada do primeiro");

        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        assertEquals(5, engine.snapshotFor(1).player(2).playedCards().size());
        assertEquals(5, engine.snapshotFor(2).player(1).playedCards().size());
        assertEquals(play1, engine.snapshotFor(2).player(1).playedCards());
    }

    @Test
    @DisplayName("o placar de mesa é a pontuação das cinco cartas sem poderes")
    void tableScoreMatchesScoringEngineWithoutPowers() {
        engine.start();
        List<Card> play1 = List.copyOf(engine.handOf(1).subList(0, 5));
        List<Card> play2 = List.copyOf(engine.handOf(2).subList(0, 5));
        engine.submitPlay(1, play1);
        assertNull(engine.snapshotFor(1).player(1).tableScore(),
                "o placar de mesa só existe depois que as duas mãos são enviadas");
        engine.submitPlay(2, play2);

        ScoreResult table1 = engine.snapshotFor(1).player(1).tableScore();
        ScoreResult table2 = engine.snapshotFor(2).player(2).tableScore();
        assertNotNull(table1);
        assertNotNull(table2);
        assertEquals(scoring.score(play1, List.of(), engine.globalModifierId()), table1);
        assertEquals(scoring.score(play2, List.of(), engine.globalModifierId()), table2);
        assertFalse(table1.steps().isEmpty(), "o placar de mesa carrega os passos da contagem");
        assertFalse(table2.steps().isEmpty());
        assertEquals(table1, engine.snapshotFor(2).player(1).tableScore(),
                "os dois visualizadores recebem o mesmo placar de mesa");
    }

    @Test
    @DisplayName("os poderes jogados ficam ocultos até a revelação")
    void playedPowersHiddenUntilReveal() {
        GameEngine game = powersEngine();
        game.start();
        submitBoth(game);
        game.lockPowers(1, List.of(0));
        assertTrue(game.snapshotFor(2).player(1).playedPowers().isEmpty());
        game.lockPowers(2, List.of());

        assertEquals(1, game.snapshotFor(2).player(1).playedPowers().size());
        assertEquals(0, game.snapshotFor(2).player(2).playedPowers().size());
        assertTrue(game.snapshotFor(2).player(1).powerHand().isEmpty(),
                "a mão continua oculta depois de travar");
    }

    @Test
    @DisplayName("a jogada aceita de 1 a maxPlay cartas")
    void submitPlayAcceptsFewerThanMaxPlay() {
        engine.start();
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of()));

        List<Card> three = engine.handOf(1).subList(0, 3);
        engine.submitPlay(1, three);
        assertEquals(3, engine.pendingCardsOf(1).size());
        assertTrue(engine.snapshot().player(1).submitted());

        List<Card> one = engine.handOf(2).subList(0, 1);
        engine.submitPlay(2, one);
        assertEquals(GamePhase.SABOTAGE, engine.phase());
        assertEquals(1, engine.pendingCardsOf(2).size());
    }

    @Test
    @DisplayName("a jogada rejeita mais cartas que o máximo permitido")
    void submitPlayRejectsMoreThanMaxPlay() {
        engine.start();
        assertCode("INVALID_SELECTION",
                () -> engine.submitPlay(1, engine.handOf(1).subList(0, 6)));
    }

    @Test
    @DisplayName("o descarte substitui as cartas e mantém o tamanho da mão")
    void discardReplacesCardsAndKeepsHandSize() {
        engine.start();
        List<Card> handBefore = engine.handOf(1);
        List<Card> discarded = handBefore.subList(0, 3);

        engine.discardCards(1, discarded);

        List<Card> handAfter = engine.handOf(1);
        assertEquals(handBefore.size(), handAfter.size());
        assertFalse(handAfter.stream().anyMatch(discarded::contains),
                "as cartas descartadas não podem continuar na mão");
        assertEquals(1, engine.discardsRemaining(1));
        assertEquals(1, engine.snapshotFor(1).player(1).discardsRemaining());
    }

    @Test
    @DisplayName("o descarte é limitado a duas ações por rodada")
    void discardLimitedToTwoPerRound() {
        engine.start();
        engine.discardCards(1, engine.handOf(1).subList(0, 1));
        engine.discardCards(1, engine.handOf(1).subList(0, 1));
        assertEquals(0, engine.discardsRemaining(1));
        assertCode("NO_DISCARDS_LEFT",
                () -> engine.discardCards(1, engine.handOf(1).subList(0, 1)));
    }

    @Test
    @DisplayName("o descarte exige de 1 a 8 cartas")
    void discardValidatesCardCount() {
        GameEngine wide = new GameEngine("wide", "Ana", "Beto", new Random(5),
                new GameRules(300, 10, 5, 3, 0, 0, 0), scoring);
        wide.start();
        assertCode("INVALID_SELECTION", () -> wide.discardCards(1, List.of()));
        assertCode("TOO_MANY_CARDS",
                () -> wide.discardCards(1, wide.handOf(1).subList(0, 9)));

        wide.discardCards(1, wide.handOf(1).subList(0, 8));
        assertEquals(10, wide.handOf(1).size());
        assertEquals(1, wide.discardsRemaining(1));
    }

    @Test
    @DisplayName("o descarte rejeita cartas fora da mão e fases erradas")
    void discardRejectsIllegalCardsAndPhases() {
        engine.start();
        assertCode("CARD_NOT_IN_HAND",
                () -> engine.discardCards(1, List.of(engine.handOf(2).get(0))));

        submitBoth(engine);
        assertEquals(GamePhase.SABOTAGE, engine.phase());
        assertCode("WRONG_PHASE",
                () -> engine.discardCards(1, engine.handOf(1).subList(0, 1)));
    }

    @Test
    @DisplayName("os descartes voltam a ficar disponíveis na rodada seguinte")
    void discardsResetOnNextRound() {
        engine.start();
        engine.discardCards(1, engine.handOf(1).subList(0, 1));
        engine.discardCards(1, engine.handOf(1).subList(0, 1));
        submitBoth(engine);
        engine.lockPowers(1, List.of());
        engine.lockPowers(2, List.of());
        engine.advance(1);
        engine.advance(2);

        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(2, engine.round());
        assertEquals(GameEngine.MAX_DISCARDS_PER_ROUND, engine.discardsRemaining(1));
        assertEquals(GameEngine.MAX_DISCARDS_PER_ROUND, engine.discardsRemaining(2));
    }

    private void assertOrderBeforeResult(GameEngine game, String orderLine) {        List<String> texts = game.snapshot().log().stream().map(LogEntry::text).toList();
        int orderIndex = texts.indexOf(orderLine);
        assertTrue(orderIndex >= 0, "o log deve conter " + orderLine);
        int resultIndex = -1;
        for (int i = orderIndex + 1; i < texts.size(); i++) {
            if (texts.get(i).startsWith("RESULT|")) {
                resultIndex = i;
                break;
            }
        }
        assertTrue(resultIndex > orderIndex, "ORDEM deve ser registrado antes de aplicar as cartas");
    }

    private static void submitBoth(GameEngine game) {
        game.submitPlay(1, game.handOf(1).subList(0, 5));
        game.submitPlay(2, game.handOf(2).subList(0, 5));
    }

    /** Procura uma semente em que o jogador 1 receba a sabotagem pedida e para na fase de poderes. */
    private GameEngine engineWithSabotage(String sabotageId) {
        for (int seed = 0; seed < 500; seed++) {
            GameEngine candidate = new GameEngine("s", "A", "B", new Random(seed),
                    POWERS, scoring);
            candidate.start();
            submitBoth(candidate);
            if (candidate.powerHandOf(1).contains(new PowerCard(PowerKind.SABOTAGE, sabotageId))) {
                return candidate;
            }
        }
        throw new AssertionError("nenhuma semente ofereceu a sabotagem " + sabotageId);
    }

    private static int indexOf(List<PowerCard> hand, PowerKind kind, String id) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).kind() == kind && hand.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new AssertionError("carta não encontrada: " + kind + "/" + id);
    }

    private static List<Integer> indicesOf(List<PowerCard> hand, PowerKind kind) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).kind() == kind) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static int countKind(List<PowerCard> hand, PowerKind kind) {
        return (int) hand.stream().filter(card -> card.kind() == kind).count();
    }

    private static boolean groupedByKind(List<PowerCard> hand) {
        int switches = 0;
        for (int i = 1; i < hand.size(); i++) {
            if (hand.get(i).kind() != hand.get(i - 1).kind()) {
                switches++;
            }
        }
        return switches <= 1;
    }

    private static List<Card> mirrored(List<Card> cards) {
        List<Card> flipped = new ArrayList<>(cards.size());
        for (Card card : cards) {
            flipped.add(new Card(card.rank(), switch (card.suit()) {
                case SPADES -> Suit.HEARTS;
                case HEARTS -> Suit.SPADES;
                case DIAMONDS -> Suit.CLUBS;
                case CLUBS -> Suit.DIAMONDS;
            }));
        }
        return flipped;
    }

    private static List<Card> concat(List<Card> a, List<Card> b) {
        List<Card> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void assertCode(String code, Runnable action) {
        GameRuleException exception = assertThrows(GameRuleException.class, action::run);
        assertEquals(code, exception.code(), exception.getMessage());
    }

    @Test
    @DisplayName("toda mudança visível em qualquer visualizador incrementa a versão do instantâneo")
    void everyVisibleChangeBumpsTheViewerVersion() {
        GameEngine engine = new GameEngine("versions", "Ana", "Beto", new Random(11), POWERS,
                new ScoringEngine());
        assertBumped(engine, engine::start);
        List<Card> hand1 = engine.handOf(1).subList(0, 5);
        assertBumped(engine, () -> engine.submitPlay(1, hand1));
        List<Card> hand2 = engine.handOf(2).subList(0, 5);
        assertBumped(engine, () -> engine.submitPlay(2, hand2));
        // A mesa/pontuação e as mãos de poder ficaram visíveis para os dois lados.
        long lockedVersion = engine.version();
        assertBumped(engine, () -> engine.lockPowers(1, List.of()));
        assertTrue(engine.version() > lockedVersion);
        assertBumped(engine, () -> engine.lockPowers(2, List.of()));
        assertTrue(engine.phase() == GamePhase.REVEAL);
        assertBumped(engine, () -> engine.advance(1));
        assertBumped(engine, () -> engine.advance(2));
    }

    private static void assertBumped(GameEngine engine, Runnable action) {
        GameSnapshot before1 = engine.snapshotFor(GameEngine.PLAYER_ONE);
        GameSnapshot before2 = engine.snapshotFor(GameEngine.PLAYER_TWO);
        action.run();
        GameSnapshot after1 = engine.snapshotFor(GameEngine.PLAYER_ONE);
        GameSnapshot after2 = engine.snapshotFor(GameEngine.PLAYER_TWO);
        assertViewerVersionBumped(before1, after1);
        assertViewerVersionBumped(before2, after2);
    }

    private static void assertViewerVersionBumped(GameSnapshot before, GameSnapshot after) {
        GameSnapshot normalizedBefore = withoutVersion(before);
        GameSnapshot normalizedAfter = withoutVersion(after);
        if (!normalizedBefore.equals(normalizedAfter)) {
            assertTrue(after.version() > before.version(),
                    "o instantâneo visível mudou sem incrementar a versão ("
                            + before.version() + " -> " + after.version() + ")");
        }
    }

    private static GameSnapshot withoutVersion(GameSnapshot snapshot) {
        return new GameSnapshot(snapshot.sessionId(), 0L, snapshot.phase(), snapshot.round(),
                snapshot.currentPlayer(), snapshot.rules(), snapshot.globalModifierId(),
                snapshot.globalModifierName(), snapshot.globalModifierDescription(),
                snapshot.firstResolver(), snapshot.player1(), snapshot.player2(),
                snapshot.lastOutcome(), snapshot.winner(), snapshot.log());
    }

    @Test
    @DisplayName("índices de poder inválidos deixam o estado intacto e podem ser corrigidos")
    void invalidPowerIndicesLeaveTheStateUntouched() {
        GameEngine engine = new GameEngine("powers", "Ana", "Beto", new Random(3), POWERS,
                new ScoringEngine());
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 5));
        engine.submitPlay(2, engine.handOf(2).subList(0, 5));
        assertEquals(GamePhase.SABOTAGE, engine.phase());

        GameSnapshot before = engine.snapshot();
        assertCode("INVALID_POWER", () -> engine.lockPowers(1, List.of(99)));
        assertEquals(before, engine.snapshot(), "a recusa não pode alterar nada");
        assertCode("INVALID_POWER", () -> engine.lockPowers(1, List.of(0, 0)));
        assertEquals(before, engine.snapshot(), "índices repetidos não podem alterar nada");

        engine.lockPowers(1, List.of(0));
        assertTrue(engine.snapshotFor(1).player(1).powersLocked());
    }

    @Test
    @DisplayName("seleção inválida de cartas não altera a mão nem a versão")
    void invalidPlayLeavesTheStateUntouched() {
        GameEngine engine = new GameEngine("plays", "Ana", "Beto", new Random(4), POWERS,
                new ScoringEngine());
        engine.start();
        GameSnapshot before = engine.snapshot();
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of()));
        assertEquals(before, engine.snapshot());
        Card notInHand = engine.handOf(2).get(0);
        assertCode("CARD_NOT_IN_HAND", () -> engine.submitPlay(1,
                List.of(notInHand, engine.handOf(1).get(0), engine.handOf(1).get(1),
                        engine.handOf(1).get(2), engine.handOf(1).get(3))));
        assertEquals(before, engine.snapshot());
    }
}
