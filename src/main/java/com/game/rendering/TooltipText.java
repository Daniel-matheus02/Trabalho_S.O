package com.game.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colorização pura do texto de um tooltip. Não depende de libGDX: devolve spans com uma chave
 * semântica de cor que a UI traduz para {@link Palette}.
 *
 * <p>Regras: "+N fichas" (ou "N fichas") em CHIPS; "mult" e "xN"/"×N" em MULT; os quatro
 * glifos de naipe em suas cores. O texto é preservado integralmente e spans vizinhos da mesma cor
 * são fundidos.</p>
 */
public final class TooltipText {

    public record Span(String text, TooltipColor color) {
    }

    private static final Pattern CHIPS = Pattern.compile("[+-]?\\d+\\s*fichas",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MULT_WORD = Pattern.compile("\\bmult\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MULT_FACTOR = Pattern.compile("[x×]\\s*\\d+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SUIT = Pattern.compile("[♠♥♦♣]");

    private TooltipText() {
    }

    public static List<Span> colorize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        collect(CHIPS, text, TooltipColor.CHIPS, matches);
        collect(MULT_WORD, text, TooltipColor.MULT, matches);
        collect(MULT_FACTOR, text, TooltipColor.MULT, matches);
        collectSuit(text, matches);
        matches.sort((a, b) -> a.start != b.start
                ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));

        List<Span> spans = new ArrayList<>();
        int cursor = 0;
        int lastEnd = -1;
        for (Match match : matches) {
            if (match.start < lastEnd) {
                continue;
            }
            if (match.start > cursor) {
                String gap = text.substring(cursor, match.start);
                TooltipColor gapColor = !spans.isEmpty()
                        && spans.get(spans.size() - 1).color() == match.color
                        && gap.isBlank() ? match.color : TooltipColor.INK;
                add(spans, gap, gapColor);
            }
            add(spans, text.substring(match.start, match.end), match.color);
            cursor = match.end;
            lastEnd = match.end;
        }
        if (cursor < text.length()) {
            add(spans, text.substring(cursor), TooltipColor.INK);
        }
        return List.copyOf(spans);
    }

    private static void collect(Pattern pattern, String text, TooltipColor color,
                                List<Match> matches) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(new Match(matcher.start(), matcher.end(), color));
        }
    }

    private static void collectSuit(String text, List<Match> matches) {
        Matcher matcher = SUIT.matcher(text);
        while (matcher.find()) {
            TooltipColor color = switch (matcher.group()) {
                case "♠" -> TooltipColor.SPADE;
                case "♥" -> TooltipColor.HEART;
                case "♦" -> TooltipColor.DIAMOND;
                default -> TooltipColor.CLUB;
            };
            matches.add(new Match(matcher.start(), matcher.end(), color));
        }
    }

    private static void add(List<Span> spans, String text, TooltipColor color) {
        if (text.isEmpty()) {
            return;
        }
        if (!spans.isEmpty() && spans.get(spans.size() - 1).color() == color) {
            Span previous = spans.remove(spans.size() - 1);
            spans.add(new Span(previous.text() + text, color));
        } else {
            spans.add(new Span(text, color));
        }
    }

    private record Match(int start, int end, TooltipColor color) {
    }
}
