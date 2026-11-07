package com.mineagent.mcevent;

import com.example.httpd.HttpdProvider;

import java.util.List;

/**
 * 把 {@code /evt/} 挂到 MGHttpdProvider 的共享服务（127.0.0.1:3420）。
 *
 * <p>这个类只在确认装了 provider 后才会被加载，没装时不会触发任何 provider 类。
 */
final class EventHttpHook {
    private EventHttpHook() {
    }

    static void register() {
        HttpdProvider.register("/evt", "MC Event Reporter — 事件类型目录",
                List.of(new HttpdProvider.Endpoint("GET", "/evt/", "事件类型与 data 字段名")),
                new EventEndpoint());
    }
}
