package org.spd.cache;

import org.spd.server.ProxyServer;

import static org.spd.server.ProxyServer.logger;

public class CacheCleaner implements Runnable {

    private static final long CACHE_TTL = 60 * 1000;

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(60 * 1000);
                flushExpiredCacheEntries();
            } catch (InterruptedException e) {
                logger.error("CacheCleaner thread interrupted", e);
            }
        }
    }

    private void flushExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        ProxyServer.cache.entrySet().removeIf(entry -> {
            CachedResponse cachedResponse = entry.getValue();
            return (currentTime - cachedResponse.timestamp) > CACHE_TTL;
        });
        logger.info("Expired cache entries Flushed");
    }

}
