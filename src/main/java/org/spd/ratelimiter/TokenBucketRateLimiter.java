package org.spd.ratelimiter;

import java.sql.Timestamp;
import java.time.Instant;

public class TokenBucketRateLimiter implements RateLimiterStrategy {

    private final long capacity;
    private final double fillRate;
    private double tokens;
    private Instant lastRefillTimestamp;

    public TokenBucketRateLimiter(long capacity, double fillRate) {
        this.capacity = capacity;
        this.fillRate = fillRate;
        this.tokens = capacity;
        this.lastRefillTimestamp = Instant.now();
    }

    @Override
    public synchronized boolean allowRequest() {
        refillTokens();
        if (this.tokens == 0) {
            return false;
        }
        this.tokens--;
        return true;
    }

    private void refillTokens() {
        Instant now = Instant.now();
        double tokensToAdd = (now.toEpochMilli() - lastRefillTimestamp.toEpochMilli()) * fillRate / 1000.0;
        this.tokens = Math.min(capacity, this.tokens + tokensToAdd);
        this.lastRefillTimestamp = now;
    }

}
