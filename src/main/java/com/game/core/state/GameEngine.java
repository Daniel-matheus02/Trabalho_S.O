package com.game.core.state;

import com.game.core.model.Card;
import com.game.core.model.Deck;
import com.game.core.model.PowerCard;
import com.game.core.model.PowerKind;
import com.game.core.model.Suit;
import com.game.core.rules.CombatResolver;
import com.game.core.rules.GameRuleException;
import com.game.core.rules.GameRules;
import com.game.core.rules.PowerEvent;
import com.game.core.rules.RoundOutcome;
import com.game.core.sabotage.Sabotages;
import com.game.core.scoring.GlobalModifierDef;
import com.game.core.scoring.GlobalModifiers;
import com.game.core.scoring.Jokers;
import com.game.core.scoring.ScoreModifiers;
import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Motor de regras autoritativo de uma partida. Não sabe nada sobre renderização ou rede.
 *
 * <p>Fluxo: iniciar &rarr; distribuir 8 cartas &rarr; cada jogador envia exatamente 5 cartas &rarr;
 * cada jogador recebe uma única mão embaralhada de 4 jokers + 3 sabotagens e escolhe até
 * {@link GameRules#maxPowersPlayed()} cartas dela &rarr; pontuação e dano (poderes na ordem
 * alternada) &rarr; revelação &rarr; próxima rodada. Quando nenhum poder está configurado, a
 * rodada resolve assim que as duas mãos são enviadas. Todos os métodos que alteram estado são
 * synchronized, para que possam ser chamados com segurança a partir de threads de rede.</p>
 */
@SuppressWarnings("unchecked")
public final class GameEngine {

    public static final int PLAYER_ONE = 1;
    public static final int PLAYER_TWO = 2;
    public static final int OBSERVER = 0;

    public static final String STYLE_NORMAL = "";
    public static final String STYLE_RESULT = "res";
    public static final String STYLE_GAME_OVER = "fim";
    public static final String STYLE_SYSTEM = "system";

    private static final int MAX_LOG_ENTRIES = 200;

    private final String sessionId;
    private final GameRules rules;
    private final ScoringEngine scoringEngine;
    private final Random random;

    private final String[] playerNames = new String[3];
    private final int[] hp = new int[3];
    private final List<Card>[] hands = new List[3];
    private final List<PowerCard>[] powerHands = new List[3];
    private final List<PowerCard>[] playedPowers = new List[3];
    private final boolean[] powersLocked = new boolean[3];
    private final boolean[] ready = new boolean[3];
    private final boolean[] rematch = new boolean[3];
    private final List<Card>[] pendingCards = new List[3];
    private final ScoreResult[] tableScores = new ScoreResult[3];

    private final List<LogEntry> log = new ArrayList<>();

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
        java.util.Arrays.fill(powerHands, List.of());
        java.util.Arrays.fill(playedPowers, List.of());
        java.util.Arrays.fill(pendingCards, null);
        java.util.Arrays.fill(tableScores, null);
        java.util.Arrays.fill(powersLocked, false);
        java.util.Arrays.fill(ready, false);
        java.util.Arrays.fill(rematch, false);
        hp[PLAYER_ONE] = rules.startingHp();
        hp[PLAYER_TWO] = rules.startingHp();
        hands[PLAYER_ONE] = new ArrayList<>();
        hands[PLAYER_TWO] = new ArrayList<>();
        powerHands[PLAYER_ONE] = new ArrayList<>();
        powerHands[PLAYER_TWO] = new ArrayList<>();
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

    public synchronized List<Card> handOf(int player) {
        ensurePlayer(player);
        return List.copyOf(hands[player]);
    }

    public synchronized List<Card> pendingCardsOf(int player) {
        ensurePlayer(player);
        return pendingCards[player] == null ? List.of() : List.copyOf(pendingCards[player]);
    }

    public synchronized List<PowerCard> powerHandOf(int player) {
        ensurePlayer(player);
        return List.copyOf(powerHands[player]);
    }

    public synchronized List<PowerCard> playedPowersOf(int player) {
        ensurePlayer(player);
        return List.copyOf(playedPowers[player]);
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
        firstResolver = firstResolverFor(round);
        winner = 0;
        lastOutcome = null;
        dealRound();
        phase = GamePhase.BUILD;
        currentPlayer = PLAYER_ONE;
        log("GLOBAL|" + globalModifierId, STYLE_NORMAL);
        touch();
    }

    /** Envia as cinco cartas que um jogador deseja jogar nesta rodada. */
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
        for (Card card : cards) {
            if (card == null) {
                throw new GameRuleException("INVALID_SELECTION", "A seleção contém uma carta nula");
            }
        }
        Set<Card> distinct = new HashSet<>(cards);
        if (distinct.size() != cards.size()) {
            throw new GameRuleException("INVALID_SELECTION", "Cartas duplicadas na seleção");
        }
        if (!hands[player].containsAll(cards)) {
            throw new GameRuleException("CARD_NOT_IN_HAND", "As cartas selecionadas não estão na mão do jogador");
        }

        pendingCards[player] = List.copyOf(cards);
        hands[player].removeAll(cards);
        log("SUBMIT|" + player + "|" + codes(cards), STYLE_NORMAL);

        if (player == PLAYER_ONE) {
            currentPlayer = PLAYER_TWO;
        } else {
            scoreTableHands();
            dealPowers();
            if (rules.jokersPerRound() == 0 && rules.sabotagesPerRound() == 0) {
                resolveRound();
            } else {
                phase = GamePhase.SABOTAGE;
                currentPlayer = 0;
            }
        }
        touch();
    }

    /**
     * Pontuação de mesa: as cinco cartas enviadas com o modificador global da rodada, sem poderes.
     * É o valor mostrado ao lado de cada mão assim que as duas são enviadas.
     */
    private void scoreTableHands() {
        tableScores[PLAYER_ONE] = scoringEngine.score(pendingCards[PLAYER_ONE], List.of(),
                globalModifierId);
        tableScores[PLAYER_TWO] = scoringEngine.score(pendingCards[PLAYER_TWO], List.of(),
                globalModifierId);
    }

    /**
     * Trava os poderes do jogador. A lista vazia significa "não jogar nada"; índices repetidos ou
     * fora do intervalo são rejeitados, assim como mais de {@code maxPowersPlayed} cartas. Quando
     * os dois jogadores travam, a rodada é resolvida.
     */
    public synchronized void lockPowers(int player, List<Integer> powerIndices) {
        ensurePlayer(player);
        requirePhase(GamePhase.SABOTAGE);
        if (powersLocked[player]) {
            throw new GameRuleException("ALREADY_LOCKED", "Você já travou seus poderes nesta rodada");
        }
        List<Integer> indices = powerIndices == null ? List.of() : List.copyOf(powerIndices);
        validatePowerIndices(indices, powerHands[player].size());
        if (indices.size() > rules.maxPowersPlayed()) {
            throw new GameRuleException("TOO_MANY_POWERS",
                    "Você pode jogar no máximo " + rules.maxPowersPlayed() + " poderes por rodada");
        }

        List<Integer> ascending = new ArrayList<>(indices);
        Collections.sort(ascending);
        playedPowers[player] = ascending.stream().map(powerHands[player]::get).toList();
        powersLocked[player] = true;
        log("LOCK|" + player, STYLE_SYSTEM);

        if (powersLocked[PLAYER_ONE] && powersLocked[PLAYER_TWO]) {
            resolveRound();
        }
        touch();
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
                beginNextRound();
            }
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

    /**
     * Apenas para o DevPreview: redistribui a mão de poderes do jogador enquanto ele ainda não
     * travou. Não faz parte do protocolo de rede.
     */
    public synchronized void redealPowersForPreview(int player) {
        ensurePlayer(player);
        if (phase != GamePhase.SABOTAGE || powersLocked[player]) {
            return;
        }
        powerHands[player] = drawPowerHand();
        playedPowers[player] = List.of();
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

    private static void validatePowerIndices(List<Integer> indices, int size) {
        Set<Integer> distinct = new HashSet<>();
        for (Integer index : indices) {
            if (index == null || index < 0 || index >= size || !distinct.add(index)) {
                throw new GameRuleException("INVALID_POWER", "Índice de poder inválido: " + index);
            }
        }
    }

    private void dealPowers() {
        for (int player : new int[]{PLAYER_ONE, PLAYER_TWO}) {
            powerHands[player] = drawPowerHand();
            playedPowers[player] = List.of();
            powersLocked[player] = false;
        }
    }

    /**
     * Monta a mão de poderes como UMA lista: os jokers e as sabotagens sorteados são combinados e
     * embaralhados juntos, nunca agrupados por tipo. O resultado é determinístico para uma semente.
     */
    private List<PowerCard> drawPowerHand() {
        List<PowerCard> hand = new ArrayList<>();
        for (String id : drawDistinct(Jokers.ids(), rules.jokersPerRound())) {
            hand.add(new PowerCard(PowerKind.JOKER, id));
        }
        for (String id : drawDistinct(Sabotages.ids(), rules.sabotagesPerRound())) {
            hand.add(new PowerCard(PowerKind.SABOTAGE, id));
        }
        boolean bothKinds = hand.stream().anyMatch(card -> card.kind() == PowerKind.JOKER)
                && hand.stream().anyMatch(card -> card.kind() == PowerKind.SABOTAGE);
        if (bothKinds) {
            for (int attempt = 0; attempt < 64; attempt++) {
                shuffle(hand);
                if (!groupedByKind(hand)) {
                    break;
                }
            }
        } else {
            shuffle(hand);
        }
        return new ArrayList<>(hand);
    }

    /** Verdadeiro quando a lista tem no máximo uma troca de tipo (isto é, está agrupada). */
    private static boolean groupedByKind(List<PowerCard> cards) {
        int switches = 0;
        for (int i = 1; i < cards.size(); i++) {
            if (cards.get(i).kind() != cards.get(i - 1).kind()) {
                switches++;
            }
        }
        return switches <= 1;
    }

    private <T> List<T> drawDistinct(List<T> pool, int count) {
        List<T> copy = new ArrayList<>(pool);
        shuffle(copy);
        return new ArrayList<>(copy.subList(0, Math.min(count, copy.size())));
    }

    private void resolveRound() {
        log("ORDEM|" + round + "|" + firstResolver, STYLE_SYSTEM);
        ScoreModifiers[] modifiers = {ScoreModifiers.NONE, ScoreModifiers.NONE, ScoreModifiers.NONE};
        List<PowerEvent> events = new ArrayList<>();
        int second = firstResolver == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
        applyPowers(firstResolver, modifiers, events);
        applyPowers(second, modifiers, events);

        ScoreResult score1 = scoringEngine.score(pendingCards[PLAYER_ONE],
                jokerIds(PLAYER_ONE), globalModifierId, modifiers[PLAYER_ONE],
                sabotageIdsAgainst(PLAYER_ONE));
        ScoreResult score2 = scoringEngine.score(pendingCards[PLAYER_TWO],
                jokerIds(PLAYER_TWO), globalModifierId, modifiers[PLAYER_TWO],
                sabotageIdsAgainst(PLAYER_TWO));

        RoundOutcome outcome = CombatResolver.resolve(round, score1, score2,
                pendingCards[PLAYER_ONE], pendingCards[PLAYER_TWO],
                playedPowers[PLAYER_ONE], playedPowers[PLAYER_TWO], events, rules);
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

        // Poderes não usados são descartados ao fim da rodada; os jogados ficam para a revelação.
        powerHands[PLAYER_ONE] = new ArrayList<>();
        powerHands[PLAYER_TWO] = new ArrayList<>();

        phase = GamePhase.REVEAL;
        currentPlayer = 0;
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
    }

    private List<String> jokerIds(int player) {
        List<String> ids = new ArrayList<>();
        for (PowerCard card : playedPowers[player]) {
            if (card.kind() == PowerKind.JOKER) {
                ids.add(card.id());
            }
        }
        return ids;
    }

    /** Sabotagens jogadas pelo oponente, na ordem da mão, que afetam este jogador. */
    private List<String> sabotageIdsAgainst(int player) {
        int opponent = player == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
        List<String> ids = new ArrayList<>();
        for (PowerCard card : playedPowers[opponent]) {
            if (card.kind() == PowerKind.SABOTAGE) {
                ids.add(card.id());
            }
        }
        return ids;
    }

    private void applyPowers(int player, ScoreModifiers[] modifiers, List<PowerEvent> events) {
        int opponent = player == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
        for (PowerCard card : playedPowers[player]) {
            if (card.kind() == PowerKind.SABOTAGE) {
                events.add(applySabotage(player, opponent, card.id(), modifiers));
            } else {
                events.add(new PowerEvent(player, PowerKind.JOKER, card.id(), player, null, null));
            }
        }
        List<String> jokers = jokerIds(player);
        if (!jokers.isEmpty()) {
            log("JOKERS|" + player + "|" + String.join(",", jokers), STYLE_SYSTEM);
        }
    }

    private PowerEvent applySabotage(int player, int opponent, String id,
                                     ScoreModifiers[] modifiers) {
        log("SABOTAGE|" + player + "|" + id, STYLE_SYSTEM);
        List<Card> before = pendingCards[opponent] == null
                ? null : List.copyOf(pendingCards[opponent]);
        switch (id) {
            case "anti_flush" -> modifiers[opponent] = modifiers[opponent].withBlockFlush();
            case "peso_chumbo" -> modifiers[opponent] = modifiers[opponent].withMultPenalty(5);
            case "imposto_par" -> modifiers[opponent] = modifiers[opponent].withPairTax();
            case "nevoa" -> modifiers[opponent] = modifiers[opponent].withNullifyJokers();
            case "virada_naipes" -> flipPlayedSuits(opponent);
            case "troca_carta" -> swapPlayedCard(opponent);
            default -> {
                // Sabotagem desconhecida: não faz nada, mas continua registrada no log.
            }
        }
        List<Card> after = pendingCards[opponent] == null
                ? null : List.copyOf(pendingCards[opponent]);
        boolean changed = before != null && !before.equals(after);
        return new PowerEvent(player, PowerKind.SABOTAGE, id, opponent,
                changed ? before : null, changed ? after : null);
    }

    private void flipPlayedSuits(int opponent) {
        List<Card> played = pendingCards[opponent];
        if (played == null || played.isEmpty()) {
            return;
        }
        List<Card> flipped = new ArrayList<>(played.size());
        for (Card card : played) {
            flipped.add(new Card(card.rank(), mirrorSuit(card.suit())));
        }
        pendingCards[opponent] = List.copyOf(flipped);
        log("NIAPES|" + opponent + "|" + codes(played) + "->" + codes(flipped), STYLE_SYSTEM);
    }

    private static Suit mirrorSuit(Suit suit) {
        return switch (suit) {
            case SPADES -> Suit.HEARTS;
            case HEARTS -> Suit.SPADES;
            case DIAMONDS -> Suit.CLUBS;
            case CLUBS -> Suit.DIAMONDS;
        };
    }

    private void swapPlayedCard(int opponent) {
        List<Card> played = pendingCards[opponent];
        if (played == null || played.isEmpty()) {
            return;
        }
        int index = random.nextInt(played.size());
        Card old = played.get(index);
        Card replacement = drawCard();
        List<Card> updated = new ArrayList<>(played);
        updated.set(index, replacement);
        pendingCards[opponent] = List.copyOf(updated);
        log("TROCA_CARTA|" + opponent + "|" + old.code() + "->" + replacement.code(), STYLE_SYSTEM);
    }

    private Card drawCard() {
        if (drawPile.isEmpty()) {
            drawPile = Deck.shuffled(random);
        }
        Card card = drawPile.get(0);
        drawPile = drawPile.subList(1, drawPile.size());
        return card;
    }

    private void beginNextRound() {
        round++;
        firstResolver = firstResolverFor(round);
        ready[PLAYER_ONE] = false;
        ready[PLAYER_TWO] = false;
        powersLocked[PLAYER_ONE] = false;
        powersLocked[PLAYER_TWO] = false;
        playedPowers[PLAYER_ONE] = List.of();
        playedPowers[PLAYER_TWO] = List.of();
        pendingCards[PLAYER_ONE] = null;
        pendingCards[PLAYER_TWO] = null;
        tableScores[PLAYER_ONE] = null;
        tableScores[PLAYER_TWO] = null;
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
    }

    private int firstResolverFor(int roundNumber) {
        return roundNumber % 2 == 1 ? PLAYER_ONE : PLAYER_TWO;
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
        boolean handVisible = phase == GamePhase.BUILD || phase == GamePhase.SABOTAGE;
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
                firstResolver,
                buildPlayerView(PLAYER_ONE, viewer, handVisible),
                buildPlayerView(PLAYER_TWO, viewer, handVisible),
                lastOutcome,
                winner,
                List.copyOf(log)
        );
    }

    private PlayerView buildPlayerView(int player, int viewer, boolean handVisible) {
        boolean own = viewer == OBSERVER || viewer == player;
        boolean bothSubmitted = phase == GamePhase.SABOTAGE || phase == GamePhase.REVEAL;
        List<Card> visibleHand = own && handVisible ? List.copyOf(hands[player]) : List.of();
        boolean playedVisible = pendingCards[player] != null && (own || bothSubmitted);
        List<Card> visiblePlayed = playedVisible ? List.copyOf(pendingCards[player]) : List.of();
        boolean powersVisible = own && phase == GamePhase.SABOTAGE;
        List<PowerCard> visiblePowerHand = powersVisible
                ? List.copyOf(powerHands[player]) : List.of();
        boolean playedPowersVisible = phase == GamePhase.REVEAL || phase == GamePhase.GAME_OVER;
        List<PowerCard> visiblePlayedPowers = playedPowersVisible
                ? List.copyOf(playedPowers[player]) : List.of();
        ScoreResult visibleTableScore = bothSubmitted ? tableScores[player] : null;
        return new PlayerView(
                player,
                playerNames[player],
                Math.max(0, hp[player]),
                rules.startingHp(),
                hands[player].size(),
                visibleHand,
                pendingCards[player] != null,
                visiblePlayed,
                visibleTableScore,
                visiblePowerHand,
                powerHands[player].size(),
                visiblePlayedPowers,
                powersLocked[player],
                ready[player]
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
