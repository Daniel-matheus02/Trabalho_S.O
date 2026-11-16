package com.game.core.scoring;

import com.game.core.model.HandType;
import com.game.core.model.Suit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Os nove curingas do jogo, com seus efeitos exatos sobre as cinco cartas jogadas. */
public final class Jokers {

    private static final Map<String, JokerDef> BY_ID = new LinkedHashMap<>();

    static {
        register("naipe_afiado", "Briga de Espada", "+15 fichas por carta de espadas jogada", context -> {
            int spades = context.countSuit(Suit.SPADES);
            if (spades > 0) {
                int value = 15 * spades;
                context.addChips(value);
                context.addPlusLine("Briga de Espada: +" + value + " fichas");
            }
        });
        register("ouro_ardente", "Chuva de Ouro", "+15 fichas por carta de ouros jogada", context -> {
            int diamonds = context.countSuit(Suit.DIAMONDS);
            if (diamonds > 0) {
                int value = 15 * diamonds;
                context.addChips(value);
                context.addPlusLine("Chuva de Ouro: +" + value + " fichas");
            }
        });
        register("coracao", "Cortem a Cabeça!", "+15 fichas por carta de copas jogada", context -> {
            int hearts = context.countSuit(Suit.HEARTS);
            if (hearts > 0) {
                int value = 15 * hearts;
                context.addChips(value);
                context.addPlusLine("Cortem a Cabeça!: +" + value + " fichas");
            }
        });
        register("pau_potente", "Pau Potente", "+15 fichas por carta de paus jogada", context -> {
            int clubs = context.countSuit(Suit.CLUBS);
            if (clubs > 0) {
                int value = 15 * clubs;
                context.addChips(value);
                context.addPlusLine("Pau Potente: +" + value + " fichas");
            }
        });
        register("escada", "Escada Alta", "+50 fichas se a mão for sequência ou melhor", context -> {
            if (context.typeAtLeast(HandType.STRAIGHT)) {
                context.addChips(50);
                context.addPlusLine("Escada Alta: +50 fichas");
            }
        });
        register("rio", "O Mar Está Para Fichas", "+30 fichas se a mão for flush ou melhor", context -> {
            if (context.typeAtLeast(HandType.FLUSH)) {
                context.addChips(30);
                context.addPlusLine("O Mar Está Para Fichas: +30 fichas");
            }
        });
        register("par_mult", "Par Perfeito", "+4 mult com par ou melhor", context -> {
            if (context.typeAtLeast(HandType.PAIR)) {
                context.addMult(4);
                context.addMultLine("Par Perfeito: +4 mult");
            }
        });
        register("trinca_ouro", "Três é Mais Gostoso", "+60 fichas com trinca ou melhor", context -> {
            if (context.typeAtLeast(HandType.THREE_OF_A_KIND)) {
                context.addChips(60);
                context.addPlusLine("Três é Mais Gostoso: +60 fichas");
            }
        });
        register("quintuplus", "Quíntuplus", "+8 mult com Full House ou melhor", context -> {
            if (context.typeAtLeast(HandType.FULL_HOUSE)) {
                context.addMult(8);
                context.addMultLine("Quíntuplus: +8 mult");
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
