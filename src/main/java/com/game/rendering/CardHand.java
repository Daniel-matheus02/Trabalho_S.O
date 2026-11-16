package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.game.assets.GameAssets;
import com.game.client.layout.LayoutMetrics;
import com.game.client.layout.LayoutMetrics.CardMetrics;
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
    private CardMetrics cardMetrics = LayoutMetrics.cardMetrics(1365f, 695f);
    private IntConsumer onToggle;

    public CardHand(GameAssets assets) {
        this.assets = assets;
        setTransform(false);
    }

    /** Liga ou desliga a animação de distribuição (respeita a preferência de movimento reduzido). */
    public void setAnimateDeals(boolean animateDeals) {
        this.animateDeals = animateDeals;
    }

    /** Tamanho único da mão: cartas, curingas, sabotagens e versos usam este retângulo. */
    public void setCardMetrics(CardMetrics cardMetrics) {
        if (cardMetrics == null || cardMetrics.equals(this.cardMetrics)) {
            return;
        }
        this.cardMetrics = cardMetrics;
        invalidate();
    }

    public CardMetrics cardMetrics() {
        return cardMetrics;
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
        boolean animate = animateDeals && cards.isEmpty();
        cards = next;
        this.interactive = interactive;
        rebuild(animate);
        applySelection();
    }

    private void rebuild(boolean animateCards) {
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
            if (animateCards) {
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

    /**
     * Remove da mão os atores dos índices pedidos e devolve-os para que a mesa os adote. A lista
     * {@link #cards} só é atualizada no próximo {@link #setCards}, que reconstrói as restantes.
     */
    public java.util.List<CardActor> takeActors(java.util.List<Integer> indices) {
        java.util.List<CardActor> taken = new ArrayList<>();
        for (int index : indices) {
            if (index >= 0 && index < actors.size()) {
                taken.add(actors.get(index));
            }
        }
        // Os atores continuam presos à mão até a mesa reparentá-los; assim a conversão de
        // coordenadas para o palco continua válida.
        actors.removeAll(taken);
        return taken;
    }

    public java.util.List<CardActor> actorsSnapshot() {
        return java.util.List.copyOf(actors);
    }

    private void applySelection() {
        for (int i = 0; i < actors.size(); i++) {
            boolean isSelected = selected.contains(i);
            actors.get(i).setSelected(isSelected);
            if (isSelected) {
                actors.get(i).toFront();
            }
        }
    }

    @Override
    public void layout() {
        CardRowLayout.layout(this, actors, cardMetrics);
    }
}
