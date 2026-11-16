package com.game.rendering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipTextTest {

    @Test
    void chipsAndSuitsAreColoredAndTextIsPreserved() {
        String text = "+15 fichas por ♠ jogada";
        List<TooltipText.Span> spans = TooltipText.colorize(text);

        assertEquals(text, join(spans));
        assertTrue(spans.stream().anyMatch(span ->
                span.color() == TooltipColor.CHIPS && span.text().equals("+15 fichas")));
        assertTrue(spans.stream().anyMatch(span ->
                span.color() == TooltipColor.SPADE && span.text().equals("♠")));
    }

    @Test
    void multAndMultiplierUseTheMultColor() {
        String text = "O oponente perde 5 de mult nesta rodada.";
        List<TooltipText.Span> spans = TooltipText.colorize(text);

        assertEquals(text, join(spans));
        assertTrue(spans.stream().anyMatch(span ->
                span.color() == TooltipColor.MULT && span.text().equals("mult")));
    }

    @Test
    void adjacentMultTokensAreMerged() {
        List<TooltipText.Span> spans = TooltipText.colorize("mult x2");
        assertEquals(1, spans.size());
        assertEquals(new TooltipText.Span("mult x2", TooltipColor.MULT), spans.get(0));
    }

    @Test
    void allFourSuitsHaveTheirOwnColors() {
        List<TooltipText.Span> spans = TooltipText.colorize("♠♥♦♣");
        assertEquals(4, spans.size());
        assertEquals(TooltipColor.SPADE, spans.get(0).color());
        assertEquals(TooltipColor.HEART, spans.get(1).color());
        assertEquals(TooltipColor.DIAMOND, spans.get(2).color());
        assertEquals(TooltipColor.CLUB, spans.get(3).color());
    }

    @Test
    void emptyAndNullAreSafe() {
        assertTrue(TooltipText.colorize(null).isEmpty());
        assertTrue(TooltipText.colorize("").isEmpty());
    }

    private static String join(List<TooltipText.Span> spans) {
        StringBuilder text = new StringBuilder();
        for (TooltipText.Span span : spans) {
            text.append(span.text());
        }
        return text.toString();
    }
}
