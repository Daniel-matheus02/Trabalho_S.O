package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

import java.util.List;

/** Layout compartilhado das mãos de cartas (jogo e poderes). */
final class CardRowLayout {

    private CardRowLayout() {
    }

    /**
     * Distribui os atores em uma única linha centrada, com a mesma proporção de carta usada em
     * toda a mesa. Reaproveitado por {@link CardHand} e {@link PowerHand}.
     */
    static void layout(WidgetGroup group, List<? extends Actor> actors) {
        int count = actors.size();
        if (count == 0) {
            return;
        }
        float gap = Math.max(4f, Math.min(10f, group.getWidth() * 0.012f));
        float maxHeight = Math.max(80f, group.getHeight() - 22f);
        float cardWidth = maxHeight / 1.38f;
        cardWidth = Math.min(cardWidth, 112f);
        cardWidth = Math.min(cardWidth, (group.getWidth() - gap * (count - 1)) / count);
        cardWidth = Math.max(48f, cardWidth);
        float cardHeight = cardWidth * 1.38f;
        float total = count * cardWidth + gap * (count - 1);
        float startX = (group.getWidth() - total) / 2f;
        float y = (group.getHeight() - cardHeight) / 2f - 2f;
        for (int i = 0; i < count; i++) {
            actors.get(i).setBounds(startX + i * (cardWidth + gap), y, cardWidth, cardHeight);
        }
    }
}
