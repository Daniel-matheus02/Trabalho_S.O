package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.game.assets.GameAssets;
import com.game.core.model.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/** Posiciona as cartas do jogador, anima a distribuição e repassa os cliques nas cartas. */
public final class CardHand extends WidgetGroup {

    private final GameAssets assets;
    private final List<CardActor> actors = new ArrayList<>();
    private List<Card> cards = List.of();
    private Set<Integer> selected = Set.of();
    private boolean interactive;
    private boolean animateDeals = true;
    private IntConsumer onToggle;

    public CardHand(GameAssets assets) {
        this.assets = assets;
        setTransform(false);
    }

    /** Liga ou desliga a animação de distribuição (respeita a preferência de movimento reduzido). */
    public void setAnimateDeals(boolean animateDeals) {
        this.animateDeals = animateDeals;
    }

    public void setOnToggle(IntConsumer onToggle) {
        this.onToggle = onToggle;
    }

    public void setCards(List<Card> newCards, boolean interactive) {
        List<Card> next = newCards == null ? List.of() : List.copyOf(newCards);
        if (next.equals(cards) && this.interactive == interactive) {
            applySelection();
            return;
        }
        boolean sameCount = next.size() == cards.size();
        cards = next;
        this.interactive = interactive;
        if (!sameCount || actors.size() != cards.size()) {
            rebuild();
        } else {
            // Mesmo número de cartas, mas faces diferentes: reconstrói sem repetir a animação de distribuição.
            rebuild();
        }
        applySelection();
    }

    private void rebuild() {
        clearChildren();
        actors.clear();
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            CardActor actor = new CardActor(assets, cards.get(i), false,
                    () -> {
                        if (onToggle != null) {
                            onToggle.accept(index);
                        }
                    });
            actor.setInteractive(interactive);
            actor.getColor().a = 1f;
            if (animateDeals) {
                actor.startDeal(index * 0.055f, 0.34f);
            } else {
                actor.showInstantly();
            }
            actors.add(actor);
            addActor(actor);
        }
        invalidate();
        invalidateHierarchy();
    }

    public void setSelectedIndices(Set<Integer> selection) {
        this.selected = selection == null ? Set.of() : new HashSet<>(selection);
        applySelection();
    }

    public void setInteractive(boolean interactive) {
        if (this.interactive == interactive) {
            return;
        }
        this.interactive = interactive;
        for (CardActor actor : actors) {
            actor.setInteractive(interactive);
        }
    }

    public void setDisabled(boolean disabled) {
        for (CardActor actor : actors) {
            actor.setDisabled(disabled);
        }
    }

    public int cardCount() {
        return cards.size();
    }

    private void applySelection() {
        for (int i = 0; i < actors.size(); i++) {
            actors.get(i).setSelected(selected.contains(i));
        }
    }

    @Override
    public void layout() {
        int count = actors.size();
        if (count == 0) {
            return;
        }
        float gap = Math.max(4f, Math.min(10f, getWidth() * 0.012f));
        float maxHeight = Math.max(80f, getHeight() - 22f);
        float cardWidth = maxHeight / 1.38f;
        cardWidth = Math.min(cardWidth, 112f);
        cardWidth = Math.min(cardWidth, (getWidth() - gap * (count - 1)) / count);
        cardWidth = Math.max(48f, cardWidth);
        float cardHeight = cardWidth * 1.38f;
        float total = count * cardWidth + gap * (count - 1);
        float startX = (getWidth() - total) / 2f;
        float y = (getHeight() - cardHeight) / 2f - 2f;
        for (int i = 0; i < count; i++) {
            actors.get(i).setBounds(startX + i * (cardWidth + gap), y, cardWidth, cardHeight);
        }
    }
}
