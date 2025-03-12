package org.spd.ratelimiter;

public interface RateLimiterStrategy {
    boolean allowRequest();
}
