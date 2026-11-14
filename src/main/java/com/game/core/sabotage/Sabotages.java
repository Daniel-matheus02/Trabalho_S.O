package com.game.core.sabotage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de sabotagens da v0.3/v0.4.
 *
 * <p>As sabotagens de mesa reescrevem o tabuleiro compartilhado; as de debuff afetam apenas a
 * pontuação do oponente na rodada. Todas são de uso único.</p>
 */
public final class Sabotages {

    private static final Map<String, SabotageDef> BY_ID = new LinkedHashMap<>();

    static {
        register("anti_flush", "Anti-Flush",
                "Anula o flush do oponente nesta rodada.", SabotageCategory.DEBUFF);
        register("peso_chumbo", "Peso de Chumbo",
                "O oponente perde 5 de mult nesta rodada.", SabotageCategory.DEBUFF);
        register("imposto_par", "Imposto do Par",
                "O oponente perde 25 fichas se a mão final tiver par ou melhor.",
                SabotageCategory.DEBUFF);
        register("bloqueio_joker", "Bloqueio de Joker",
                "Desativa o primeiro joker do oponente nesta rodada.", SabotageCategory.DEBUFF);
        register("nevoa", "Névoa",
                "Anula todos os jokers do oponente nesta rodada.", SabotageCategory.DEBUFF);
        register("virada_naipes", "Virada de Naipes",
                "Inverte os naipes das 5 cartas da mesa (espadas↔copas, ouros↔paus).",
                SabotageCategory.MESA);
        register("troca_river", "Troca de River",
                "Substitui a carta do river por uma nova carta do baralho.",
                SabotageCategory.MESA);
    }

    private Sabotages() {
    }

    private static void register(String id, String name, String description, SabotageCategory category) {
        BY_ID.put(id, new SabotageDef(id, name, description, category));
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
