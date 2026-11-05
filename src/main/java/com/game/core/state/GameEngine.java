package com.game.core.state;

import com.game.core.model.Card;
import com.game.core.model.Deck;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
import com.game.core.rules.RoundOutcome;
import com.game.core.scoring.GlobalModifierDef;
import com.game.core.scoring.GlobalModifiers;
import com.game.core.scoring.Jokers;
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
 * <p>Fluxo (idêntico ao protótipo de referência): iniciar &rarr; distribuir &rarr; ambos os jogadores montam
 * uma jogada &rarr; revelar e causar dano &rarr; cada um escolhe um curinga &rarr; próxima rodada. Todos os métodos
 * que alteram estado são synchronized, para que possam ser chamados com segurança a partir de threads de rede.</p>
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
    private final List<Card>[] hands = new List[3];
    private final List<String>[] jokers = new List[3];
    private final List<String>[] offers = new List[3];
    private final int[] rerolls = new int[3];
    private final boolean[] ready = new boolean[3];
    private final boolean[] rematch = new boolean[3];
    private final ScoreResult[] pendingScores = new ScoreResult[3];
    private final List<Card>[] pendingCards = new List[3];

    private final List<LogEntry> log = new ArrayList<>();

    private GamePhase phase = GamePhase.WAITING;
    private int round = 1;
    private int currentPlayer = 0;
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
        java.util.Arrays.fill(pendingCards, null);
        java.util.Arrays.fill(pendingScores, null);
        java.util.Arrays.fill(ready, false);
        java.util.Arrays.fill(rematch, false);
        hp[PLAYER_ONE] = rules.startingHp();
        hp[PLAYER_TWO] = rules.startingHp();
        rerolls[PLAYER_ONE] = rules.rerollsPerMatch();
        rerolls[PLAYER_TWO] = rules.rerollsPerMatch();
        hands[PLAYER_ONE] = new ArrayList<>();
        hands[PLAYER_TWO] = new ArrayList<>();
        jokers[PLAYER_ONE] = new ArrayList<>();
        jokers[PLAYER_TWO] = new ArrayList<>();
        offers[PLAYER_ONE] = new ArrayList<>();
        offers[PLAYER_TWO] = new ArrayList<>();
        phase = GamePhase.WAITING;
        round = 1;
        currentPlayer = 0;
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

    public synchronized List<Card> handOf(int player) {
        ensurePlayer(player);
        return List.copyOf(hands[player]);
    }

    public synchronized List<String> jokersOf(int player) {
        ensurePlayer(player);
        return List.copyOf(jokers[player]);
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
        round = 1;
        winner = 0;
        lastOutcome = null;
        dealRound();
        phase = GamePhase.BUILD;
        currentPlayer = PLAYER_ONE;
        log("GLOBAL|" + globalModifierId, STYLE_NORMAL);
        touch();
    }

    /** Envia as cartas que um jogador deseja jogar nesta rodada. */
    public synchronized void submitPlay(int player, List<Card> cards) {
        ensurePlayer(player);
        requirePhase(GamePhase.BUILD);
        if (currentPlayer != player) {
            throw new GameRuleException("NOT_YOUR_TURN", "Não é a vez do jogador " + player);
        }
        if (cards == null || cards.isEmpty()) {
            throw new GameRuleException("INVALID_SELECTION", "Selecione pelo menos uma carta");
        }
        if (cards.size() > rules.maxPlay()) {
            throw new GameRuleException("INVALID_SELECTION",
                    "No máximo " + rules.maxPlay() + " cartas podem ser jogadas");
        }
        Set<Card> distinct = new HashSet<>(cards);
        if (distinct.size() != cards.size()) {
            throw new GameRuleException("INVALID_SELECTION", "Cartas duplicadas na seleção");
        }
        if (!hands[player].containsAll(cards)) {
            throw new GameRuleException("CARD_NOT_IN_HAND", "As cartas selecionadas não estão na mão do jogador");
        }

        pendingCards[player] = List.copyOf(cards);
        pendingScores[player] = scoringEngine.score(cards, jokers[player], globalModifierId);
        log("SUBMIT|" + player + "|" + codes(cards), STYLE_NORMAL);

        if (player == PLAYER_ONE) {
            currentPlayer = PLAYER_TWO;
        } else {
            resolveRound();
        }
        touch();
    }

    /** Marca a tela de revelação como confirmada. Quando ambos os jogadores estão prontos, a partida avança. */
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
                startDraft();
            }
        }
        touch();
    }

    /** Escolhe um dos três curingas oferecidos. */
    public synchronized void pickJoker(int player, int offerIndex) {
        ensurePlayer(player);
        requirePhase(GamePhase.DRAFT);
        if (currentPlayer != player) {
            throw new GameRuleException("NOT_YOUR_TURN", "Não é a vez do jogador " + player);
        }
        List<String> available = offers[player];
        if (offerIndex < 0 || offerIndex >= available.size()) {
            throw new GameRuleException("INVALID_OFFER", "Índice de oferta fora do intervalo");
        }
        String id = available.get(offerIndex);
        jokers[player].add(id);
        ready[player] = true;
        log("DRAFT|" + player + "|" + id, STYLE_DRAFT);

        if (player == PLAYER_ONE) {
            currentPlayer = PLAYER_TWO;
        } else {
            startNextRound();
        }
        touch();
    }

    /** Rerrola as três ofertas do jogador atual. Cada jogador tem um número limitado por partida. */
    public synchronized void reroll(int player) {
        ensurePlayer(player);
        requirePhase(GamePhase.DRAFT);
        if (currentPlayer != player) {
            throw new GameRuleException("NOT_YOUR_TURN", "Não é a vez do jogador " + player);
        }
        if (rerolls[player] <= 0) {
            throw new GameRuleException("NO_REROLLS", "Não restam rerolagens");
        }
        offers[player] = generateOffer();
        rerolls[player]--;
        log("REROLL|" + player + "|restam=" + rerolls[player], STYLE_DRAFT);
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
        RoundOutcome outcome = CombatResolver.resolve(round,
                pendingScores[PLAYER_ONE], pendingScores[PLAYER_TWO],
                pendingCards[PLAYER_ONE], pendingCards[PLAYER_TWO], rules);
        lastOutcome = outcome;
        if (outcome.winner() != 0) {
            hp[outcome.loser()] -= outcome.damage();
        }
        log("RESULT|vencedor=" + (outcome.winner() == 0 ? "empate" : outcome.winner())
                + "|p1=" + outcome.player1Score().finalScore()
                + "|p2=" + outcome.player2Score().finalScore()
                + "|dano=" + outcome.damage(), STYLE_RESULT);
        log("HP|1=" + Math.max(0, hp[PLAYER_ONE]) + "|2=" + Math.max(0, hp[PLAYER_TWO]), STYLE_RESULT);
        if (hp[PLAYER_ONE] <= 0 || hp[PLAYER_TWO] <= 0) {
            log("FIM|vencedor=" + (hp[PLAYER_ONE] > 0 ? PLAYER_ONE : PLAYER_TWO), STYLE_GAME_OVER);
        }
        phase = GamePhase.REVEAL;
        currentPlayer = 0;
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
    }

    private void startDraft() {
        List<String> offer = generateOffer();
        offers[PLAYER_ONE] = new ArrayList<>(offer);
        offers[PLAYER_TWO] = new ArrayList<>(offer);
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
        pendingCards[PLAYER_ONE] = null;
        pendingCards[PLAYER_TWO] = null;
        pendingScores[PLAYER_ONE] = null;
        pendingScores[PLAYER_TWO] = null;
        phase = GamePhase.DRAFT;
        currentPlayer = PLAYER_ONE;
        log("DRAFT_OFFER|" + String.join(",", offer), STYLE_DRAFT);
    }

    private void startNextRound() {
        round++;
        offers[PLAYER_ONE] = new ArrayList<>();
        offers[PLAYER_TWO] = new ArrayList<>();
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
        pendingCards[PLAYER_ONE] = null;
        pendingCards[PLAYER_TWO] = null;
        pendingScores[PLAYER_ONE] = null;
        pendingScores[PLAYER_TWO] = null;
        dealRound();
        phase = GamePhase.BUILD;
        currentPlayer = PLAYER_ONE;
        log("ROUND|" + round, STYLE_NORMAL);
    }

    private void dealRound() {
        List<Card> deck = Deck.shuffled(random);
        hands[PLAYER_ONE] = new ArrayList<>(deck.subList(0, rules.handSize()));
        hands[PLAYER_TWO] = new ArrayList<>(deck.subList(rules.handSize(), rules.handSize() * 2));
    }

    private List<String> generateOffer() {
        List<String> pool = new ArrayList<>(Jokers.ids());
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return new ArrayList<>(pool.subList(0, 3));
    }

    private GameSnapshot buildSnapshot(int viewer) {
        boolean handVisible = phase == GamePhase.BUILD || phase == GamePhase.DRAFT;
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
        List<String> visibleOffers = own && phase == GamePhase.DRAFT
                ? List.copyOf(offers[player]) : List.of();
        return new PlayerView(
                player,
                playerNames[player],
                Math.max(0, hp[player]),
                rules.startingHp(),
                hands[player].size(),
                handVisible && own,
                visibleHand,
                played,
                pendingScores[player] != null,
                List.copyOf(jokers[player]),
                visibleOffers,
                rerolls[player],
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
