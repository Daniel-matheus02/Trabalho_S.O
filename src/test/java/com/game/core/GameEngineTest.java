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
import com.game.core.state.PlayerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

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
    @DisplayName("o início distribui oito cartas para cada jogador e escolhe um modificador global")
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
        assertEquals(16, new HashSet<>(concat(hand1, hand2)).size(), "os jogadores não podem compartilhar cartas");
        assertTrue(engine.snapshot().log().stream()
                .anyMatch(entry -> entry.text().startsWith("GLOBAL|")));
    }

    @Test
    @DisplayName("os jogadores alternam os turnos de construção e o combate é resolvido depois que ambos enviam")
    void turnOrderAndReveal() {
        engine.start();
        List<Card> play1 = engine.handOf(1).subList(0, 3);
        engine.submitPlay(1, play1);

        assertEquals(2, engine.currentPlayer());
        assertEquals(GamePhase.BUILD, engine.phase());

        List<Card> play2 = engine.handOf(2).subList(0, 2);
        engine.submitPlay(2, play2);

        assertEquals(GamePhase.REVEAL, engine.phase());
        assertEquals(0, engine.currentPlayer());
        assertNotNull(engine.snapshot().lastOutcome());

        ScoreResult expected1 = scoring.score(play1, List.of(), engine.globalModifierId());
        ScoreResult expected2 = scoring.score(play2, List.of(), engine.globalModifierId());
        assertEquals(expected1.finalScore(), engine.snapshot().lastOutcome().player1Score().finalScore());
        assertEquals(expected2.finalScore(), engine.snapshot().lastOutcome().player2Score().finalScore());

        int damage = engine.snapshot().lastOutcome().damage();
        int hp1 = engine.hpOf(1);
        int hp2 = engine.hpOf(2);
        if (expected1.finalScore() > expected2.finalScore()) {
            assertEquals(300 - damage, hp2);
            assertEquals(300, hp1);
        } else if (expected2.finalScore() > expected1.finalScore()) {
            assertEquals(300 - damage, hp1);
            assertEquals(300, hp2);
        } else {
            assertEquals(300, hp1);
            assertEquals(300, hp2);
            assertEquals(0, damage);
        }
    }

    @Test
    @DisplayName("ações inválidas são rejeitadas com códigos de erro explícitos")
    void invalidActions() {
        engine.start();
        List<Card> hand1 = engine.handOf(1);

        assertCode("NOT_YOUR_TURN", () -> engine.submitPlay(2, engine.handOf(2).subList(0, 1)));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of()));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, hand1.subList(0, 6)));
        assertCode("INVALID_SELECTION", () -> engine.submitPlay(1, List.of(hand1.get(0), hand1.get(0))));
        assertCode("CARD_NOT_IN_HAND", () -> engine.submitPlay(1,
                List.of(engine.handOf(2).get(0))));
        assertCode("WRONG_PHASE", () -> engine.pickJoker(1, 0));
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
    @DisplayName("o draft oferece os mesmos três curingas para ambos os jogadores e mantém as escolhas")
    void draftFlow() {
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 2));
        engine.submitPlay(2, engine.handOf(2).subList(0, 2));

        engine.advance(1);
        assertEquals(GamePhase.REVEAL, engine.phase());
        engine.advance(2);

        assertEquals(GamePhase.DRAFT, engine.phase());
        assertEquals(1, engine.currentPlayer());

        List<String> offers1 = engine.snapshotFor(1).player(1).offers();
        List<String> offers2 = engine.snapshotFor(2).player(2).offers();
        assertEquals(3, offers1.size());
        assertEquals(offers1, offers2, "ambos os jogadores recebem as mesmas três ofertas");

        // um jogador nunca vê o painel de draft do oponente
        assertTrue(engine.snapshotFor(1).player(2).offers().isEmpty());

        String picked = offers1.get(1);
        engine.pickJoker(1, 1);
        assertEquals(2, engine.currentPlayer());
        assertEquals(GamePhase.DRAFT, engine.phase());
        assertEquals(List.of(picked), engine.jokersOf(1));

        engine.pickJoker(2, 0);
        assertEquals(GamePhase.BUILD, engine.phase());
        assertEquals(2, engine.round());
        assertEquals(1, engine.currentPlayer());
        assertEquals(1, engine.jokersOf(1).size());
        assertEquals(1, engine.jokersOf(2).size());
        assertEquals(8, engine.handOf(1).size());
    }

    @Test
    @DisplayName("os rerolls são limitados por jogador e substituem apenas as ofertas daquele jogador")
    void rerolls() {
        engine.start();
        engine.submitPlay(1, engine.handOf(1).subList(0, 1));
        engine.submitPlay(2, engine.handOf(2).subList(0, 1));
        engine.advance(1);
        engine.advance(2);

        List<String> firstOffers = engine.snapshotFor(1).player(1).offers();
        engine.reroll(1);
        assertEquals(1, engine.snapshotFor(1).player(1).rerolls());
        assertEquals(firstOffers, engine.snapshotFor(2).player(2).offers(),
                "o reroll não pode alterar as ofertas do oponente");

        engine.reroll(1);
        assertEquals(0, engine.snapshot().player(1).rerolls());
        assertCode("NO_REROLLS", () -> engine.reroll(1));
        assertCode("NOT_YOUR_TURN", () -> engine.reroll(2));
    }

    @Test
    @DisplayName("uma partida completa entre bots chega ao fim de jogo e permite revanche")
    void fullMatchToGameOver() {
        BotBrain brain = new BotBrain(scoring, 5);
        engine.start();
        int guard = 0;

        while (engine.phase() != GamePhase.GAME_OVER && guard++ < 500) {
            switch (engine.phase()) {
                case BUILD -> {
                    int player = engine.currentPlayer();
                    List<Card> play = brain.choosePlay(engine.handOf(player),
                            engine.jokersOf(player), engine.globalModifierId());
                    engine.submitPlay(player, play);
                }
                case REVEAL -> {
                    engine.advance(1);
                    engine.advance(2);
                }
                case DRAFT -> {
                    int player = engine.currentPlayer();
                    GameSnapshot snapshot = engine.snapshotFor(player);
                    List<String> offers = snapshot.player(player).offers();
                    if (brain.shouldReroll(offers, engine.handOf(player),
                            engine.jokersOf(player), engine.globalModifierId(),
                            snapshot.player(player).rerolls())) {
                        engine.reroll(player);
                        offers = engine.snapshotFor(player).player(player).offers();
                    }
                    engine.pickJoker(player, brain.chooseJoker(offers, engine.handOf(player),
                            engine.jokersOf(player), engine.globalModifierId()));
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
        assertTrue(engine.jokersOf(1).isEmpty());
        assertTrue(engine.jokersOf(2).isEmpty());
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

    private static List<Card> concat(List<Card> a, List<Card> b) {
        java.util.ArrayList<Card> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void assertCode(String code, Runnable action) {
        GameRuleException exception = assertThrows(GameRuleException.class, action::run);
        assertEquals(code, exception.code(), exception.getMessage());
    }
}
