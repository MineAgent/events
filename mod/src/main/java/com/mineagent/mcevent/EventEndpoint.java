package com.mineagent.mcevent;

import com.example.httpd.PathHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 挂在 MGHttpdProvider 的 {@code /evt} 前缀下，只读返回 {@link EventCatalog}。
 *
 * <p>{@code GET /evt/} 与 {@code GET /evt} 返回目录；其他子路径 404，非 GET/HEAD 405。
 * 不访问游戏状态，直接在 HTTP 工作线程返回。
 */
final class EventEndpoint implements PathHandler {
    private static final byte[] BODY = EventCatalog.render().getBytes(StandardCharsets.UTF_8);

    @Override
    public void handle(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            respond(exchange, 405, "method not allowed: " + method + " (use GET)\n");
            return;
        }
        if (!path.isEmpty() && !"/".equals(path)) {
            respond(exchange, 404, "not found\n");
            return;
        }
        respond(exchange, 200, BODY);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod()) || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
