package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.game.client.layout.LayoutMetrics;

import java.util.List;

/**
 * Layout compartilhado das mãos de cartas (jogo e poderes). O tamanho do retângulo vem SEMPRE do
 * {@link LayoutMetrics.CardMetrics} da tela: esta classe nunca calcula tamanho de carta, apenas
 * distribui os atores e, quando a fileira não cabe, aplica o leque (no máximo 38% de sobreposição).
 */
final class CardRowLayout {

    private static final float MAX_OVERLAP = LayoutMetrics.MAX_POWER_OVERLAP;

    private CardRowLayout() {
    }

    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       LayoutMetrics.CardMetrics cards) {
        layout(group, actors, cards.handCardWidth(), cards.handCardHeight());
    }

    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       float cardWidth, float cardHeight) {
        int count = actors.size();
        if (count == 0) {
            return;
        }
        float width = group.getWidth();
        float step;
        if (count > 1 && width > 0f) {
            step = (group.getWidth() - cardWidth) / (count - 1);
            step = Math.min(cardWidth, Math.max(cardWidth * (1f - MAX_OVERLAP), step));
        } else {
            step = 0f;
        }
        float total = cardWidth + (count - 1) * step;
        float startX = (group.getWidth() - total) / 2f;
        float y = (group.getHeight() - cardHeight) / 2f - 2f;
        for (int i = 0; i < count; i++) {
            actors.get(i).setBounds(startX + i * step, y, cardWidth, cardHeight);
        }
    }
}
