package com.game.client.controller;

import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.core.model.Card;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;

import java.util.ArrayList;
import java.util.List;

/** Sessão de partida respaldada pela conexão com o servidor autoritativo. */
public final class NetworkMatchSession implements MatchSession {

    private final GameClient client;
    private final int playerId;

    public NetworkMatchSession(GameClient client) {
        this.client = client;
        var info = client.sessionInfo();
        if (info == null) {
            throw new IllegalStateException("O cliente não está em uma sessão");
        }
        this.playerId = info.playerId();
    }

    @Override
    public int myPlayerId() {
        return playerId;
    }

    @Override
    public GameSnapshot snapshot() {
        return client.latestSnapshot();
    }

    @Override
    public boolean isSinglePlayer() {
        return false;
    }

    @Override
    public void submitPlay(List<Card> cards) {
        List<String> codes = new ArrayList<>(cards.size());
        for (Card card : cards) {
            codes.add(card.code());
        }
        client.sendAction(ActionPayload.play(codes));
    }

    @Override
    public void pickJoker(int offerIndex) {
        client.sendAction(ActionPayload.pickJoker(offerIndex));
    }

    @Override
    public void reroll() {
        client.sendAction(ActionPayload.reroll());
    }

    @Override
    public void advance() {
        client.sendAction(ActionPayload.advance());
    }

    @Override
    public void requestRematch() {
        client.sendAction(ActionPayload.rematch());
    }

    @Override
    public ClientEvent pollEvent() {
        return client.pollEvent();
    }

    public GameClient client() {
        return client;
    }

    @Override
    public void close() {
        client.close();
    }
}
