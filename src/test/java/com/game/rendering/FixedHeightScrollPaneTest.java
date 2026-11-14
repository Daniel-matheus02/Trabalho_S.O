package com.game.rendering;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Garante que o painel de log mantenha a altura reservada pelo layout: quem cresce é o conteúdo
 * (com rolagem interna), não a página.
 */
class FixedHeightScrollPaneTest {

    @BeforeAll
    static void installApp() {
        // O ScrollPane registra um ActorGestureListener e o Timer exige Gdx.app não nulo; o layout
        // em si não usa nenhum método da aplicação, então um stub inerte basta.
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == int.class) {
                        return 0;
                    }
                    if (type == long.class) {
                        return 0L;
                    }
                    if (type == float.class) {
                        return 0f;
                    }
                    return null;
                });
    }

    @AfterAll
    static void removeApp() {
        Gdx.app = null;
    }

    /** Conteúdo de altura variável, sem depender de Table/Cell (que exigem Gdx.files). */
    private static final class GrowingContent extends Widget {
        private float prefHeight;

        GrowingContent(float prefHeight) {
            this.prefHeight = prefHeight;
        }

        void growTo(float value) {
            prefHeight = value;
        }

        @Override
        public float getPrefHeight() {
            return prefHeight;
        }
    }

    private static Skin skin() {
        Skin skin = new Skin();
        skin.add("default", new ScrollPane.ScrollPaneStyle());
        return skin;
    }

    @Test
    void preferredHeightIgnoresContentGrowth() {
        GrowingContent content = new GrowingContent(900f);
        FixedHeightScrollPane pane = new FixedHeightScrollPane(content, skin(), 120f);
        pane.setScrollingDisabled(true, false);

        assertEquals(120f, pane.getPrefHeight(), 0.001f);

        content.growTo(4000f);
        content.invalidateHierarchy();

        assertEquals(120f, pane.getPrefHeight(), 0.001f);
    }

    @Test
    void standardScrollPaneWouldGrowWithTheContent() {
        // Contrato do libGDX que motivou a classe: a altura preferida acompanha o conteúdo.
        GrowingContent content = new GrowingContent(900f);
        ScrollPane standard = new ScrollPane(content, skin());
        standard.setScrollingDisabled(true, false);

        assertEquals(900f, standard.getPrefHeight(), 0.001f);

        content.growTo(4000f);

        assertEquals(4000f, standard.getPrefHeight(), 0.001f);
    }
}
