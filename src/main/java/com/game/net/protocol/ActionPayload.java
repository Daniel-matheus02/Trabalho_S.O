package com.game.net.protocol;

import java.util.List;

/** Ação genérica de jogador. Apenas os campos relevantes para a ação são preenchidos. */
public record ActionPayload(ActionType action, List<String> cards, List<Integer> powerIndices) {

    public ActionPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
        powerIndices = powerIndices == null ? List.of() : List.copyOf(powerIndices);
    }

    public static ActionPayload play(List<String> cardCodes) {
        return new ActionPayload(ActionType.SUBMIT_PLAY, cardCodes, List.of());
    }

    public static ActionPayload discard(List<String> cardCodes) {
        return new ActionPayload(ActionType.DISCARD_CARDS, cardCodes, List.of());
    }

    public static ActionPayload lockPowers(List<Integer> powerIndices) {
        return new ActionPayload(ActionType.LOCK_POWERS, List.of(), powerIndices);
    }

    public static ActionPayload advance() {
        return new ActionPayload(ActionType.ADVANCE, List.of(), List.of());
    }

    public static ActionPayload rematch() {
        return new ActionPayload(ActionType.REMATCH, List.of(), List.of());
    }
}
