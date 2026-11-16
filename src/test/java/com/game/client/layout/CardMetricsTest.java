package com.game.client.layout;

import com.game.client.layout.LayoutMetrics.CardMetrics;
import com.game.client.layout.LayoutMetrics.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de tamanhos de carta: uma única proporção, mão/poderes e as duas fileiras da mesa
 * idênticas entre si, tudo dentro do viewport e a barra de ações sempre acessível.
 */
class CardMetricsTest {

    private static final float[][] SIZES = {
            {1093, 614}, {1365, 695}, {1366, 768}, {1536, 864}, {1920, 1080}
    };

    private static final int HAND_CARDS = 8;
    private static final int POWER_CARDS = LayoutMetrics.POWER_CARDS;
    private static final float MAX_OVERLAP = LayoutMetrics.MAX_POWER_OVERLAP;
    private static final float EPS = 0.5f;

    private static LayoutMetrics metricsAt(float width, float height) {
        return LayoutMetrics.compute(width, height, width - 28f < 1120f, 7f);
    }

    @Test
    void everyCardUsesTheSingleAspectRatio() {
        for (float[] size : SIZES) {
            CardMetrics cards = LayoutMetrics.cardMetrics(size[0], size[1]);
            assertEquals(1.36f, cards.handCardHeight() / cards.handCardWidth(), 1e-4f,
                    "proporção da mão em " + size[0] + "x" + size[1]);
            assertEquals(1.36f, cards.tableCardHeight() / cards.tableCardWidth(), 1e-4f,
                    "proporção da mesa em " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void tableCardsAreLargeEnoughAndNeverSmallerThanSixtyPercentOfTheHand() {
        for (float[] size : SIZES) {
            LayoutMetrics m = metricsAt(size[0], size[1]);
            CardMetrics cards = m.cards;
            assertTrue(cards.tableCardHeight() >= cards.handCardHeight() * 0.60f - 1e-3f,
                    "carta da mesa abaixo de 60% da mão em " + size[0] + "x" + size[1]
                            + ": mesa=" + cards.tableCardHeight()
                            + " mão=" + cards.handCardHeight());
            assertTrue(cards.tableCardHeight() <= cards.handCardHeight() + EPS,
                    "carta da mesa maior que a da mão em " + size[0] + "x" + size[1]);
            assertTrue(cards.tableCardHeight() <= (m.table.height() - 8f) / 2f + EPS,
                    "duas fileiras não cabem na mesa em " + size[0] + "x" + size[1]);
            assertTrue(cards.handCardHeight() >= LayoutMetrics.MIN_CARD_HEIGHT - EPS,
                    "carta da mão abaixo do mínimo em " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void handAndPowerRowsShareTheSameRectangleAndOnlyOverlapInsideTheFan() {
        for (float[] size : SIZES) {
            LayoutMetrics m = metricsAt(size[0], size[1]);
            CardMetrics cards = m.cards;
            assertRowFits(m.hand.width(), HAND_CARDS, cards.handCardWidth(),
                    size[0] + "x" + size[1] + " (mão)");
            assertRowFits(m.hand.width(), POWER_CARDS, cards.handCardWidth(),
                    size[0] + "x" + size[1] + " (poderes)");
        }
    }

    @Test
    void bothTableRowsFitAndUseOneCommonSize() {
        for (float[] size : SIZES) {
            LayoutMetrics m = metricsAt(size[0], size[1]);
            CardMetrics cards = m.cards;
            float cardWidth = cards.tableCardWidth();
            float cardGap = Math.max(4f, cardWidth * 0.22f);
            float powerGap = cardGap * 1.6f;
            int slots = 7;
            float rowWidth = slots * cardWidth + (slots - 1) * cardGap + powerGap;
            assertTrue(rowWidth + 10f + 60f + 12f <= m.table.width() + EPS,
                    "fileira da mesa não cabe em " + size[0] + "x" + size[1]
                            + ": fileira=" + rowWidth + " mesa=" + m.table.width());
        }
    }

    @Test
    void everythingStaysInsideTheViewportAndTheActionButtonIsHitTestable() {
        for (float[] size : SIZES) {
            LayoutMetrics m = metricsAt(size[0], size[1]);
            for (Region region : new Region[]{m.header, m.opponentBar, m.table, m.ownBar,
                    m.hand, m.actionBar, m.rightColumn}) {
                assertTrue(region.inside(size[0], size[1]),
                        "região fora do viewport em " + size[0] + "x" + size[1] + ": " + region);
            }
            // O botão tem 36 px dentro de uma barra de 42 px: continua clicável.
            assertTrue(m.actionBar.height() >= 42f - EPS,
                    "barra de ações curta em " + size[0] + "x" + size[1]);
            assertTrue(m.actionBar.y() >= 14f,
                    "barra de ações colada na borda em " + size[0] + "x" + size[1]);
            // A mão fica dentro da coluna esquerda, sem invadir a barra de ações.
            assertTrue(m.hand.y() >= m.actionBar.top() - EPS,
                    "mão sobre a barra de ações em " + size[0] + "x" + size[1]);
        }
    }

    /** Uma fileira de n cartas com o leque permitido (máx. 38%) sempre cabe na região. */
    private static void assertRowFits(float regionWidth, int count, float cardWidth, String where) {
        float step = count > 1
                ? Math.min(cardWidth, Math.max(cardWidth * (1f - MAX_OVERLAP),
                (regionWidth - cardWidth) / (count - 1)))
                : 0f;
        float total = cardWidth + (count - 1) * step;
        assertTrue(total <= regionWidth + EPS, "leque não cabe: " + where);
        if (count > 1 && step < cardWidth) {
            float overlap = 1f - step / cardWidth;
            assertTrue(overlap <= MAX_OVERLAP + 1e-4f, "sobreposição acima de 38%: " + where);
        }
    }
}
