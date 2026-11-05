package com.game.net.protocol;

import java.util.List;

/** Ação genérica de jogador. Apenas os campos relevantes para a ação são preenchidos. */
public record ActionPayload(ActionType action, List<String> cards, Integer offerIndex) {

    public ActionPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    public static ActionPayload play(List<String> cardCodes) {
        return new ActionPayload(ActionType.SUBMIT_PLAY, cardCodes, null);
    }

    public static ActionPayload pickJoker(int index) {
        return new ActionPayload(ActionType.PICK_JOKER, List.of(), index);
    }

    public static ActionPayload reroll() {
        return new ActionPayload(ActionType.REROLL, List.of(), null);
    }

    public static ActionPayload advance() {
        return new ActionPayload(ActionType.ADVANCE, List.of(), null);
    }

    public static ActionPayload rematch() {
        return new ActionPayload(ActionType.REMATCH, List.of(), null);
    }
}
