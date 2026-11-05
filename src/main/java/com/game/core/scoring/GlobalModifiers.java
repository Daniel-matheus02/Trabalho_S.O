package com.game.core.scoring;

import com.game.core.model.Suit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Os cinco modificadores globais por partida do jogo de referência. */
public final class GlobalModifiers {

    private static final Map<String, GlobalModifierDef> BY_ID = new LinkedHashMap<>();

    static {
        register("mesa_rica", "Mesa Rica", "+20 fichas em toda jogada", context -> {
            context.addChips(20);
            context.addPlusLine("Mesa Rica: +20 fichas");
        });
        register("pressao", "Press\u00e3o Alta", "+1 mult em toda jogada", context -> {
            context.addMult(1);
            context.addMultLine("Press\u00e3o Alta: +1 mult");
        });
        register("festa", "Festa dos Naipes", "+6 fichas por carta jogada", context -> {
            int value = 6 * context.cardCount();
            context.addChips(value);
            context.addPlusLine("Festa dos Naipes: +" + value + " fichas");
        });
        register("dobradinha", "Dobradinha", "mult \u00d72 se jogar 1 ou 2 cartas", context -> {
            if (context.cardCount() > 0 && context.cardCount() <= 2) {
                context.multiplyMult(2);
                context.addMultLine("Dobradinha: mult x2");
            }
        });
        register("ouro", "Ouro Corrido", "+10 fichas por \u2666 jogado", context -> {
            int diamonds = context.countSuit(Suit.DIAMONDS);
            if (diamonds > 0) {
                int value = 10 * diamonds;
                context.addChips(value);
                context.addPlusLine("Ouro Corrido: +" + value + " fichas");
            }
        });
    }

    private GlobalModifiers() {
    }

    private static void register(String id, String name, String description, ScoreEffect effect) {
        BY_ID.put(id, new GlobalModifierDef(id, name, description, effect));
    }

    public static List<GlobalModifierDef> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static GlobalModifierDef byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
