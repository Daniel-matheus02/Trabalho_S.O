package com.game.core.scoring;

/** Um passo anotado da decomposição da pontuação. */
public record ScoreLine(String text, LineStyle style) {

    public static ScoreLine normal(String text) {
        return new ScoreLine(text, LineStyle.NORMAL);
    }

    public static ScoreLine plus(String text) {
        return new ScoreLine(text, LineStyle.PLUS);
    }

    public static ScoreLine mult(String text) {
        return new ScoreLine(text, LineStyle.MULT);
    }
}
