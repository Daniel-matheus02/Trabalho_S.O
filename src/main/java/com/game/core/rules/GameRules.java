package com.game.core.rules;

/**
 * Regras ajustáveis do duelo.
 *
 * <p>Os valores padrão implementam o alvo v0.6: cada jogador escolhe 2 cartas, a mesa abre 5
 * (flop/turn/river), sabotagens são resolvidas em ordem alternada e a economia usa moedas.
 * Os números de economia não fixados pelas notas da versão foram escolhidos como padrão e estão
 * documentados no README.</p>
 */
public record GameRules(
        int startingHp,
        int handSize,
        int maxPlay,
        int damageDivisor,
        int rerollsPerMatch,
        int communitySize,
        int startingCoins,
        int roundCoins,
        int winnerBonusCoins,
        int underdogBonusCoins,
        int maxSabotages,
        int sabotagePrice,
        int paidRerollBaseCost,
        int paidRerollIncrement
) {

    public static final GameRules DEFAULT = new GameRules(
            300, // vida inicial
            8,   // cartas na mão por rodada
            2,   // cartas escolhidas por jogador
            3,   // divisor do dano
            2,   // rerolls grátis de joker por partida
            5,   // cartas comunitárias
            10,  // moedas iniciais
            5,   // moedas por rodada para cada jogador
            5,   // bônus de moedas para o vencedor da rodada
            10,  // bônus de azarão após 2 derrotas seguidas
            2,   // sabotagens máximas em mãos
            8,   // preço de uma sabotagem na loja
            5,   // custo do primeiro reroll pago de joker na visita
            5    // acréscimo do reroll pago a cada uso na mesma visita
    );

    /** Construtor de compatibilidade com as regras anteriores à v0.3. */
    public GameRules(int startingHp, int handSize, int maxPlay, int damageDivisor, int rerollsPerMatch) {
        this(startingHp, handSize, maxPlay, damageDivisor, rerollsPerMatch,
                5, 10, 5, 5, 10, 2, 8, 5, 5);
    }

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
        if (rerollsPerMatch < 0) {
            throw new IllegalArgumentException("rerollsPerMatch não pode ser negativo");
        }
        if (communitySize < 0 || communitySize > 5) {
            throw new IllegalArgumentException("communitySize deve estar entre 0 e 5");
        }
        if (startingCoins < 0 || roundCoins < 0 || winnerBonusCoins < 0
                || underdogBonusCoins < 0 || sabotagePrice < 0) {
            throw new IllegalArgumentException("valores de moedas não podem ser negativos");
        }
        if (maxSabotages < 0) {
            throw new IllegalArgumentException("maxSabotages não pode ser negativo");
        }
        if (paidRerollBaseCost < 0 || paidRerollIncrement < 0) {
            throw new IllegalArgumentException("custos de reroll não podem ser negativos");
        }
    }
}
