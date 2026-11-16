package com.game.rendering;

import com.badlogic.gdx.math.MathUtils;
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

    /** A mão do jogador fica claramente aberta; só sobrepõe quando não houver espaço. */
    private static final float HAND_OVERLAP = 0.12f;
    /** Quanto as pontas do leque sobem, para o arco parecer natural. */
    private static final float ARC_LIFT = 7f;

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
        int count = actors.size();
        if (count == 0) {
            return;
        }
        float cardWidth = cardMetrics.handCardWidth();
        float cardHeight = cardMetrics.handCardHeight();
        float maxTilt = HandCardActor.maxFanTilt(count);
        // Como as cartas giram em torno do centro, as pontas do leque ocupam mais largura do que
        // o retângulo reto: reserva essa margem para nada encostar na borda da mão.
        float edgePadding = rotationEdgePadding(cardWidth, cardHeight, maxTilt);
        CardRowLayout.layout(this, actors, cardWidth, cardHeight, HAND_OVERLAP, edgePadding);

        float center = (count - 1) / 2f;
        for (int i = 0; i < count; i++) {
            CardActor actor = actors.get(i);
            float offset = i - center;
            float normalized = count <= 1 ? 0f : Math.abs(offset) / center;
            actor.setY(actor.getY() + ARC_LIFT * normalized * normalized);
            actor.setFanAngle(HandCardActor.fanAngleAt(i, count));
        }
    }

    /** Metade do excesso horizontal causado pela rotação da carta da ponta. */
    private static float rotationEdgePadding(float cardWidth, float cardHeight,
                                             float maxTiltDegrees) {
        if (cardWidth <= 0f || cardHeight <= 0f || maxTiltDegrees <= 0f) {
            return 0f;
        }
        float radians = maxTiltDegrees * MathUtils.degreesToRadians;
        float rotatedWidth = cardWidth * MathUtils.cos(radians)
                + cardHeight * MathUtils.sin(radians);
        return Math.max(0f, (rotatedWidth - cardWidth) / 2f);
    }
}
