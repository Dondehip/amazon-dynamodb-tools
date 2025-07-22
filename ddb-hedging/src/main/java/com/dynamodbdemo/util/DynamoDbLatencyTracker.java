package com.dynamodbdemo.util;

import com.dynamodbdemo.dao.EntityRecordDDbNativeDAO;
import com.dynamodbdemo.model.DDBResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Aspect
@Component
public class DynamoDbLatencyTracker {

    private static final Logger logger = Logger.getLogger(DynamoDbLatencyTracker.class.getName());
    private final ConcurrentSkipListMap<Instant, Long> latencyMeasurements;
    private final long windowSizeMs;

    public DynamoDbLatencyTracker(@Value("${dynamodb.latency.window.size.ms:300000}") long windowSizeMs) {
        this.windowSizeMs = windowSizeMs;
        this.latencyMeasurements = new ConcurrentSkipListMap<>();
    }

    @Around("execution(* com.dynamodbdemo.dao.EntityRecordDDbNativeDAO.fetchByRecordIDAndEntityNumberAsync(..))")
    public Object trackQueryLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();

        CompletableFuture<DDBResponse> future = (CompletableFuture<DDBResponse>) joinPoint.proceed();

        return future.whenComplete((response, error) -> {
            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            recordLatency(latencyMs);

            if (error != null) {
                // Optionally handle or log error
                logger.log(Level.SEVERE, "DynamoDB query failed", error);
            }
        });
    }

    private void recordLatency(long latencyMs) {
        latencyMeasurements.put(Instant.now(), latencyMs);
        removeOldMeasurements();
    }

    private void removeOldMeasurements() {
        Instant cutoffTime = Instant.now().minusMillis(windowSizeMs);
        latencyMeasurements.headMap(cutoffTime).clear();
    }

    public double getP90Latency() {
        List<Long> currentMeasurements = new ArrayList<>(latencyMeasurements.values());

        if (currentMeasurements.isEmpty()) {
            return 0.0;
        }

        currentMeasurements.sort(Long::compareTo);
        int index = (int) Math.ceil(currentMeasurements.size() * 0.9) - 1;
        return currentMeasurements.get(Math.max(0, index));
    }

    // Additional utility methods for metrics
    public double getAverageLatency() {
        List<Long> measurements = new ArrayList<>(latencyMeasurements.values());
        if (measurements.isEmpty()) {
            return 0.0;
        }
        return measurements.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    public long getMaxLatency() {
        return latencyMeasurements.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
    }

    public int getMeasurementCount() {
        return latencyMeasurements.size();
    }
}
