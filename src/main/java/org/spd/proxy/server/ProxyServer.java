package org.spd.proxy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spd.cache.CacheCleaner;
import org.spd.cache.CachedResponse;
import org.spd.cache.LRUCache;
import org.spd.ratelimiter.RateLimiterStrategy;
import org.spd.ratelimiter.FixedWindowCounterRateLimiter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 20;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    public static final Logger logger = LoggerFactory.getLogger(ProxyServer.class);
    public static final LRUCache<String, CachedResponse> cache = new LRUCache<>(100);
    private static final RateLimiterStrategy rateLimiter = new FixedWindowCounterRateLimiter(60, 2);

    public void start() {

        Thread cacheCleanupThread = new Thread(new CacheCleaner());
        cacheCleanupThread.setDaemon(true); // Mark as daemon thread
        cacheCleanupThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            threadPool.shutdown();
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (rateLimiter.allowRequest()) {
                    logger.info("New Client connected: " + clientSocket.getInetAddress());
                    threadPool.submit(() -> handleRequest(clientSocket));
                } else {
                    logger.info("Max. Request limit reached. Please try after sometime.");
                    sendErrorResponse(clientSocket, 429, "Too Many Requests");
                }
            }
        } catch (IOException e) {
            logger.error("Server error", e);
        }
    }

    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }

            if (requestBuilder.isEmpty()) {
                logger.warn("Empty request received");
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            String request = requestBuilder.toString();
            logger.info("Received request:\n{}", request);

            String[] requestLines = request.split("\r\n");
            String[] requestLineParts = requestLines[0].split(" ");
            if (requestLineParts.length < 2) {
                logger.warn("Invalid request line: {}", requestLines[0]);
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            String method = requestLineParts[0];
            String url = requestLineParts[1];

            if ("GET".equalsIgnoreCase(method)) {
                handleGetRequest(out, url);
            } else {
                sendErrorResponse(out, 501, "Not Implemented");
            }
        } catch (IOException e) {
            logger.error("Error handling request", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    private static void handleGetRequest(OutputStream out, String url) throws IOException {
        CachedResponse cachedResponse = cache.get(url);
        if (cachedResponse != null) {
            logger.info("Cache hit for URL: {}", url);
            out.write(cachedResponse.getHeaders().getBytes());
            out.write(cachedResponse.getBody().getBytes());
            out.flush();
            return;
        }

        logger.info("Cache miss for URL: {}", url);
        URL backendUrl = new URL("https://www.ifixit.com" + url);
        HttpURLConnection connection = (HttpURLConnection) backendUrl.openConnection();
        connection.setRequestMethod("GET");

        int statusCode = connection.getResponseCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Backend server returned status code: " + statusCode);
        }

        StringBuilder headersBuilder = new StringBuilder();
        headersBuilder.append("HTTP/1.1 ").append(statusCode).append(" ").append(connection.getResponseMessage()).append("\r\n");
        connection.getHeaderFields().forEach((key, values) -> {
            if (key != null) {
                values.forEach(value -> headersBuilder.append(key).append(": ").append(value).append("\r\n"));
            }
        });
        headersBuilder.append("\r\n");

        StringBuilder bodyBuilder = new StringBuilder();
        try (BufferedReader backendIn = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = backendIn.readLine()) != null) {
                bodyBuilder.append(line).append("\r\n");
            }
        }

        CachedResponse response = new CachedResponse(headersBuilder.toString(), bodyBuilder.toString());
        cache.put(url, response);

        out.write(headersBuilder.toString().getBytes());
        out.write(bodyBuilder.toString().getBytes());
        out.flush();
    }

    private static void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) {
        try {
            String response = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n\r\n";
            out.write(response.getBytes());
            out.flush();
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
    }

    private static void sendErrorResponse(Socket clientSocket, int statusCode, String statusMessage) {
        try (OutputStream out = clientSocket.getOutputStream()) {
            sendErrorResponse(out, statusCode, statusMessage);
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
    }
}