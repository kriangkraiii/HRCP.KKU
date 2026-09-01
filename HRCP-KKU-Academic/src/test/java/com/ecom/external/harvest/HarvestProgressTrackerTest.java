package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.model.HarvestResult;

class HarvestProgressTrackerTest {

    @Test
    @DisplayName("Tracker calculates accurate % based on steps and handles complete lifecycle")
    void testProgressTrackerLifecycle() {
        HarvestProgressTracker tracker = new HarvestProgressTracker();

        // 1. Initial State
        HarvestProgressTracker.ProgressSnapshot snap0 = tracker.snapshot();
        assertThat(snap0.running()).isFalse();
        assertThat(snap0.completed()).isFalse();
        assertThat(snap0.percent()).isZero();

        // 2. Start with 10 total steps across 3 sources
        tracker.start(10, List.of("OPENALEX", "CROSSREF", "DBLP"));
        HarvestProgressTracker.ProgressSnapshot snapStart = tracker.snapshot();
        assertThat(snapStart.running()).isTrue();
        assertThat(snapStart.completed()).isFalse();
        assertThat(snapStart.percent()).isZero();
        assertThat(snapStart.sources()).hasSize(3);

        // 3. Step advancement
        tracker.advance("OPENALEX", "กำลังดึง OpenAlex");
        tracker.advance("OPENALEX", "ดึง OpenAlex เรียบร้อย", 2); // completedSteps = 3
        HarvestProgressTracker.ProgressSnapshot snapMid = tracker.snapshot();
        assertThat(snapMid.percent()).isEqualTo(30);
        assertThat(snapMid.completedSteps()).isEqualTo(3);
        assertThat(snapMid.totalSteps()).isEqualTo(10);

        // 4. Update source statuses
        tracker.updateSource("OPENALEX", "SUCCESS", 100, 50, "พบ 50 รายการ");
        tracker.updateSource("DBLP", "FAILED", 100, 0, "503 Service Unavailable");

        HarvestProgressTracker.ProgressSnapshot snapSources = tracker.snapshot();
        HarvestProgressTracker.SourceProgress openalex = snapSources.sources().stream()
                .filter(s -> "OPENALEX".equals(s.sourceName()))
                .findFirst().orElseThrow();
        assertThat(openalex.status()).isEqualTo("SUCCESS");
        assertThat(openalex.itemsFound()).isEqualTo(50);

        // 5. Finish
        HarvestResult r1 = HarvestResult.ok("OPENALEX", List.of(), null, 1, 100L, "ok");
        tracker.finish(List.of(r1), 50);

        HarvestProgressTracker.ProgressSnapshot snapDone = tracker.snapshot();
        assertThat(snapDone.running()).isFalse();
        assertThat(snapDone.completed()).isTrue();
        assertThat(snapDone.percent()).isEqualTo(100);
        assertThat(snapDone.completionSummary()).contains("สำเร็จ");

        // 6. Reset
        tracker.reset();
        assertThat(tracker.snapshot().running()).isFalse();
        assertThat(tracker.snapshot().percent()).isZero();
    }
}
