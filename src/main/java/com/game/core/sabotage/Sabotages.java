package com.game.core.sabotage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo das seis sabotagens da rodada.
 *
 * <p>As sabotagens de mesa reescrevem a jogada do oponente; as de debuff afetam apenas a
 * pontuação do oponente na rodada.</p>
 */
public final class Sabotages {

    private static final Map<String, SabotageDef> BY_ID = new LinkedHashMap<>();

    static {
        register("anti_flush", "Panelinha?",
                "Anula o flush do oponente nesta rodada.");
        register("peso_chumbo", "Pay to Play",
                "O oponente perde 2 de mult nesta rodada.");
        register("imposto_par", "Nem meu, nem seu",
                "O oponente perde 25 fichas se a mão final tiver par ou melhor.");
        register("nevoa", "Sem graça!",
                "Anula todos os jokers do oponente nesta rodada.");
        register("virada_naipes", "Vira casaca",
                "Inverte os naipes das 5 cartas da jogada do oponente (espadas↔copas, ouros↔paus).");
        register("troca_carta", "ÁRBITRO???",
                "Substitui uma carta aleatória da jogada do oponente por uma nova carta do baralho.");
    }

    private Sabotages() {
    }

    private static void register(String id, String name, String description) {
        BY_ID.put(id, new SabotageDef(id, name, description));
    }

    public static List<SabotageDef> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static SabotageDef byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static String nameOf(String id) {
        SabotageDef def = byId(id);
        return def == null ? id : def.name();
    }

    public static String descriptionOf(String id) {
        SabotageDef def = byId(id);
        return def == null ? "" : def.description();
    }
}
