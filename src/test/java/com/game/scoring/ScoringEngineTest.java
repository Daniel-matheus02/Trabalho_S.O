package com.game.scoring;

import com.game.core.model.Card;
import com.game.core.model.HandType;
import com.game.core.scoring.LineStyle;
import com.game.core.scoring.ScoreModifiers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringEngineTest {

    private final ScoringEngine scoring = new ScoringEngine();

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(Card::fromCode).toList();
    }

    @Test
    @DisplayName("todas as categorias de mão seguem a ordem e as regras de referência")
    void handCategories() {
        assertEquals(HandType.HIGH_CARD, evaluate("AE", "KC"));
        assertEquals(HandType.PAIR, evaluate("AE", "AC"));
        assertEquals(HandType.TWO_PAIR, evaluate("AE", "AC", "KO", "KP"));
        assertEquals(HandType.THREE_OF_A_KIND, evaluate("AE", "AC", "AO"));
        assertEquals(HandType.STRAIGHT, evaluate("5E", "6C", "7O", "8P", "9E"));
        assertEquals(HandType.FLUSH, evaluate("2E", "5E", "9E", "JE", "KE"));
        assertEquals(HandType.FULL_HOUSE, evaluate("AE", "AC", "AO", "KE", "KC"));
        assertEquals(HandType.FOUR_OF_A_KIND, evaluate("AE", "AC", "AO", "AP"));
        assertEquals(HandType.STRAIGHT_FLUSH, evaluate("5E", "6E", "7E", "8E", "9E"));
    }

    @Test
    @DisplayName("a roda A-2-3-4-5 conta como sequência")
    void wheelStraight() {
        assertEquals(HandType.STRAIGHT, evaluate("AE", "2C", "3O", "4P", "5E"));
        assertEquals(HandType.STRAIGHT_FLUSH, evaluate("AE", "2E", "3E", "4E", "5E"));
    }

    @Test
    @DisplayName("cinco cartas não consecutivas não formam sequência")
    void notStraight() {
        assertEquals(HandType.HIGH_CARD, evaluate("AE", "2C", "3O", "4P", "6E"));
        assertEquals(HandType.HIGH_CARD, evaluate("AE", "2C", "3O", "4P", "KE"));
    }

    @Test
    @DisplayName("fichas base, fichas das cartas e multiplicador batem com o protótipo")
    void baseScoring() {
        ScoreResult result = scoring.score(cards("AE", "AC"), List.of(), null);
        // base 10 + (11 + 11) fichas, mult x2
        assertEquals(HandType.PAIR, result.type());
        assertEquals(32, result.chips());
        assertEquals(2, result.mult());
        assertEquals(64, result.finalScore());
        assertEquals(LineStyle.NORMAL, result.lines().get(0).style());
        assertEquals("base Par: 10 fichas x 2 mult", result.lines().get(0).text());
        assertEquals("cartas: +22 fichas", result.lines().get(1).text());
    }

    @Test
    @DisplayName("cartas de figura somam 10 fichas e o ás soma 11")
    void cardChipValues() {
        assertEquals(10, Card.fromCode("TE").rank().chipValue());
        assertEquals(10, Card.fromCode("JE").rank().chipValue());
        assertEquals(10, Card.fromCode("QE").rank().chipValue());
        assertEquals(10, Card.fromCode("KE").rank().chipValue());
        assertEquals(11, Card.fromCode("AE").rank().chipValue());
        assertEquals(7, Card.fromCode("7E").rank().chipValue());
    }

    @Test
    @DisplayName("seleção vazia pré-visualiza como Carta Alta com 5 fichas")
    void emptySelection() {
        ScoreResult result = scoring.score(List.of(), List.of(), null);
        assertEquals(HandType.HIGH_CARD, result.type());
        assertEquals(5, result.chips());
        assertEquals(1, result.mult());
        assertEquals(5, result.finalScore());
    }

    @Test
    @DisplayName("modificadores globais aplicam exatamente uma vez, antes dos curingas")
    void globalModifiers() {
        ScoreResult rich = scoring.score(cards("AE", "AC"), List.of(), "mesa_rica");
        assertEquals(52, rich.chips());

        ScoreResult pressure = scoring.score(cards("AE", "AC"), List.of(), "pressao");
        assertEquals(3, pressure.mult());

        ScoreResult party = scoring.score(cards("AE", "AC"), List.of(), "festa");
        assertEquals(32 + 6, party.chips()); // 32 + 6 pela única carta do naipe mais frequente

        ScoreResult weakPair = scoring.score(cards("AE", "AC"), List.of(), "mao_fraca");
        // base Par: 32 fichas, mult 2 x2
        assertEquals(32, weakPair.chips());
        assertEquals(4, weakPair.mult());

        ScoreResult gold = scoring.score(cards("AO", "2O"), List.of(), "ouro");
        assertEquals(18 + 20, gold.chips()); // Carta Alta: 5 + 11 + 2 fichas, +20 pelas duas cartas de ouros
    }

    @Test
    @DisplayName("peso_chumbo agora tira 2 de mult e o mult nunca fica abaixo de 1")
    void pesoChumboNerfAndMultFloor() {
        ScoreResult pair = scoring.score(cards("AE", "AC"), List.of(), null,
                ScoreModifiers.NONE.withMultPenalty(2), List.of("peso_chumbo"));
        assertEquals(32, pair.chips());
        assertEquals(1, pair.mult(), "2 mult - 2 deve parar no piso de 1");
        assertEquals(32, pair.finalScore());
        assertTrue(pair.lines().stream()
                .anyMatch(line -> line.text().equals("Peso de Chumbo: -2 mult")));

        ScoreResult high = scoring.score(cards("AE"), List.of(), null,
                ScoreModifiers.NONE.withMultPenalty(2), List.of("peso_chumbo"));
        assertEquals(1, high.mult());
        assertEquals(high.chips(), high.finalScore(),
                "mesmo sabotado, o jogador pontua fichas x 1");
        assertTrue(high.finalScore() > 0);
    }

    @Test
    @DisplayName("o modificador global Mão Fraca só dobra Carta Alta e Par")
    void weakHandOnlyBoostsHighCardOrPair() {
        assertEquals(2, scoring.score(cards("AE"), List.of(), "mao_fraca").mult());
        assertEquals(4, scoring.score(cards("AE", "AC"), List.of(), "mao_fraca").mult());
        assertEquals(3, scoring.score(cards("AE", "AC", "AO"), List.of(), "mao_fraca").mult());
    }

    @Test
    @DisplayName("cada curinga aplica seu efeito exato")
    void jokers() {
        // par de ases: 32 fichas / 2 mult
        assertEquals(32 + 15, scoring.score(cards("AE", "AC"), List.of("naipe_afiado"), null).chips());
        assertEquals(32 + 15, scoring.score(cards("AO", "AC"), List.of("ouro_ardente"), null).chips());
        assertEquals(32 + 15, scoring.score(cards("AC", "AO"), List.of("coracao"), null).chips());
        assertEquals(32 + 15, scoring.score(cards("AP", "AC"), List.of("pau_potente"), null).chips());
        assertEquals(65 + 50, scoring.score(cards("5E", "6C", "7O", "8P", "9E"),
                List.of("escada"), null).chips());
        assertEquals(71 + 30, scoring.score(cards("2E", "5E", "9E", "JE", "KE"),
                List.of("rio"), null).chips());
        assertEquals(2 + 4, scoring.score(cards("AE", "AC"), List.of("par_mult"), null).mult());
        assertEquals(63 + 60, scoring.score(cards("AE", "AC", "AO"), List.of("trinca_ouro"), null).chips());
        assertEquals(4 + 8, scoring.score(cards("AE", "AC", "AO", "KE", "KC"),
                List.of("quintuplus"), null).mult());
    }

    @Test
    @DisplayName("curingas que exigem um tipo de mão mínimo não disparam abaixo dele")
    void jokerConditions() {
        ScoreResult pair = scoring.score(cards("AE", "AC"), List.of("trinca_ouro"), null);
        assertEquals(32, pair.chips());

        ScoreResult highCard = scoring.score(cards("AE"), List.of("par_mult"), null);
        assertEquals(1, highCard.mult());

        ScoreResult weakPair = scoring.score(cards("AE", "AC"), List.of("quintuplus"), null);
        assertEquals(2, weakPair.mult());

        ScoreResult straight = scoring.score(cards("5E", "6C", "7O", "8P", "9E"),
                List.of("rio"), null);
        assertEquals(65, straight.chips(), "O Mar exige flush ou melhor");
    }

    @Test
    @DisplayName("os curingas acumulam na ordem de aquisição")
    void jokerOrder() {
        ScoreResult result = scoring.score(cards("AE", "AP"), List.of("naipe_afiado", "par_mult"), null);
        assertEquals(47, result.chips());
        assertEquals(6, result.mult());
        assertEquals(282, result.finalScore());
    }

    @Test
    @DisplayName("modificadores desconhecidos são ignorados")
    void unknownModifiersIgnored() {
        ScoreResult result = scoring.score(cards("AE", "AC"), List.of("nope"), "nope");
        assertEquals(64, result.finalScore());
    }

    @Test
    @DisplayName("a pontuação final arredonda fichas x mult")
    void rounding() {
        // Carta Alta, um rei: 5 + 10 = 15 fichas, mult 1; pressao dá +1 => 30
        ScoreResult result = scoring.score(cards("KE"), List.of(), "pressao");
        assertEquals(30, result.finalScore());
        assertTrue(result.lines().stream().anyMatch(line -> line.style() == LineStyle.MULT));
    }

    @Test
    @DisplayName("a melhor mão de 5 entre as cartas jogadas é usada na pontuação")
    void bestFiveFromPlayedCards() {
        // Três ases + K + 9 formam a melhor mão entre as cinco cartas jogadas.
        ScoreResult result = scoring.score(
                cards("AE", "AC", "AO", "KE", "9O"), List.of(), null);
        assertEquals(HandType.THREE_OF_A_KIND, result.type());
        assertEquals(5, result.scoringCards().size());
        assertEquals(3, result.scoringCards().stream()
                .filter(card -> card.rank() == com.game.core.model.Rank.ACE).count());
        assertEquals(30 + 11 + 11 + 11 + 10 + 9, result.chips());
    }

    @Test
    @DisplayName("a sabotagem Anti-Flush reclassifica a mão sem o flush")
    void antiFlushCancelsFlush() {
        List<Card> pool = cards("2E", "5E", "9E", "JE", "KE");
        assertEquals(HandType.FLUSH, scoring.score(pool, List.of(), null).type());
        ScoreResult blocked = scoring.score(pool, List.of(), null,
                ScoreModifiers.NONE.withBlockFlush());
        assertTrue(blocked.type() != HandType.FLUSH);
        assertTrue(blocked.lines().stream().anyMatch(line -> line.text().contains("Anti-Flush")));
    }

    @Test
    @DisplayName("Peso de Chumbo, Imposto do Par e Névoa alteram a pontuação final")
    void sabotageScoringModifiers() {
        List<Card> pair = cards("AE", "AC");

        ScoreResult heavy = scoring.score(pair, List.of(), null,
                ScoreModifiers.NONE.withMultPenalty(2));
        assertEquals(1, heavy.mult(), "o mult nunca cai abaixo de 1");
        assertEquals(32, heavy.finalScore());

        ScoreResult taxed = scoring.score(pair, List.of(), null,
                ScoreModifiers.NONE.withPairTax());
        assertEquals(32 - 25, taxed.chips());

        ScoreResult fog = scoring.score(pair, List.of("par_mult"), null,
                ScoreModifiers.NONE.withNullifyJokers());
        assertEquals(32, fog.chips(), "Névoa anula todos os jokers da rodada");
        assertEquals(2, fog.mult());
    }

    private HandType evaluate(String... codes) {
        return scoring.score(cards(codes), List.of(), null).type();
    }
}
