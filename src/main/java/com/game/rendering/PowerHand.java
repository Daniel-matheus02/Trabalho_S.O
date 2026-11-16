package com.game.rendering;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.game.assets.GameAssets;
import com.game.client.layout.LayoutMetrics;
import com.game.client.layout.LayoutMetrics.CardMetrics;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * A mão de poderes da rodada: sete cartas na ordem enviada pelo servidor, seleção limitada por
 * {@link #setMaxSelected(int)} e recusa com tremor ao tentar uma terceira carta.
 */
public final class PowerHand extends WidgetGroup {

    private final GameAssets assets;
    private final List<PowerCardActor> actors = new ArrayList<>();
    private final Set<Integer> selected = new LinkedHashSet<>();

    private List<PowerCard> cards = List.of();
    private boolean interactive;
    private boolean animateDeals = true;
    private int maxSelected = 2;
    private CardMetrics cardMetrics = LayoutMetrics.cardMetrics(1365f, 695f);
    private CardTooltip tooltip;
    private IntConsumer onToggle;
    private Runnable onSelectionChanged;

    public PowerHand(GameAssets assets) {
        this.assets = assets;
        setTransform(false);
    }

    public void setAnimateDeals(boolean animateDeals) {
        this.animateDeals = animateDeals;
    }

    /** Curingas, sabotagens e versos compartilham exatamente o retângulo da mão de cartas. */
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

    public void setOnSelectionChanged(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setTooltip(CardTooltip tooltip) {
        this.tooltip = tooltip;
        for (PowerCardActor actor : actors) {
            actor.setTooltip(tooltip);
        }
    }

    public void setMaxSelected(int maxSelected) {
        this.maxSelected = Math.max(0, maxSelected);
        while (selected.size() > this.maxSelected) {
            int last = -1;
            for (int index : selected) {
                last = index;
            }
            selected.remove(last);
        }
        applySelection();
    }

    public void setCards(List<PowerCard> newCards, boolean interactive) {
        List<PowerCard> next = newCards == null ? List.of() : List.copyOf(newCards);
        if (next.equals(cards) && this.interactive == interactive) {
            applySelection();
            return;
        }
        boolean deal = animateDeals && cards.isEmpty() && !next.isEmpty();
        cards = next;
        this.interactive = interactive;
        rebuild(deal);
        applySelection();
    }

    private void rebuild(boolean dealCards) {
        clearChildren();
        actors.clear();
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            PowerCardActor actor = new PowerCardActor(assets, cards.get(i), false,
                    () -> {
                        if (onToggle != null) {
                            onToggle.accept(index);
                        } else {
                            toggle(index);
                        }
                    });
            actor.setInteractive(interactive);
            if (tooltip != null) {
                actor.setTooltip(tooltip);
            }
            actor.getColor().a = 1f;
            if (dealCards) {
                actor.showInstantly();
                actor.getColor().a = 0f;
                actor.setScale(0.9f);
                // A subida é um deslocamento de desenho: mexer no y do ator brigaria com o leiaute
                // do leque (e deixava as cartas 40px acima da área da mão).
                actor.startRise(index * 0.1f, 0.35f);
                actor.addAction(Actions.sequence(
                        Actions.delay(index * 0.1f),
                        Actions.parallel(
                                Actions.fadeIn(0.35f),
                                Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut))));
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
        selected.clear();
        if (selection != null) {
            for (int index : selection) {
                if (index >= 0 && index < cards.size() && selected.size() < maxSelected) {
                    selected.add(index);
                }
            }
        }
        applySelection();
        notifySelectionChanged();
    }

    public Set<Integer> selection() {
        return new LinkedHashSet<>(selected);
    }

    public List<Integer> sortedSelection() {
        return selected.stream().sorted().toList();
    }

    public void clearSelection() {
        if (selected.isEmpty()) {
            return;
        }
        selected.clear();
        applySelection();
        notifySelectionChanged();
    }

    /** Clique em uma carta: alterna a seleção ou recusa a terceira com tremor. */
    public void toggle(int index) {
        if (!interactive || index < 0 || index >= actors.size()) {
            return;
        }
        if (selected.remove(index)) {
            applySelection();
            notifySelectionChanged();
            return;
        }
        if (selected.size() >= maxSelected) {
            Ui.shakeHorizontally(actors.get(index));
            return;
        }
        selected.add(index);
        applySelection();
        notifySelectionChanged();
    }

    public void setInteractive(boolean interactive) {
        if (this.interactive == interactive) {
            return;
        }
        this.interactive = interactive;
        for (PowerCardActor actor : actors) {
            actor.setInteractive(interactive);
        }
    }

    public boolean isInteractive() {
        return interactive;
    }

    /** Reexecuta a animação de distribuição sem trocar as cartas (DevPreview). */
    public void replayDeal() {
        for (int i = 0; i < actors.size(); i++) {
            actors.get(i).getColor().a = 1f;
            actors.get(i).startDeal(i * 0.045f, 0.34f);
        }
    }

    public PowerCardActor actor(int index) {
        return index >= 0 && index < actors.size() ? actors.get(index) : null;
    }

    public List<PowerCard> cards() {
        return List.copyOf(cards);
    }

    /** Restaura as cartas da mão após uma apresentação de resolução/replay. */
    public void resetVisuals() {
        for (int i = 0; i < actors.size(); i++) {
            PowerCardActor actor = actors.get(i);
            actor.clearActions();
            actor.setVisible(true);
            actor.setScale(1f, 1f);
            actor.getColor().a = 1f;
            actor.showInstantly();
            actor.setFanAngle(HandCardActor.fanAngleAt(i, actors.size()));
        }
    }

    /** QA: injeta hover determinístico na primeira carta do tipo pedido. */
    public boolean demoHoverFirst(PowerKind kind) {
        for (int i = 0; i < actors.size(); i++) {
            PowerCardActor actor = actors.get(i);
            if (kind == null || (actor.power() != null && actor.power().kind() == kind)) {
                actor.demoHover(actor.getWidth() * 0.72f, actor.getHeight() / 2f);
                return true;
            }
        }
        return false;
    }

    public void demoUnhover() {
        for (PowerCardActor actor : actors) {
            actor.demoUnhover();
        }
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

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    @Override
    public void layout() {
        CardRowLayout.layout(this, actors, cardMetrics);
        for (int i = 0; i < actors.size(); i++) {
            actors.get(i).setFanAngle(HandCardActor.fanAngleAt(i, actors.size()));
        }
    }
}
