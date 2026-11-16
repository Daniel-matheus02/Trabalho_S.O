package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.game.client.layout.LayoutMetrics;

import java.util.List;

/**
 * Layout compartilhado das mãos de cartas (jogo e poderes). O tamanho do retângulo vem SEMPRE do
 * {@link LayoutMetrics.CardMetrics} da tela: esta classe nunca calcula tamanho de carta, apenas
 * distribui os atores e, quando a fileira não cabe, aplica o leque (sobreposição limitada pelo
 * chamador). A mão do jogador usa um limite menor e reserva margem para a rotação de leque; a mão
 * de poderes mantém o limite histórico de {@link LayoutMetrics#MAX_POWER_OVERLAP}.
 */
final class CardRowLayout {

    private static final float MAX_OVERLAP = LayoutMetrics.MAX_POWER_OVERLAP;

    private CardRowLayout() {
    }

    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       LayoutMetrics.CardMetrics cards) {
        layout(group, actors, cards.handCardWidth(), cards.handCardHeight(), MAX_OVERLAP, 0f);
    }

    /**
     * @param maxOverlap  fração máxima de sobreposição entre cartas vizinhas
     * @param edgePadding margem horizontal extra em cada ponta (rotação das cartas externas)
     */
    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       LayoutMetrics.CardMetrics cards, float maxOverlap, float edgePadding) {
        layout(group, actors, cards.handCardWidth(), cards.handCardHeight(),
                maxOverlap, edgePadding);
    }

    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       float cardWidth, float cardHeight) {
        layout(group, actors, cardWidth, cardHeight, MAX_OVERLAP, 0f);
    }

    static void layout(WidgetGroup group, List<? extends Actor> actors,
                       float cardWidth, float cardHeight, float maxOverlap, float edgePadding) {
        int count = actors.size();
        if (count == 0) {
            return;
        }
        float padding = Math.max(0f, edgePadding);
        float footprint = cardWidth + padding * 2f;
        float width = group.getWidth();
        float step;
        if (count > 1 && width > 0f) {
            step = (width - footprint) / (count - 1);
            step = Math.min(cardWidth, Math.max(cardWidth * (1f - maxOverlap), step));
        } else {
            step = 0f;
        }
        float total = footprint + (count - 1) * step;
        float startX = (width - total) / 2f + padding;
        float y = (group.getHeight() - cardHeight) / 2f - 2f;
        for (int i = 0; i < count; i++) {
            actors.get(i).setBounds(startX + i * step, y, cardWidth, cardHeight);
        }
    }
}
