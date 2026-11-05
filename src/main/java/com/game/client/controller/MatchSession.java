package com.game.client.controller;

import com.game.client.network.ClientEvent;
import com.game.core.model.Card;
import com.game.core.state.GameSnapshot;

import java.util.List;

/**
 * Abstração sobre onde uma partida reside: um {@code GameEngine} local para um jogador ou
 * um servidor autoritativo remoto para multijogador. As telas falam apenas com esta interface.
 */
public interface MatchSession extends AutoCloseable {

    int myPlayerId();

    GameSnapshot snapshot();

    boolean isSinglePlayer();

    void submitPlay(List<Card> cards);

    void pickJoker(int offerIndex);

    void reroll();

    void advance();

    void requestRematch();

    /** Chamado a cada quadro pela tela de jogo (usado pela IA local). */
    default void tick(float delta) {
    }

    /** Eventos de rede, se houver; sessões locais sempre retornam {@code null}. */
    default ClientEvent pollEvent() {
        return null;
    }

    default String statusMessage() {
        return "";
    }

    @Override
    default void close() {
    }
}
