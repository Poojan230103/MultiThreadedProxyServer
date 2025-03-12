package org.spd;

import org.spd.proxy.server.ProxyServer;

public class Main {
    public static void main(String[] args) {
        ProxyServer proxyServer = new ProxyServer();
        proxyServer.start();
    }
}