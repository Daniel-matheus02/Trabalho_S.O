package com.game.core;

import com.game.ai.BotBrain;
import com.game.core.model.Card;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {

    private GameEngine engine;
    private final ScoringEngine scoring = new ScoringEngine();

    @BeforeEach
    void setUp() {
        engine = new GameEngine("session-1", "Ana", "Beto", new Random(1234),
                GameRules.DEFAULT, scoring);
    }

    @Test
    @DisplayName("o início distribui oito cartas, 10 moedas e ainda não abre a mesa")
    void startDealsRound() {
        assertEquals(GamePhase.WAITING, engine.phase());
        engine.start();

        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(1, engine.round());
        assertEquals(1, engine.currentPlayer());
        assertEquals(300, engine.hpOf(1));
        assertEquals(300, engine.hpOf(2));
        assertEquals(10, engine.coinsOf(1));
        assertEquals(10, engine.coinsOf(2));
        assertNotNull(engine.globalModifierId());
        assertTrue(engine.community().isEmpty(), "a mesa só abre depois das duas jogadas");

        List<Card> hand1 = engine.handOf(1);
        List<Card> hand2 = engine.handOf(2);
        assertEquals(8, hand1.size());
        assertEquals(8, hand2.size());
        assertEquals(16, new HashSet<>(concat(hand1, hand2)).size(), "os jogadores não podem compartilhar cartas");
        assertTrue(engine.snapshot().log().stream()
                .anyMatch(entry -> entry.text().startsWith("GLOBAL|")));
    }

    @Test
    @DisplayName("cada jogador envia 2 cartas, a mesa abre 5 e as sabotagens resolvem antes da pontuação")
    void communityAndSabotageFlow() {
        engine.start();
        List<Card> play1 = engine.handOf(1).subList(0, 2);
        engine.submitPlay(1, play1);
        assertEquals(2, engine.currentPlayer());
        assertEquals(GamePhase.BUILD, engine.phase());

        List<Card> play2 = engine.handOf(2).subList(0, 2);
        engine.submitPlay(2, play2);

        assertEquals(GamePhase.SABOTAGE, engine.phase());
        assertEquals(5, engine.community().size());
        assertEquals(1, engine.firstResolver());
        Set<Card> used = new HashSet<>(concat(engine.handOf(1), engine.handOf(2)));
        used.addAll(engine.community());
        assertEquals(21, used.size(), "mãos e mesa não compartilham cartas");

        engine.passSabotage(1);
        engine.passSabotage(2);
        assertEquals(GamePhase.REVEAL, engine.phase());
        assertNotNull(engine.snapshot().lastOutcome());
        assertEquals(5, engine.snapshot().lastOutcome().community().size());

        ScoreResult expected1 = scoring.score(combine(play1, engine.community()), List.of(),
                engine.globalModifierId());
        ScoreResult expected2 = scoring.score(combine(play2, engine.community()), List.of(),
                engine.globalModifierId());
        assertEquals(expected1.finalScore(), engine.snapshot().lastOutcome().player1Score().finalScore());
        assertEquals(expected2.finalScore(), engine.snapshot().lastOutcome().player2Score().finalScore());
    }

    @Test
    @DisplayName("ações inválidas são rejeitadas com códigos de erro explícitos")
    void invalidActions() {
        engine.start();
        List<Card> hand1 = engine.handOf(1);

        assertCode("NOT_YOUR_TURN", () -> engine.submitPlay(2, engine.handOf(2).subList(0, 2)));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of()));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, hand1.subList(0, 3)));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of(hand1.get(0), hand1.get(0))));
        assertCode("CARD_NOT_IN_HAND", () -> engine.submitPlay(1,
                List.of(engine.handOf(2).get(0), hand1.get(0))));
        assertCode("WRONG_PHASE", () -> engine.pickJoker(1, 0));
        assertCode("WRONG_PHASE", () -> engine.useSabotage(1, 0));
        assertCode("WRONG_PHASE", () -> engine.advance(1));
    }

    @Test
    @DisplayName("os instantâneos nunca vazam a mão do oponente")
    void hiddenInformation() {
        engine.start();
        GameSnapshot forPlayer1 = engine.snapshotFor(1);
        PlayerView own = forPlayer1.player(1);
        PlayerView opponent = forPlayer1.player(2);

        assertEquals(8, own.hand().size());
        assertTrue(own.handVisible());
        assertTrue(opponent.hand().isEmpty());
        assertFalse(opponent.handVisible());
        assertEquals(8, opponent.handCount());
        assertEquals("Beto", opponent.name());

        GameSnapshot spectator = engine.snapshot();
        assertEquals(8, spectator.player(1).hand().size());
        assertEquals(8, spectator.player(2).hand().size());
    }

    @Test
    @DisplayName("a loja oferece os mesmos 3 jokers e as mesmas 3 sabotagens, com compras por moedas")
    void shopFlow() {
        engine.start();
        finishRoundToShop();

        assertEquals(GamePhase.SHOP, engine.phase());
        List<String> offers1 = engine.snapshotFor(1).player(1).offers();
        List<String> offers2 = engine.snapshotFor(2).player(2).offers();
        assertEquals(3, offers1.size());
        assertEquals(offers1, offers2, "ambos recebem as mesmas três ofertas de joker");
        assertEquals(3, engine.snapshotFor(1).player(1).sabotageOffers().size());
        assertEquals(engine.snapshotFor(1).player(1).sabotageOffers(),
                engine.snapshotFor(2).player(2).sabotageOffers());
        assertTrue(engine.snapshotFor(1).player(2).offers().isEmpty(),
                "um jogador nunca vê as ofertas do outro");

        String picked = offers1.get(1);
        engine.pickJoker(1, 1);
        assertEquals(List.of(picked), engine.jokersOf(1));
        assertCode("ALREADY_PICKED", () -> engine.pickJoker(1, 0));

        int coinsBefore = engine.coinsOf(1);
        assertTrue(coinsBefore >= engine.rules().sabotagePrice());
        engine.buySabotage(1, 0);
        assertEquals(1, engine.sabotageHandOf(1).size());
        assertEquals(coinsBefore - engine.rules().sabotagePrice(), engine.coinsOf(1));

        engine.ready(1);
        assertEquals(GamePhase.SHOP, engine.phase(), "a loja espera os dois jogadores");
        engine.pickJoker(2, 0);
        engine.ready(2);

        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(2, engine.round());
        assertEquals(1, engine.currentPlayer());
        assertEquals(1, engine.jokersOf(1).size());
        assertEquals(1, engine.jokersOf(2).size());
        assertEquals(1, engine.sabotageHandOf(1).size());
        assertEquals(8, engine.handOf(1).size());
    }

    @Test
    @DisplayName("os rerolls grátis são limitados e depois custam 5, 10, 15 moedas na mesma visita")
    void rerolls() {
        engine.start();
        finishRoundToShop();

        List<String> firstOffers = engine.snapshotFor(1).player(1).offers();
        engine.reroll(1);
        assertEquals(1, engine.snapshotFor(1).player(1).rerolls());
        assertEquals(firstOffers, engine.snapshotFor(2).player(2).offers(),
                "o reroll não pode alterar as ofertas do oponente");

        engine.reroll(1);
        assertEquals(0, engine.snapshot().player(1).rerolls());
        assertEquals(5, engine.snapshot().player(1).nextRerollCost());

        int coins = engine.coinsOf(1);
        assertTrue(coins >= 15);
        engine.reroll(1);
        assertEquals(coins - 5, engine.coinsOf(1));
        assertEquals(10, engine.snapshot().player(1).nextRerollCost());
        engine.reroll(1);
        assertEquals(coins - 15, engine.coinsOf(1));
        assertEquals(15, engine.snapshot().player(1).nextRerollCost());

        engine.pickJoker(1, 0);
        assertCode("ALREADY_PICKED", () -> engine.reroll(1));
    }

    @Test
    @DisplayName("a ordem de sabotagem alterna a cada rodada e o log registra ORDEM antes de aplicar")
    void sabotageOrderAlternates() {
        engine.start();
        finishRoundToShop();
        completeShop();
        assertEquals(2, engine.round());
        assertEquals(2, engine.firstResolver(), "na rodada 2 o jogador 2 resolve primeiro");

        engine.submitPlay(1, engine.handOf(1).subList(0, 2));
        engine.submitPlay(2, engine.handOf(2).subList(0, 2));
        engine.passSabotage(1);
        engine.passSabotage(2);

        List<String> texts = engine.snapshot().log().stream().map(LogEntry::text).toList();
        int orderIndex = texts.indexOf("ORDEM|2|2");
        assertTrue(orderIndex >= 0, "o log deve conter ORDEM|2|2");
        int resultIndex = -1;
        for (int i = orderIndex + 1; i < texts.size(); i++) {
            if (texts.get(i).startsWith("RESULT|")) {
                resultIndex = i;
                break;
            }
        }
        assertTrue(resultIndex > orderIndex,
                "ORDEM deve ser registrado antes de aplicar as cartas");
    }

    @Test
    @DisplayName("usar uma sabotagem consome a carta e aplica debuff/reescrita antes da pontuação")
    void sabotageIsSingleUseAndResolvedBeforeScoring() {
        engine.start();
        finishRoundToShop();
        // Compra a primeira sabotagem ofertada e avança para a rodada 2.
        engine.buySabotage(1, 0);
        String sabotage = engine.sabotageHandOf(1).get(0);
        completeShop();

        engine.submitPlay(1, engine.handOf(1).subList(0, 2));
        engine.submitPlay(2, engine.handOf(2).subList(0, 2));
        engine.useSabotage(1, 0);
        engine.passSabotage(2);

        assertTrue(engine.sabotageHandOf(1).isEmpty(), "comprou, usou, acabou");
        List<String> texts = engine.snapshot().log().stream().map(LogEntry::text).toList();
        assertTrue(texts.contains("SABOTAGE|1|" + sabotage));
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("RESULT|")));
    }

    @Test
    @DisplayName("uma partida completa entre bots chega ao fim de jogo e permite revanche")
    void fullMatchToGameOver() {
        BotBrain brain = new BotBrain(scoring, 2);
        engine.start();
        int guard = 0;

        while (engine.phase() != GamePhase.GAME_OVER && guard++ < 5000) {
            switch (engine.phase()) {
                case BUILD -> {
                    int player = engine.currentPlayer();
                    List<Card> play = brain.choosePlay(engine.handOf(player),
                            engine.jokersOf(player), engine.globalModifierId(),
                            engine.community());
                    engine.submitPlay(player, play);
                }
                case SABOTAGE -> {
                    engine.useSabotage(1, botSabotageIndex(1));
                    engine.useSabotage(2, botSabotageIndex(2));
                }
                case REVEAL -> {
                    engine.advance(1);
                    engine.advance(2);
                }
                case SHOP -> {
                    if (!engine.snapshot().player(1).jokerPicked()) {
                        engine.pickJoker(1, 0);
                    } else if (!engine.snapshot().player(1).shopReady()) {
                        engine.ready(1);
                    }
                    if (!engine.snapshot().player(2).jokerPicked()) {
                        engine.pickJoker(2, 0);
                    } else if (!engine.snapshot().player(2).shopReady()) {
                        engine.ready(2);
                    }
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
        assertTrue(engine.snapshot().log().stream().anyMatch(entry -> entry.text().startsWith("AZARAO|")),
                "o bônus de azarão deve aparecer em uma partida longa");

        engine.requestRematch(1);
        assertEquals(GamePhase.GAME_OVER, engine.phase());
        engine.requestRematch(2);
        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(1, engine.round());
        assertEquals(300, engine.hpOf(1));
        assertEquals(300, engine.hpOf(2));
        assertEquals(10, engine.coinsOf(1));
        assertTrue(engine.jokersOf(1).isEmpty());
        assertTrue(engine.sabotageHandOf(1).isEmpty());
    }

    @Test
    @DisplayName("a mesma semente reproduz a mesma partida")
    void deterministicWithSeed() {
        GameEngine a = new GameEngine("x", "A", "B", new Random(99), GameRules.DEFAULT, scoring);
        GameEngine b = new GameEngine("x", "A", "B", new Random(99), GameRules.DEFAULT, scoring);
        a.start();
        b.start();
        assertEquals(a.handOf(1), b.handOf(1));
        assertEquals(a.globalModifierId(), b.globalModifierId());
    }

    private int botSabotageIndex(int player) {
        return engine.sabotageHandOf(player).isEmpty() ? -1 : 0;
    }

    private void finishRoundToShop() {
        engine.submitPlay(1, engine.handOf(1).subList(0, 2));
        engine.submitPlay(2, engine.handOf(2).subList(0, 2));
        engine.passSabotage(1);
        engine.passSabotage(2);
        assertEquals(GamePhase.REVEAL, engine.phase());
        engine.advance(1);
        engine.advance(2);
    }

    private void completeShop() {
        engine.pickJoker(1, 0);
        engine.ready(1);
        engine.pickJoker(2, 0);
        engine.ready(2);
    }

    private static List<Card> combine(List<Card> played, List<Card> community) {
        List<Card> pool = new ArrayList<>(played);
        pool.addAll(community);
        return pool;
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
}
