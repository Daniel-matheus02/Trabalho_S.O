package com.game.core.state;

import com.game.core.model.Card;
import com.game.core.model.Deck;
import com.game.core.model.Suit;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
import com.game.core.rules.RoundOutcome;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.GlobalModifierDef;
import com.game.core.scoring.GlobalModifiers;
import com.game.core.scoring.Jokers;
import com.game.core.scoring.ScoreModifiers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Motor de regras autoritativo de uma partida. Não sabe nada sobre renderização ou rede.
 *
 * <p>Fluxo v0.6: iniciar &rarr; distribuir 8 cartas &rarr; cada jogador envia 2 cartas &rarr; a mesa
 * abre 5 cartas &rarr; cada jogador usa uma sabotagem ou passa (ordem alternada por rodada) &rarr;
 * pontuação e dano &rarr; loja (joker + sabotagens) &rarr; próxima rodada. Todos os métodos que
 * alteram estado são synchronized, para que possam ser chamados com segurança a partir de threads
 * de rede.</p>
 */
@SuppressWarnings("unchecked")
public final class GameEngine {

    public static final int PLAYER_ONE = 1;
    public static final int PLAYER_TWO = 2;
    public static final int OBSERVER = 0;

    public static final String STYLE_NORMAL = "";
    public static final String STYLE_RESULT = "res";
    public static final String STYLE_GAME_OVER = "fim";
    public static final String STYLE_DRAFT = "draft";

    private static final int MAX_LOG_ENTRIES = 200;

    private final String sessionId;
    private final GameRules rules;
    private final ScoringEngine scoringEngine;
    private final Random random;

    private final String[] playerNames = new String[3];
    private final int[] hp = new int[3];
    private final int[] coins = new int[3];
    private final int[] lossStreak = new int[3];
    private final List<Card>[] hands = new List[3];
    private final List<String>[] jokers = new List[3];
    private final List<String>[] offers = new List[3];
    private final List<String>[] sabotageHands = new List[3];
    private final List<String>[] sabotageOffers = new List[3];
    private final int[] rerolls = new int[3];
    private final int[] paidRerolls = new int[3];
    private final boolean[] ready = new boolean[3];
    private final boolean[] shopReady = new boolean[3];
    private final boolean[] jokerPicked = new boolean[3];
    private final boolean[] sabotageChosen = new boolean[3];
    private final String[] chosenSabotage = new String[3];
    private final boolean[] rematch = new boolean[3];
    private final List<Card>[] pendingCards = new List[3];

    private final List<LogEntry> log = new ArrayList<>();

    private List<Card> community = List.of();
    private List<Card> drawPile = List.of();

    private GamePhase phase = GamePhase.WAITING;
    private int round = 1;
    private int currentPlayer = 0;
    private int firstResolver = PLAYER_ONE;
    private int winner = 0;
    private String globalModifierId;
    private RoundOutcome lastOutcome;
    private long version;

    public GameEngine(String sessionId, String player1Name, String player2Name) {
        this(sessionId, player1Name, player2Name, new Random(), GameRules.DEFAULT, new ScoringEngine());
    }

    public GameEngine(String sessionId, String player1Name, String player2Name, Random random,
                      GameRules rules, ScoringEngine scoringEngine) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.random = Objects.requireNonNull(random, "random");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.scoringEngine = Objects.requireNonNull(scoringEngine, "scoringEngine");
        playerNames[PLAYER_ONE] = normaliseName(player1Name, "Jogador 1");
        playerNames[PLAYER_TWO] = normaliseName(player2Name, "Jogador 2");
        resetInternalState();
    }

    private static String normaliseName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }

    private void resetInternalState() {
        java.util.Arrays.fill(hands, List.of());
        java.util.Arrays.fill(jokers, List.of());
        java.util.Arrays.fill(offers, List.of());
        java.util.Arrays.fill(sabotageHands, List.of());
        java.util.Arrays.fill(sabotageOffers, List.of());
        java.util.Arrays.fill(pendingCards, null);
        java.util.Arrays.fill(chosenSabotage, null);
        java.util.Arrays.fill(ready, false);
        java.util.Arrays.fill(shopReady, false);
        java.util.Arrays.fill(jokerPicked, false);
        java.util.Arrays.fill(sabotageChosen, false);
        java.util.Arrays.fill(rematch, false);
        java.util.Arrays.fill(coins, 0);
        java.util.Arrays.fill(lossStreak, 0);
        java.util.Arrays.fill(paidRerolls, 0);
        hp[PLAYER_ONE] = rules.startingHp();
        hp[PLAYER_TWO] = rules.startingHp();
        coins[PLAYER_ONE] = rules.startingCoins();
        coins[PLAYER_TWO] = rules.startingCoins();
        rerolls[PLAYER_ONE] = rules.rerollsPerMatch();
        rerolls[PLAYER_TWO] = rules.rerollsPerMatch();
        hands[PLAYER_ONE] = new ArrayList<>();
        hands[PLAYER_TWO] = new ArrayList<>();
        jokers[PLAYER_ONE] = new ArrayList<>();
        jokers[PLAYER_TWO] = new ArrayList<>();
        offers[PLAYER_ONE] = new ArrayList<>();
        offers[PLAYER_TWO] = new ArrayList<>();
        sabotageHands[PLAYER_ONE] = new ArrayList<>();
        sabotageHands[PLAYER_TWO] = new ArrayList<>();
        sabotageOffers[PLAYER_ONE] = new ArrayList<>();
        sabotageOffers[PLAYER_TWO] = new ArrayList<>();
        community = List.of();
        drawPile = List.of();
        phase = GamePhase.WAITING;
        round = 1;
        currentPlayer = 0;
        firstResolver = PLAYER_ONE;
        winner = 0;
        globalModifierId = null;
        lastOutcome = null;
        log.clear();
    }

    // ------------------------------------------------------------------ consultas

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized GameRules rules() {
        return rules;
    }

    public synchronized GamePhase phase() {
        return phase;
    }

    public synchronized int currentPlayer() {
        return currentPlayer;
    }

    public synchronized int round() {
        return round;
    }

    public synchronized int firstResolver() {
        return firstResolver;
    }

    public synchronized int winner() {
        return winner;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized int hpOf(int player) {
        ensurePlayer(player);
        return Math.max(0, hp[player]);
    }

    public synchronized int coinsOf(int player) {
        ensurePlayer(player);
        return coins[player];
    }

    public synchronized int lossStreakOf(int player) {
        ensurePlayer(player);
        return lossStreak[player];
    }

    public synchronized List<Card> handOf(int player) {
        ensurePlayer(player);
        return List.copyOf(hands[player]);
    }

    public synchronized List<String> jokersOf(int player) {
        ensurePlayer(player);
        return List.copyOf(jokers[player]);
    }

    public synchronized List<String> sabotageHandOf(int player) {
        ensurePlayer(player);
        return List.copyOf(sabotageHands[player]);
    }

    public synchronized List<Card> community() {
        return List.copyOf(community);
    }

    public synchronized String globalModifierId() {
        return globalModifierId;
    }

    public synchronized boolean isStarted() {
        return phase != GamePhase.WAITING;
    }

    // ------------------------------------------------------------------ ações

    /** Inicia a partida (somente a partir de {@link GamePhase#WAITING}). */
    public synchronized void start() {
        requirePhase(GamePhase.WAITING);
        globalModifierId = GlobalModifiers.all().get(random.nextInt(GlobalModifiers.all().size())).id();
        hp[PLAYER_ONE] = rules.startingHp();
        hp[PLAYER_TWO] = rules.startingHp();
        coins[PLAYER_ONE] = rules.startingCoins();
        coins[PLAYER_TWO] = rules.startingCoins();
        lossStreak[PLAYER_ONE] = 0;
        lossStreak[PLAYER_TWO] = 0;
        round = 1;
        firstResolver = firstResolverFor(round);
        winner = 0;
        lastOutcome = null;
        dealRound();
        phase = GamePhase.BUILD;
        currentPlayer = PLAYER_ONE;
        log("GLOBAL|" + globalModifierId, STYLE_NORMAL);
        log("MOEDAS|1=" + coins[PLAYER_ONE] + "|2=" + coins[PLAYER_TWO], STYLE_NORMAL);
        touch();
    }

    /** Envia as duas cartas que um jogador deseja jogar nesta rodada. */
    public synchronized void submitPlay(int player, List<Card> cards) {
        ensurePlayer(player);
        requirePhase(GamePhase.BUILD);
        if (currentPlayer != player) {
            throw new GameRuleException("NOT_YOUR_TURN", "Não é a vez do jogador " + player);
        }
        if (cards == null || cards.size() != rules.maxPlay()) {
            throw new GameRuleException("INVALID_SELECTION",
                    "É preciso escolher exatamente " + rules.maxPlay() + " cartas");
        }
        Set<Card> distinct = new HashSet<>(cards);
        if (distinct.size() != cards.size()) {
            throw new GameRuleException("INVALID_SELECTION", "Cartas duplicadas na seleção");
        }
        if (!hands[player].containsAll(cards)) {
            throw new GameRuleException("CARD_NOT_IN_HAND", "As cartas selecionadas não estão na mão do jogador");
        }

        pendingCards[player] = List.copyOf(cards);
        log("SUBMIT|" + player + "|" + codes(cards), STYLE_NORMAL);

        if (player == PLAYER_ONE) {
            currentPlayer = PLAYER_TWO;
        } else {
            openTable();
            phase = GamePhase.SABOTAGE;
            currentPlayer = 0;
            sabotageChosen[PLAYER_ONE] = false;
            sabotageChosen[PLAYER_TWO] = false;
            chosenSabotage[PLAYER_ONE] = null;
            chosenSabotage[PLAYER_TWO] = null;
            log("MESA|" + codes(community), STYLE_NORMAL);
        }
        touch();
    }

    /** Usa a sabotagem de índice {@code handIndex}; índice -1 equivale a passar. */
    public synchronized void useSabotage(int player, int handIndex) {
        ensurePlayer(player);
        requirePhase(GamePhase.SABOTAGE);
        if (sabotageChosen[player]) {
            throw new GameRuleException("ALREADY_CHOSEN", "Você já escolheu sua sabotagem nesta rodada");
        }
        String id = null;
        if (handIndex >= 0) {
            if (handIndex >= sabotageHands[player].size()) {
                throw new GameRuleException("INVALID_SABOTAGE", "Índice de sabotagem fora do intervalo");
            }
            id = sabotageHands[player].get(handIndex);
        }
        chosenSabotage[player] = id;
        sabotageChosen[player] = true;
        log(id == null ? "PASS|" + player : "CHOOSE_SABOTAGE|" + player + "|" + id, STYLE_DRAFT);
        if (sabotageChosen[PLAYER_ONE] && sabotageChosen[PLAYER_TWO]) {
            resolveRound();
        }
        touch();
    }

    /** Passa a vez de sabotagem sem gastar nenhuma carta. */
    public synchronized void passSabotage(int player) {
        useSabotage(player, -1);
    }

    /** Marca a tela de revelação como confirmada. Quando ambos estão prontos, a partida avança. */
    public synchronized void advance(int player) {
        ensurePlayer(player);
        requirePhase(GamePhase.REVEAL);
        ready[player] = true;
        if (ready[PLAYER_ONE] && ready[PLAYER_TWO]) {
            if (hp[PLAYER_ONE] <= 0 || hp[PLAYER_TWO] <= 0) {
                phase = GamePhase.GAME_OVER;
                currentPlayer = 0;
                winner = hp[PLAYER_ONE] > 0 ? PLAYER_ONE : PLAYER_TWO;
            } else {
                startShop();
            }
        }
        touch();
    }

    /** Escolhe um dos três curingas oferecidos na loja. A escolha não encerra a loja sozinha. */
    public synchronized void pickJoker(int player, int offerIndex) {
        ensurePlayer(player);
        requirePhase(GamePhase.SHOP);
        if (jokerPicked[player]) {
            throw new GameRuleException("ALREADY_PICKED", "Você já escolheu um joker nesta loja");
        }
        List<String> available = offers[player];
        if (offerIndex < 0 || offerIndex >= available.size()) {
            throw new GameRuleException("INVALID_OFFER", "Índice de oferta fora do intervalo");
        }
        String id = available.get(offerIndex);
        jokers[player].add(id);
        jokerPicked[player] = true;
        log("DRAFT|" + player + "|" + id, STYLE_DRAFT);
        touch();
    }

    /**
     * Rerrola as três ofertas de joker do jogador. Usa primeiro os rerolls grátis da partida; depois
     * cobra moedas, com preço crescente a cada atualização na mesma visita à loja.
     */
    public synchronized void reroll(int player) {
        ensurePlayer(player);
        requirePhase(GamePhase.SHOP);
        if (jokerPicked[player]) {
            throw new GameRuleException("ALREADY_PICKED", "Você já escolheu um joker nesta loja");
        }
        if (rerolls[player] > 0) {
            rerolls[player]--;
            offers[player] = generateJokerOffer();
            log("REROLL|" + player + "|restam=" + rerolls[player], STYLE_DRAFT);
            touch();
            return;
        }
        int cost = nextRerollCost(player);
        if (coins[player] < cost) {
            throw new GameRuleException("NO_COINS", "Moedas insuficientes para rerrolar (custo " + cost + ")");
        }
        coins[player] -= cost;
        paidRerolls[player]++;
        offers[player] = generateJokerOffer();
        log("REROLL_PAGO|" + player + "|custo=" + cost + "|moedas=" + coins[player], STYLE_DRAFT);
        touch();
    }

    /** Compra a sabotagem oferecida no índice indicado. */
    public synchronized void buySabotage(int player, int offerIndex) {
        ensurePlayer(player);
        requirePhase(GamePhase.SHOP);
        if (offerIndex < 0 || offerIndex >= sabotageOffers[player].size()) {
            throw new GameRuleException("INVALID_SABOTAGE", "Índice de sabotagem fora do intervalo");
        }
        if (sabotageHands[player].size() >= rules.maxSabotages()) {
            throw new GameRuleException("SABOTAGE_HAND_FULL",
                    "Limite de " + rules.maxSabotages() + " sabotagens em mãos");
        }
        int price = rules.sabotagePrice();
        if (coins[player] < price) {
            throw new GameRuleException("NO_COINS", "Moedas insuficientes (custo " + price + ")");
        }
        String id = sabotageOffers[player].get(offerIndex);
        coins[player] -= price;
        sabotageHands[player].add(id);
        log("BUY|" + player + "|" + id + "|preco=" + price + "|moedas=" + coins[player], STYLE_DRAFT);
        touch();
    }

    /** Confirma que o jogador terminou as compras e já escolheu o joker da rodada. */
    public synchronized void ready(int player) {
        ensurePlayer(player);
        requirePhase(GamePhase.SHOP);
        if (!jokerPicked[player]) {
            throw new GameRuleException("PICK_JOKER_FIRST", "Escolha um joker antes de continuar");
        }
        shopReady[player] = true;
        log("PRONTO|" + player, STYLE_DRAFT);
        if (shopReady[PLAYER_ONE] && shopReady[PLAYER_TWO]) {
            startNextRound();
        }
        touch();
    }

    /** Vota por uma revanche após o fim de jogo. O segundo voto reinicia a partida. */
    public synchronized void requestRematch(int player) {
        ensurePlayer(player);
        requirePhase(GamePhase.GAME_OVER);
        rematch[player] = true;
        if (rematch[PLAYER_ONE] && rematch[PLAYER_TWO]) {
            resetInternalState();
            start();
        }
        touch();
    }

    /** Instantâneo com visibilidade total, usado pelos testes e pela sessão local de um jogador. */
    public synchronized GameSnapshot snapshotFor(int viewer) {
        return buildSnapshot(viewer);
    }

    public synchronized GameSnapshot snapshot() {
        return buildSnapshot(OBSERVER);
    }

    // ------------------------------------------------------------------ rotinas internas

    private void resolveRound() {
        log("ORDEM|" + round + "|" + firstResolver, STYLE_DRAFT);
        ScoreModifiers[] modifiers = {ScoreModifiers.NONE, ScoreModifiers.NONE, ScoreModifiers.NONE};
        int second = firstResolver == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
        applySabotage(firstResolver, modifiers);
        applySabotage(second, modifiers);

        ScoreResult score1 = scoringEngine.score(combine(pendingCards[PLAYER_ONE]),
                jokers[PLAYER_ONE], globalModifierId, modifiers[PLAYER_ONE]);
        ScoreResult score2 = scoringEngine.score(combine(pendingCards[PLAYER_TWO]),
                jokers[PLAYER_TWO], globalModifierId, modifiers[PLAYER_TWO]);

        RoundOutcome outcome = CombatResolver.resolve(round, score1, score2,
                pendingCards[PLAYER_ONE], pendingCards[PLAYER_TWO], community, rules);
        lastOutcome = outcome;
        if (outcome.winner() != 0) {
            hp[outcome.loser()] -= outcome.damage();
        }
        applyEconomy(outcome);

        log("RESULT|vencedor=" + (outcome.winner() == 0 ? "empate" : outcome.winner())
                + "|p1=" + outcome.player1Score().finalScore()
                + "|p2=" + outcome.player2Score().finalScore()
                + "|dano=" + outcome.damage(), STYLE_RESULT);
        log("HP|1=" + Math.max(0, hp[PLAYER_ONE]) + "|2=" + Math.max(0, hp[PLAYER_TWO]), STYLE_RESULT);
        log("COINS|1=" + coins[PLAYER_ONE] + "|2=" + coins[PLAYER_TWO], STYLE_RESULT);
        if (hp[PLAYER_ONE] <= 0 || hp[PLAYER_TWO] <= 0) {
            log("FIM|vencedor=" + (hp[PLAYER_ONE] > 0 ? PLAYER_ONE : PLAYER_TWO), STYLE_GAME_OVER);
        }
        phase = GamePhase.REVEAL;
        currentPlayer = 0;
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
    }

    private void applySabotage(int player, ScoreModifiers[] modifiers) {
        String id = chosenSabotage[player];
        if (id == null) {
            return;
        }
        int opponent = player == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
        sabotageHands[player].remove(id); // uso único: comprou, usou, acabou
        log("SABOTAGE|" + player + "|" + id, STYLE_DRAFT);
        switch (id) {
            case "anti_flush" -> modifiers[opponent] = modifiers[opponent].withBlockFlush();
            case "peso_chumbo" -> modifiers[opponent] = modifiers[opponent].withMultPenalty(5);
            case "imposto_par" -> modifiers[opponent] = modifiers[opponent].withPairTax();
            case "bloqueio_joker" -> {
                if (!jokers[opponent].isEmpty()) {
                    modifiers[opponent] = modifiers[opponent].withDisabledJoker(0);
                    log("JOKER_BLOQUEADO|" + opponent + "|" + jokers[opponent].get(0), STYLE_DRAFT);
                }
            }
            case "nevoa" -> modifiers[opponent] = modifiers[opponent].withNullifyJokers();
            case "virada_naipes" -> flipTableSuits();
            case "troca_river" -> replaceRiver();
            default -> {
                // Sabotagem desconhecida: não faz nada, mas continua registrada no log.
            }
        }
    }

    private void applyEconomy(RoundOutcome outcome) {
        coins[PLAYER_ONE] += rules.roundCoins();
        coins[PLAYER_TWO] += rules.roundCoins();
        if (outcome.winner() == 0) {
            return;
        }
        int loser = outcome.loser();
        int winnerPlayer = outcome.winner();
        coins[winnerPlayer] += rules.winnerBonusCoins();
        lossStreak[winnerPlayer] = 0;
        lossStreak[loser]++;
        if (lossStreak[loser] >= 2) {
            coins[loser] += rules.underdogBonusCoins();
            log("AZARAO|" + loser + "|sequencia=" + lossStreak[loser]
                    + "|bonus=" + rules.underdogBonusCoins(), STYLE_RESULT);
        }
    }

    private void flipTableSuits() {
        List<Card> flipped = new ArrayList<>(community.size());
        for (Card card : community) {
            flipped.add(new Card(card.rank(), mirrorSuit(card.suit())));
        }
        community = List.copyOf(flipped);
        log("MESA_REESCRITA|" + codes(community), STYLE_DRAFT);
    }

    private static Suit mirrorSuit(Suit suit) {
        return switch (suit) {
            case SPADES -> Suit.HEARTS;
            case HEARTS -> Suit.SPADES;
            case DIAMONDS -> Suit.CLUBS;
            case CLUBS -> Suit.DIAMONDS;
        };
    }

    private void replaceRiver() {
        if (community.size() < 5) {
            return;
        }
        Card old = community.get(4);
        Card replacement = drawCard();
        List<Card> updated = new ArrayList<>(community);
        updated.set(4, replacement);
        community = List.copyOf(updated);
        log("RIVER|" + old.code() + "|" + replacement.code(), STYLE_DRAFT);
    }

    private Card drawCard() {
        if (drawPile.isEmpty()) {
            drawPile = Deck.shuffled(random);
        }
        Card card = drawPile.get(0);
        drawPile = drawPile.subList(1, drawPile.size());
        return card;
    }

    private List<Card> combine(List<Card> played) {
        List<Card> pool = new ArrayList<>();
        if (played != null) {
            pool.addAll(played);
        }
        pool.addAll(community);
        return pool;
    }

    private void startShop() {
        List<String> jokerOffer = generateJokerOffer();
        offers[PLAYER_ONE] = new ArrayList<>(jokerOffer);
        offers[PLAYER_TWO] = new ArrayList<>(jokerOffer);
        List<String> sabotageOffer = generateSabotageOffer();
        sabotageOffers[PLAYER_ONE] = new ArrayList<>(sabotageOffer);
        sabotageOffers[PLAYER_TWO] = new ArrayList<>(sabotageOffer);
        shopReady[PLAYER_ONE] = false;
        shopReady[PLAYER_TWO] = false;
        jokerPicked[PLAYER_ONE] = false;
        jokerPicked[PLAYER_TWO] = false;
        paidRerolls[PLAYER_ONE] = 0;
        paidRerolls[PLAYER_TWO] = 0;
        phase = GamePhase.SHOP;
        currentPlayer = 0;
        log("SHOP_OFFER|" + String.join(",", jokerOffer) + "|" + String.join(",", sabotageOffer),
                STYLE_DRAFT);
    }

    private void startNextRound() {
        round++;
        firstResolver = firstResolverFor(round);
        offers[PLAYER_ONE] = new ArrayList<>();
        offers[PLAYER_TWO] = new ArrayList<>();
        sabotageOffers[PLAYER_ONE] = new ArrayList<>();
        sabotageOffers[PLAYER_TWO] = new ArrayList<>();
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
        shopReady[PLAYER_ONE] = false;
        shopReady[PLAYER_TWO] = false;
        jokerPicked[PLAYER_ONE] = false;
        jokerPicked[PLAYER_TWO] = false;
        sabotageChosen[PLAYER_ONE] = false;
        sabotageChosen[PLAYER_TWO] = false;
        chosenSabotage[PLAYER_ONE] = null;
        chosenSabotage[PLAYER_TWO] = null;
        paidRerolls[PLAYER_ONE] = 0;
        paidRerolls[PLAYER_TWO] = 0;
        pendingCards[PLAYER_ONE] = null;
        pendingCards[PLAYER_TWO] = null;
        community = List.of();
        dealRound();
        phase = GamePhase.BUILD;
        currentPlayer = PLAYER_ONE;
        log("ROUND|" + round + "|primeiro=" + firstResolver, STYLE_NORMAL);
    }

    private void dealRound() {
        List<Card> deck = Deck.shuffled(random);
        hands[PLAYER_ONE] = new ArrayList<>(deck.subList(0, rules.handSize()));
        hands[PLAYER_TWO] = new ArrayList<>(
                deck.subList(rules.handSize(), rules.handSize() * 2));
        drawPile = new ArrayList<>(deck.subList(rules.handSize() * 2, deck.size()));
        community = List.of();
    }

    /** Abre as cartas comunitárias do baralho restante (flop, turn e river). */
    private void openTable() {
        int size = Math.min(rules.communitySize(), drawPile.size());
        community = List.copyOf(drawPile.subList(0, size));
        drawPile = new ArrayList<>(drawPile.subList(size, drawPile.size()));
    }

    private int firstResolverFor(int roundNumber) {
        return roundNumber % 2 == 1 ? PLAYER_ONE : PLAYER_TWO;
    }

    private int nextRerollCost(int player) {
        if (rerolls[player] > 0) {
            return 0;
        }
        return rules.paidRerollBaseCost() + rules.paidRerollIncrement() * paidRerolls[player];
    }

    private List<String> generateJokerOffer() {
        List<String> pool = new ArrayList<>(Jokers.ids());
        shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
    }

    private List<String> generateSabotageOffer() {
        List<String> pool = new ArrayList<>(Sabotages.ids());
        shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
    }

    private <T> void shuffle(List<T> pool) {
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
    }

    private GameSnapshot buildSnapshot(int viewer) {
        boolean handVisible = phase == GamePhase.BUILD || phase == GamePhase.SABOTAGE
                || phase == GamePhase.SHOP;
        boolean revealCards = phase == GamePhase.REVEAL || phase == GamePhase.GAME_OVER;
        GlobalModifierDef global = GlobalModifiers.byId(globalModifierId);
        return new GameSnapshot(
                sessionId,
                version,
                phase,
                round,
                currentPlayer,
                rules,
                globalModifierId,
                global == null ? "" : global.name(),
                global == null ? "" : global.description(),
                List.copyOf(community),
                firstResolver,
                buildPlayerView(PLAYER_ONE, viewer, handVisible, revealCards),
                buildPlayerView(PLAYER_TWO, viewer, handVisible, revealCards),
                lastOutcome,
                winner,
                List.copyOf(log)
        );
    }

    private PlayerView buildPlayerView(int player, int viewer, boolean handVisible, boolean revealCards) {
        boolean own = viewer == OBSERVER || viewer == player;
        List<Card> visibleHand = own && handVisible ? List.copyOf(hands[player]) : List.of();
        List<Card> played = List.of();
        if (revealCards && lastOutcome != null) {
            played = player == PLAYER_ONE ? lastOutcome.player1Cards() : lastOutcome.player2Cards();
        }
        List<String> visibleOffers = own && phase == GamePhase.SHOP
                ? List.copyOf(offers[player]) : List.of();
        List<String> visibleSabotageOffers = own && phase == GamePhase.SHOP
                ? List.copyOf(sabotageOffers[player]) : List.of();
        List<String> visibleSabotageHand = own ? List.copyOf(sabotageHands[player]) : List.of();
        return new PlayerView(
                player,
                playerNames[player],
                Math.max(0, hp[player]),
                rules.startingHp(),
                hands[player].size(),
                handVisible && own,
                visibleHand,
                played,
                pendingCards[player] != null,
                List.copyOf(jokers[player]),
                visibleOffers,
                rerolls[player],
                nextRerollCost(player),
                coins[player],
                lossStreak[player],
                visibleSabotageHand,
                sabotageHands[player].size(),
                visibleSabotageOffers,
                sabotageChosen[player],
                jokerPicked[player],
                shopReady[player],
                ready[player],
                rematch[player]
        );
    }

    private void requirePhase(GamePhase expected) {
        if (phase != expected) {
            throw new GameRuleException("WRONG_PHASE",
                    "A ação exige a fase " + expected + " mas a fase atual é " + phase);
        }
    }

    private void ensurePlayer(int player) {
        if (player != PLAYER_ONE && player != PLAYER_TWO) {
            throw new GameRuleException("INVALID_PLAYER", "Id de jogador desconhecido: " + player);
        }
    }

    private void touch() {
        version++;
    }

    private void log(String text, String style) {
        log.add(new LogEntry(text, style));
        while (log.size() > MAX_LOG_ENTRIES) {
            log.remove(0);
        }
    }

    private static String codes(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cards.get(i).code());
        }
        return sb.toString();
    }
}
