package org.spd.ratelimiter;

import java.time.Instant;

public class FixedWindowCounterRateLimiter implements RateLimiterStrategy {

    private final long windowSizeInSeconds;
    private final long maxRequestsPerWindow;
    private long currentWindowStart;
    private long requestCount;

    public FixedWindowCounterRateLimiter(long windowSizeInSeconds, long maxRequestsPerWindow) {
        this.windowSizeInSeconds = windowSizeInSeconds;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.currentWindowStart = Instant.now().getEpochSecond();
        this.requestCount = 0;
    }

    @Override
    public boolean allowRequest() {
        long now = Instant.now().getEpochSecond();
        if (now - currentWindowStart > windowSizeInSeconds) {
            currentWindowStart = now;
            requestCount = 0;
        }
        if (requestCount >= maxRequestsPerWindow) {
            return false;
        }
        requestCount++;
        return true;
    }
}
