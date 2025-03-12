package org.spd.cache;

public class CachedResponse {
    private String headers;
    private String body;
    long timestamp;

    public CachedResponse(String headers, String body) {
        this.headers = headers;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
    }

    public String getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}