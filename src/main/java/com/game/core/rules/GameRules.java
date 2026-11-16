package com.game.core.rules;

/**
 * Regras ajustáveis do duelo.
 *
 * <p>Os valores padrão implementam o alvo atual: cada jogador recebe 8 cartas e escolhe exatamente
 * 5 para a rodada; não há cartas comunitárias. Depois que as duas mãos são enviadas, cada jogador
 * recebe {@code jokersPerRound} jokers e {@code sabotagesPerRound} sabotagens embaralhados em uma
 * única mão e pode jogar até {@code maxPowersPlayed} cartas dessa mão, em qualquer combinação de
 * tipos. Não há loja, moedas nem rerolls.</p>
 */
public record GameRules(
        int startingHp,
        int handSize,
        int maxPlay,
        int damageDivisor,
        int jokersPerRound,
        int sabotagesPerRound,
        int maxPowersPlayed
) {

    public static final GameRules DEFAULT = new GameRules(
            300, // vida inicial
            8,   // cartas na mão por rodada
            5,   // cartas escolhidas por jogador
            3,   // divisor do dano
            4,   // jokers recebidos por rodada
            3,   // sabotagens recebidas por rodada
            2    // poderes que podem ser jogados por rodada
    );

    public GameRules {
        if (startingHp <= 0) {
            throw new IllegalArgumentException("startingHp deve ser positivo");
        }
        if (handSize <= 0 || handSize > 26) {
            throw new IllegalArgumentException("handSize fora do intervalo");
        }
        if (maxPlay <= 0 || maxPlay > handSize) {
            throw new IllegalArgumentException("maxPlay deve estar entre 1 e handSize");
        }
        if (damageDivisor <= 0) {
            throw new IllegalArgumentException("damageDivisor deve ser positivo");
        }
        if (jokersPerRound < 0 || sabotagesPerRound < 0) {
            throw new IllegalArgumentException("poderes por rodada não podem ser negativos");
        }
        int dealt = jokersPerRound + sabotagesPerRound;
        if (maxPowersPlayed < 0 || maxPowersPlayed > dealt) {
            throw new IllegalArgumentException(
                    "maxPowersPlayed deve estar entre 0 e os poderes recebidos (" + dealt + ")");
        }
    }
}
