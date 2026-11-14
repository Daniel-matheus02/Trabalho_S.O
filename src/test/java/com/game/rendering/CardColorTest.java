package com.game.rendering;

import com.game.core.model.Card;
import com.game.core.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Garante que a cor de cada parte da carta vem do naipe da própria carta. */
class CardColorTest {

    private static final List<String> SEQUENCE =
            List.of("AP", "JO", "JE", "6E", "4P", "QE", "8P", "6O");

    @Test
    void sequenceUsesOnlyTheCardsOwnSuitColour() {
        for (String code : SEQUENCE) {
            Card card = Card.fromCode(code);
            assertEquals(expected(card.suit()), CardRenderer.suitColor(card),
                    "Cor errada para " + card);
            assertEquals(CardRenderer.suitColor(card), CardActor.colorFor(card));
        }
    }

    @Test
    void suitsHaveDistinctColours() {
        assertNotEquals(CardRenderer.suitColor(Card.fromCode("AE")),
                CardRenderer.suitColor(Card.fromCode("AC")));
        assertNotEquals(CardRenderer.suitColor(Card.fromCode("AC")),
                CardRenderer.suitColor(Card.fromCode("AO")));
        assertNotEquals(CardRenderer.suitColor(Card.fromCode("AO")),
                CardRenderer.suitColor(Card.fromCode("AP")));
        System.out.println("[CardTest] spade=" + hex(CardRenderer.suitColor(Card.fromCode("AE")))
                + " heart=" + hex(CardRenderer.suitColor(Card.fromCode("AC")))
                + " diamond=" + hex(CardRenderer.suitColor(Card.fromCode("AO")))
                + " club=" + hex(CardRenderer.suitColor(Card.fromCode("AP"))));
    }

    private static com.badlogic.gdx.graphics.Color expected(Suit suit) {
        return switch (suit) {
            case SPADES -> Palette.SPADE;
            case HEARTS -> Palette.HEART;
            case DIAMONDS -> Palette.DIAMOND;
            case CLUBS -> Palette.CLUB;
        };
    }

    private static String hex(com.badlogic.gdx.graphics.Color color) {
        return String.format("#%02x%02x%02x", Math.round(color.r * 255),
                Math.round(color.g * 255), Math.round(color.b * 255));
    }
}
