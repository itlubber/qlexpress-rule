package com.hengshucredit.rule.server.health;

import org.junit.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.StatusAggregator;

import static org.junit.Assert.assertEquals;

public class RuntimeReadinessFailureMatrixTest {

    @Test
    public void dependencyAndWarmupFailuresAffectReadinessButNotLiveness() {
        assertScenario(Status.UP, Status.UP, ready(false), Status.UP);
        assertScenario(Status.DOWN, Status.UP, ready(false), Status.DOWN);
        assertScenario(Status.UP, Status.DOWN, ready(false), Status.DOWN);
        assertScenario(Status.UP, Status.UP, new OnnxWarmupStatus(), Status.OUT_OF_SERVICE);

        OnnxWarmupStatus failed = new OnnxWarmupStatus();
        failed.start(1);
        failed.recordFailure(new IllegalStateException("failed"));
        failed.complete();
        assertScenario(Status.UP, Status.UP, failed, Status.OUT_OF_SERVICE);

        assertScenario(Status.UP, Status.UP, ready(true), Status.UP);
    }

    private void assertScenario(Status db, Status redis, OnnxWarmupStatus warmup,
                                Status expectedReadiness) {
        Status liveness = StatusAggregator.getDefault().getAggregateStatus(Status.UP, Status.UP);
        Status onnx = new OnnxWarmupHealthIndicator(warmup).health().getStatus();
        Status readiness = StatusAggregator.getDefault().getAggregateStatus(
                Status.UP, db, redis, onnx);

        assertEquals(Status.UP, liveness);
        assertEquals(expectedReadiness, readiness);
    }

    private OnnxWarmupStatus ready(boolean cpuFallback) {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        status.start(cpuFallback ? 1 : 0);
        if (cpuFallback) status.recordSuccess(true);
        status.complete();
        return status;
    }
}
