package com.game.client.layout;

import com.game.client.layout.LayoutMetrics.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutMetricsTest {

    private static final float[][] SIZES = {
            {1093, 614}, {1280, 720}, {1365, 695}, {1366, 768}, {1536, 864}, {1920, 1080}
    };

    @Test
    void everyRegionFitsAndTheActionBarIsFullyInside() {
        for (float[] size : SIZES) {
            float width = size[0];
            float height = size[1];
            boolean stacked = width < 1120f;
            LayoutMetrics m = LayoutMetrics.compute(width, height, stacked, 7f);
            List<Region> regions = List.of(m.header, m.opponentBar, m.table, m.ownBar,
                    m.hand, m.actionBar, m.rightColumn);
            for (Region region : regions) {
                assertTrue(region.inside(width, height),
                        "região fora do viewport em " + width + "x" + height + ": " + region);
            }
            assertTrue(m.actionBar.inside(width, height),
                    "barra de ações cortada em " + width + "x" + height);
            assertTrue(m.actionBar.height() >= 40f,
                    "barra de ações baixa demais em " + width + "x" + height);
        }
    }

    @Test
    void regionsDoNotOverlap() {
        for (float[] size : SIZES) {
            LayoutMetrics m = LayoutMetrics.compute(size[0], size[1], size[0] < 1120f, 7f);
            List<Region> regions = List.of(m.header, m.opponentBar, m.table, m.ownBar,
                    m.hand, m.actionBar, m.rightColumn);
            for (int i = 0; i < regions.size(); i++) {
                for (int j = i + 1; j < regions.size(); j++) {
                    assertFalse(regions.get(i).overlaps(regions.get(j)),
                            "sobreposição em " + size[0] + "x" + size[1] + ": "
                                    + regions.get(i) + " / " + regions.get(j));
                }
            }
        }
    }

    @Test
    void cardsKeepTheMinimumAndSupportedShareAtTheReportedSize() {
        for (float[] size : SIZES) {
            LayoutMetrics m = LayoutMetrics.compute(size[0], size[1], size[0] < 1120f, 7f);
            assertTrue(m.cardHeight >= LayoutMetrics.MIN_CARD_HEIGHT,
                    "carta da mão abaixo do mínimo em " + size[0] + "x" + size[1]);
            assertTrue(m.tableCardHeight >= LayoutMetrics.MIN_CARD_HEIGHT,
                    "carta da mesa abaixo do mínimo em " + size[0] + "x" + size[1]);
            assertTrue(m.powerOverlap <= LayoutMetrics.MAX_POWER_OVERLAP + 1e-4f,
                    "leque de poderes sobrepõe demais em " + size[0] + "x" + size[1]);
            float powerTotal = m.cards.handCardWidth()
                    + (LayoutMetrics.POWER_CARDS - 1) * m.powerStep;
            assertTrue(powerTotal <= m.hand.width() - 12f + 0.5f,
                    "7 poderes não cabem na mão em " + size[0] + "x" + size[1]);
        }
        LayoutMetrics reported = LayoutMetrics.compute(1365, 695, false, 7f);
        assertTrue(reported.cardHeight >= 695 * 0.24f - 0.5f,
                "cartas da mão abaixo de 24% em 1365x695");
        assertTrue(reported.tableCardHeight >= 695 * 0.15f - 0.5f,
                "cartas da mesa abaixo de 15% em 1365x695");
    }

    @Test
    void tallyCellsAreSizedForTheWidestValues() {
        LayoutMetrics m = LayoutMetrics.compute(1365, 695, false, 7f);
        assertTrue(m.chipsCellWidth >= 7 * 4);
        assertTrue(m.multCellWidth >= 7 * 2);
        assertTrue(m.scoreCellWidth >= 7 * 6);
        assertTrue(m.tallyWidth > m.chipsCellWidth + m.multCellWidth + m.scoreCellWidth);
        assertTrue(m.tallyWidth < m.table.width());
    }
}
