package com.dynamodbdemo.util;
import com.dynamodbdemo.util.DynamoDbLatencyTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class DynamoDbMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbMonitoringService.class);
    private final DynamoDbLatencyTracker latencyTracker;

    public DynamoDbMonitoringService(DynamoDbLatencyTracker latencyTracker) {
        this.latencyTracker = latencyTracker;
    }

    @Scheduled(fixedRateString = "${dynamodb.metrics.reporting.interval.ms:60000}")
    public void reportMetrics() {
        double p90 = latencyTracker.getP90Latency();
        double avg = latencyTracker.getAverageLatency();
        long max = latencyTracker.getMaxLatency();
        int count = latencyTracker.getMeasurementCount();

        log.info("DynamoDB Query Metrics - P90: {}ms, Avg: {}ms, Max: {}ms, Count: {}",
                String.format("%.2f", p90),
                String.format("%.2f", avg),
                max,
                count);
    }
}
