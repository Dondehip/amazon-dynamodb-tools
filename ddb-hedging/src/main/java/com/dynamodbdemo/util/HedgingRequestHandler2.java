package com.dynamodbdemo.util;

import com.dynamodbdemo.model.DDBResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component("hedgingRequestHandler")
@ConditionalOnProperty(name = "aws.dynamodb.use-crt-client", havingValue = "true", matchIfMissing = true)
public class HedgingRequestHandler2 implements HedgingRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(CrtHedgingRequestHandler.class);

    public CompletableFuture<DDBResponse> hedgeRequests(
            Supplier<CompletableFuture<DDBResponse>> supplier,
           float delaysInMillis, boolean cancelPending)
    {



        logger.info("Initiating initial request");
        CompletableFuture<DDBResponse> firstRequest = supplier.get()
                .thenApply(response -> {
                    response.setRequestNumber(DDBResponse.FIRST_REQUEST); // First request is number 0
                    return response;
                });

        // Create a list to hold all futures (including the first request)
        List<CompletableFuture<DDBResponse>> allRequests = new ArrayList<>();
        allRequests.add(firstRequest);


            final int requestNumber = 2;

            //Convert to Nano Seconds
            long delay = (long)((double)delaysInMillis * 1_000_000L);
            // Calculate the future instant for scheduling
            Instant scheduledTime = Instant.now().plusNanos((long)delay);

            CompletableFuture<DDBResponse> hedgedRequest = CompletableFuture.supplyAsync(() -> {

                // Measure actual execution time
                Instant actualExecutionTime = Instant.now();

                // Calculate delay in milliseconds
                float schedulingDelay = (float) Duration.between(scheduledTime, actualExecutionTime).toNanos() /1_000_000L;

                logger.info(String.format("Scheduling delay for request: %.2f milliseconds", schedulingDelay));


                if (cancelPending) {
                    logger.info("Check Before hedged request#{} can be initiated", requestNumber);
                    // Check if any previous request is already complete
                    CompletableFuture<DDBResponse> completedFuture = allRequests.stream()
                            .filter(CompletableFuture::isDone)
                            .findFirst()
                            .orElse(null);

                    if (completedFuture != null) {
                        logger.info("Previous request already completed, skipping hedge request#{}", requestNumber);
                        return completedFuture.join();
                        //throw new CancellationException("Previous request already completed");
                    }
                }

                // If no previous request is complete, make new hedged request
                logger.info("Initiating hedge request#{}", requestNumber);

                return supplier.get()
                        .thenApply(response -> {
                            response.setRequestNumber(requestNumber);
                            response.setSchedulingDelay(schedulingDelay);
                            return response;
                        })
                        .exceptionally(throwable -> {
                            logger.warn("Hedged request#{} failed: {}", requestNumber, throwable.getMessage());
                            // If hedged request fails, wait for first request
                            return firstRequest.join();
                        })
                        .join();
            }, CompletableFuture.delayedExecutor(delay, TimeUnit.NANOSECONDS));

            allRequests.add(hedgedRequest);


        // Return the result of whichever request completes first and cancel others
        return CompletableFuture.anyOf(allRequests.toArray(new CompletableFuture[0]))
                .thenApply(result -> {
                    DDBResponse ddbResponse = (DDBResponse) result;
                    // Cancel all pending requests
                    if (cancelPending) {
                        cancelPendingRequests(allRequests, ddbResponse.getRequestNumber());
                    }
                    return ddbResponse;
                });
    }

    private void cancelPendingRequests(List<CompletableFuture<DDBResponse>> allRequests, int requestNumber) {
        logger.info("Request {} completed, cancelling other pending requests", requestNumber);
        allRequests.forEach(request -> {
            if (!request.isDone()) {
                request.cancel(true);
            }
        });
    }

}
