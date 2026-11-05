package com.game.core.scoring;

/** Comportamento aplicado durante a pontuação. Mantido pequeno para que os modificadores continuem orientados a dados. */
@FunctionalInterface
public interface ScoreEffect {
    void apply(ScoreContext context);
}
