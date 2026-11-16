package com.game.client.animation;

import com.game.core.scoring.ScoreResult;
import com.game.core.scoring.ScoringEngine;
import com.game.core.scoring.StepKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreCountTimelineTest {

    private final ScoringEngine scoring = new ScoringEngine();

    @Test
    void beatCountOrderingAndFinalValueMatchTheScore() {
        ScoreResult result = scoring.score(
                Arrays.stream(new String[]{"AE", "AC", "KO", "7E", "2C"})
                        .map(com.game.core.model.Card::fromCode).toList(),
                List.of("par_mult", "naipe_afiado"), null);

        List<ScoreCountTimeline.Beat> beats = ScoreCountTimeline.build(result);
        assertEquals(result.steps().size() + 1, beats.size(),
                "um beat por passo + o fechamento final");

        for (int i = 0; i < result.steps().size(); i++) {
            assertEquals(result.steps().get(i), beats.get(i).step());
        }
        assertTrue(beats.get(0).timeOffset() == 0f);
        for (int i = 1; i < beats.size() - 1; i++) {
            assertTrue(beats.get(i).timeOffset() > beats.get(i - 1).timeOffset());
        }

        ScoreCountTimeline.Beat last = beats.get(beats.size() - 1);
        assertEquals(result.chips(), last.step().chipsAfter());
        assertEquals(result.mult(), last.step().multAfter());
        assertEquals(Integer.toString(result.finalScore()), last.popupText());
        assertEquals("neutral", last.colorKey());
        assertTrue(last.timeOffset() > beats.get(beats.size() - 2).timeOffset());
    }

    @Test
    void cardBeatsCarryTheCardIndexAndStepKindsKeepTheirOrder() {
        ScoreResult result = scoring.score(
                Arrays.stream(new String[]{"2E", "5E", "9E", "JE", "KE"})
                        .map(com.game.core.model.Card::fromCode).toList(),
                List.of("rio", "par_mult"), null);

        List<ScoreCountTimeline.Beat> beats = ScoreCountTimeline.build(result);
        assertEquals(StepKind.HAND_BASE, beats.get(0).step().kind());
        for (int i = 1; i <= 5; i++) {
            assertEquals(StepKind.CARD, beats.get(i).step().kind());
            assertEquals(i - 1, beats.get(i).step().cardIndex());
            assertEquals("chips", beats.get(i).colorKey());
        }
        assertEquals(StepKind.JOKER, beats.get(6).step().kind());
        assertEquals(StepKind.JOKER, beats.get(7).step().kind());
        assertFalse(beats.get(beats.size() - 1).popupText().isBlank());
    }

    @Test
    void totalDurationIncludesTheFinalCount() {
        ScoreResult result = scoring.score(
                Arrays.stream(new String[]{"AE", "AC"})
                        .map(com.game.core.model.Card::fromCode).toList(),
                List.of(), null);
        float lastOffset = ScoreCountTimeline.build(result)
                .get(ScoreCountTimeline.build(result).size() - 1).timeOffset();
        assertEquals(lastOffset + ScoreFeel.FINAL_COUNT,
                ScoreCountTimeline.totalDuration(result), 0.0001f);
    }
}
