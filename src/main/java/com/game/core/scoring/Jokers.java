package com.game.core.scoring;

import com.game.core.model.HandType;
import com.game.core.model.Suit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todos os curingas individuais do jogo de referência, com seus efeitos exatos. */
public final class Jokers {

    private static final Map<String, JokerDef> BY_ID = new LinkedHashMap<>();

    static {
        register("naipe_afiado", "Naipe Afiado", "+15 fichas por \u2660 jogada", context -> {
            int spades = context.countSuit(Suit.SPADES);
            if (spades > 0) {
                int value = 15 * spades;
                context.addChips(value);
                context.addPlusLine("Naipe Afiado: +" + value + " fichas");
            }
        });
        register("combo5", "Combo Qu\u00edntuplo", "mult \u00d72 ao jogar 5 cartas", context -> {
            if (context.cardCount() == 5) {
                context.multiplyMult(2);
                context.addMultLine("Combo Qu\u00edntuplo: mult x2");
            }
        });
        register("mao_cheia", "M\u00e3o Cheia", "+50 fichas com par ou melhor", context -> {
            if (context.typeAtLeast(HandType.PAIR)) {
                context.addChips(50);
                context.addPlusLine("M\u00e3o Cheia: +50 fichas");
            }
        });
        register("rio", "Rio de Fichas", "+30 fichas se for flush", context -> {
            if (context.type().isFlushFamily()) {
                context.addChips(30);
                context.addPlusLine("Rio de Fichas: +30 fichas");
            }
        });
        register("par_mult", "Par Perfeito", "+4 mult com par ou melhor", context -> {
            if (context.typeAtLeast(HandType.PAIR)) {
                context.addMult(4);
                context.addMultLine("Par Perfeito: +4 mult");
            }
        });
        register("coracao", "Cora\u00e7\u00e3o Valente", "+12 fichas por \u2665 jogada", context -> {
            int hearts = context.countSuit(Suit.HEARTS);
            if (hearts > 0) {
                int value = 12 * hearts;
                context.addChips(value);
                context.addPlusLine("Cora\u00e7\u00e3o Valente: +" + value + " fichas");
            }
        });
        register("trinca_ouro", "Trinca de Ouro", "+60 fichas com trinca ou melhor", context -> {
            if (context.typeAtLeast(HandType.THREE_OF_A_KIND)) {
                context.addChips(60);
                context.addPlusLine("Trinca de Ouro: +60 fichas");
            }
        });
        register("escada", "Escada Firme", "+50 fichas se sequ\u00eancia", context -> {
            if (context.type().isStraightFamily()) {
                context.addChips(50);
                context.addPlusLine("Escada Firme: +50 fichas");
            }
        });
        register("alto_risco", "Alto Risco", "+40 fichas ao jogar s\u00f3 1 carta", context -> {
            if (context.cardCount() == 1) {
                context.addChips(40);
                context.addPlusLine("Alto Risco: +40 fichas");
            }
        });
    }

    private Jokers() {
    }

    private static void register(String id, String name, String description, ScoreEffect effect) {
        BY_ID.put(id, new JokerDef(id, name, description, effect));
    }

    public static List<JokerDef> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static JokerDef byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static String nameOf(String id) {
        JokerDef def = byId(id);
        return def == null ? id : def.name();
    }

    public static String descriptionOf(String id) {
        JokerDef def = byId(id);
        return def == null ? "" : def.description();
    }
}
