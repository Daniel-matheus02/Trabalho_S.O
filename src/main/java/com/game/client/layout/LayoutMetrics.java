package com.game.client.layout;

/**
 * Cálculo puro do layout da tela de jogo. Não importa libGDX: recebe o tamanho do palco e
 * devolve retângulos e métricas de carta. A barra de ações é reservada primeiro; a mesa recebe
 * o que sobra, e nenhuma região pode sair do viewport.
 */
public final class LayoutMetrics {

    public static final float CARD_ASPECT = 1.36f;
    public static final float MIN_CARD_HEIGHT = 96f;
    public static final float MAX_POWER_OVERLAP = 0.38f;
    public static final int POWER_CARDS = 7;
    /** Tabela de mesa: 5 cartas + 2 poderes, com o espaçamento e o placar reservado. */
    private static final float TABLE_ROW_FACTOR = 8.672f;
    private static final float TABLE_SIDE_RESERVE = 82f;

    /**
     * O ÚNICO conjunto de tamanhos de carta do jogo. A mão (cartas, curingas, sabotagens e
     * versos) usa exatamente o mesmo retângulo; as duas fileiras da mesa usam outro, também
     * idêntico entre si. Todas mantêm a mesma proporção {@link #CARD_ASPECT}.
     */
    public record CardMetrics(float handCardWidth, float handCardHeight,
                              float tableCardWidth, float tableCardHeight) {

        public CardMetrics {
            if (handCardWidth <= 0f || handCardHeight <= 0f
                    || tableCardWidth <= 0f || tableCardHeight <= 0f) {
                throw new IllegalArgumentException("métricas de carta precisam ser positivas");
            }
        }

        /** Uma única proporção para todas as cartas: altura = largura × 1,36. */
        public static float heightFor(float width) {
            return width * CARD_ASPECT;
        }
    }

    private static final float PAD = 14f;
    private static final float STATUS_HEIGHT = 22f;
    private static final float STATUS_PAD = 4f;
    private static final float GAP = 4f;
    private static final float HEADER_PAD = 8f;
    private static final float SIDEBAR_WIDTH = 320f;
    /** Mesmo limite usado pelo GameScreen para empilhar a barra lateral. */
    private static final float STACK_W = 1120f;

    public record Region(float x, float y, float width, float height) {

        public float right() {
            return x + width;
        }

        public float top() {
            return y + height;
        }

        public boolean inside(float viewWidth, float viewHeight) {
            return x >= -0.5f && y >= -0.5f
                    && right() <= viewWidth + 0.5f && top() <= viewHeight + 0.5f;
        }

        public boolean overlaps(Region other) {
            float overlapX = Math.min(right(), other.right()) - Math.max(x, other.x);
            float overlapY = Math.min(top(), other.top()) - Math.max(y, other.y);
            return overlapX > 0.5f && overlapY > 0.5f;
        }
    }

    public final float width;
    public final float height;
    public final boolean stacked;
    public final Region header;
    public final Region opponentBar;
    public final Region table;
    public final Region ownBar;
    public final Region hand;
    public final Region actionBar;
    public final Region rightColumn;
    public final float cardWidth;
    public final float cardHeight;
    public final float tableCardWidth;
    public final float tableCardHeight;
    /** Fonte única de tamanhos de carta consumida por todo o rendering. */
    public final CardMetrics cards;
    public final float handCardMinHeight;
    public final float powerStep;
    public final float powerOverlap;
    public final float chipsCellWidth;
    public final float multCellWidth;
    public final float scoreCellWidth;
    public final float tallyWidth;

    private LayoutMetrics(float width, float height, boolean stacked, float glyphWidth) {
        this.width = width;
        this.height = height;
        this.stacked = stacked;
        float innerWidth = Math.max(0f, width - PAD * 2f);
        float rightWidth = stacked ? 0f : Math.min(SIDEBAR_WIDTH, innerWidth * 0.32f);
        float leftWidth = Math.max(0f, innerWidth - rightWidth - (stacked ? 0f : 14f));

        float headerHeight = stacked ? 102f : 56f;
        float actionHeight = 42f;
        float ownHeight = stacked ? 36f : 40f;
        float opponentHeight = stacked ? 40f : 44f;
        // Orçamento vertical EXATO do GameScreen: recheio da raiz (2 x PAD), cabeçalho + seu
        // respiro, linha de status + seu respiro e as cinco linhas da coluna esquerda com os
        // quatro espaçamentos entre elas. É a conta que garante que a barra de ações nunca seja
        // cortada pela borda inferior.
        float chrome = PAD * 2f + headerHeight + HEADER_PAD + STATUS_HEIGHT + STATUS_PAD;
        float rowsGaps = GAP * 4f;
        float available = height - chrome;
        float free = Math.max(0f, available - (actionHeight + ownHeight + opponentHeight + rowsGaps));

        float tableMin = MIN_CARD_HEIGHT * 2f + 8f;
        float handPreferred = Math.max(0.24f * height + 18f, MIN_CARD_HEIGHT + 18f);
        float handHeight = Math.max(MIN_CARD_HEIGHT + 18f,
                Math.min(handPreferred, free - tableMin));
        if (handHeight > free) {
            handHeight = Math.max(0f, free);
        }
        float tableHeight = Math.max(0f, free - handHeight);

        this.handCardMinHeight = Math.max(MIN_CARD_HEIGHT, handHeight - 18f);
        float actionY = PAD + STATUS_HEIGHT + STATUS_PAD;
        float handY = actionY + actionHeight + GAP;
        float ownY = handY + handHeight + GAP;
        float tableY = ownY + ownHeight + GAP;
        float opponentY = tableY + tableHeight + GAP;
        float headerY = opponentY + opponentHeight + GAP;

        this.header = new Region(PAD, headerY, innerWidth, headerHeight);
        this.opponentBar = new Region(PAD, opponentY, leftWidth, opponentHeight);
        this.table = new Region(PAD, tableY, leftWidth, tableHeight);
        this.ownBar = new Region(PAD, ownY, leftWidth, ownHeight);
        this.hand = new Region(PAD, handY, leftWidth, handHeight);
        this.actionBar = new Region(PAD, actionY, leftWidth, actionHeight);
        this.rightColumn = new Region(PAD + leftWidth + 14f, actionY,
                rightWidth, Math.max(0f, headerY - actionY));

        this.cardHeight = Math.max(MIN_CARD_HEIGHT, handHeight - 18f);
        this.cardWidth = cardHeight / CARD_ASPECT;
        // A mesa precisa caber em DUAS fileiras e, horizontalmente, na largura da coluna,
        // descontando o placar. O tamanho é o maior que satisfaz as duas restrições, mas nunca
        // abaixo de 60% da carta da mão.
        float verticalTable = Math.max(0f, (tableHeight - 8f) / 2f);
        float horizontalTable = Math.max(0f,
                (leftWidth - TABLE_SIDE_RESERVE) / TABLE_ROW_FACTOR) * CARD_ASPECT;
        float fittedTable = Math.min(cardHeight, Math.min(verticalTable, horizontalTable));
        this.tableCardHeight = Math.max(Math.min(fittedTable, cardHeight),
                Math.min(cardHeight, MIN_CARD_HEIGHT));
        this.tableCardWidth = tableCardHeight / CARD_ASPECT;
        this.cards = new CardMetrics(cardWidth, cardHeight, tableCardWidth, tableCardHeight);

        float handWidth = Math.max(1f, leftWidth - 12f);
        float step = POWER_CARDS <= 1 ? cardWidth
                : Math.min(cardWidth, (handWidth - cardWidth) / (POWER_CARDS - 1));
        step = Math.max(cardWidth * (1f - MAX_POWER_OVERLAP), step);
        this.powerStep = Math.max(1f, step);
        this.powerOverlap = Math.max(0f, 1f - powerStep / Math.max(1f, cardWidth));

        this.chipsCellWidth = glyphWidth * 4f + 20f;
        this.multCellWidth = glyphWidth * 2f + 20f;
        this.scoreCellWidth = glyphWidth * 6f + 18f;
        this.tallyWidth = chipsCellWidth + multCellWidth + scoreCellWidth
                + glyphWidth * 2f + 26f;
    }

    public static LayoutMetrics compute(float width, float height, boolean stacked,
                                         float glyphWidth) {
        return new LayoutMetrics(Math.max(1f, width), Math.max(1f, height), stacked,
                Math.max(1f, glyphWidth));
    }

    /** Métricas de carta para o tamanho do palco, sem precisar de nenhuma outra informação. */
    public static CardMetrics cardMetrics(float width, float height) {
        boolean stacked = Math.max(0f, width - PAD * 2f) < STACK_W;
        return compute(width, height, stacked, 7f).cards;
    }

    /** Largura mínima suportada pelo layout. */
    public static final float MIN_WIDTH = 1093f;
    public static final float MIN_HEIGHT = 614f;
}
