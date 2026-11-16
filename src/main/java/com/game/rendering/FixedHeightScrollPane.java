package com.game.rendering;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * ScrollPane com altura preferida fixa, independente do conteúdo.
 *
 * O ScrollPane do libGDX reporta como altura preferida a altura do conteúdo. Dentro de um Table
 * isso faz o painel crescer junto com o log: o corpo da tela fica mais alto que a janela e a
 * barra de rolagem externa passa a rolar a tela inteira. Com a altura fixa, o painel mantém o
 * tamanho reservado pelo layout e apenas o conteúdo rola, com a barra interna.
 */
public final class FixedHeightScrollPane extends ScrollPane {

    private final float preferredHeight;

    public FixedHeightScrollPane(Actor widget, Skin skin, float preferredHeight) {
        super(widget, skin);
        this.preferredHeight = Math.max(0f, preferredHeight);
    }

    @Override
    public float getPrefHeight() {
        return preferredHeight;
    }
}
