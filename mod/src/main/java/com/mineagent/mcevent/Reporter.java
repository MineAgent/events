package com.mineagent.mcevent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 后台把 JSON 事件 POST 到 127.0.0.1:3422。
 * 游戏线程只负责入队，网络读写全部在工作线程完成，不会卡帧。
 */
public final class Reporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("mc-event");
    private static final URI ENDPOINT =
            URI.create(System.getProperty("mcevent.endpoint", "http://127.0.0.1:3422/"));
    private static final int QUEUE_CAPACITY = 1024;

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    static {
        Thread worker = new Thread(Reporter::run, "mc-event-http");
        worker.setDaemon(true);
        worker.start();
    }

    private Reporter() {
    }

    /** 让类初始化并打印一次目标地址。 */
    static void init() {
        LOGGER.info("mc-event: 事件将 POST 到 {}", ENDPOINT);
    }

    /** 游戏线程调用：事件入队；队列满时丢弃最旧的一条。 */
    static void post(String json) {
        if (!QUEUE.offer(json)) {
            QUEUE.poll();
            QUEUE.offer(json);
        }
    }

    private static void run() {
        while (true) {
            String body;
            try {
                body = QUEUE.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300) {
                    LOGGER.debug("mc-event: 服务器返回 {}", response.statusCode());
                }
            } catch (IOException exception) {
                if (FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("mc-event: 无法连接 {}（后续失败只记录 debug）", ENDPOINT, exception);
                } else {
                    LOGGER.debug("mc-event: POST 失败", exception);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
