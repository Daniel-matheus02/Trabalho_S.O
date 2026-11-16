package com.game.core.scoring;

import com.game.core.model.HandType;
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
        register("festa", "Festa dos Naipes", "+6 fichas por carta do naipe mais frequente na m\u00e3o", context -> {
            int best = 0;
            for (com.game.core.model.Suit suit : com.game.core.model.Suit.values()) {
                best = Math.max(best, context.countSuit(suit));
            }
            if (best > 0) {
                int value = 6 * best;
                context.addChips(value);
                context.addPlusLine("Festa dos Naipes: +" + value + " fichas");
            }
        });
        register("mao_fraca", "M\u00e3o Fraca", "mult \u00d72 se a m\u00e3o for Carta Alta ou Par", context -> {
            if (context.type() == HandType.HIGH_CARD || context.type() == HandType.PAIR) {
                context.multiplyMult(2);
                context.addMultLine("M\u00e3o Fraca: mult x2");
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
