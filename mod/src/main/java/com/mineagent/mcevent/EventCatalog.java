package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /evt/} 的静态事件目录。
 *
 * <p>每行先输出事件 type，再按 {@code Events.report} 里 {@code data.put} 的顺序输出字段名，
 * 以单个空格分隔。这里刻意保持静态：游戏里还没有事件发生时也能拿到完整目录。
 *
 * <p><b>新增事件或调整 data 字段时，记得同步维护本表。</b>
 */
final class EventCatalog {
    private static final Map<String, List<String>> EVENTS = new LinkedHashMap<>();

    static {
        EVENTS.put("player_hurt", List.of(
                "damage_type", "attacker_type", "attacker",
                "health_before", "health_after",
                "absorption_before", "absorption_after",
                "health_lost", "absorption_lost", "damage_taken",
                "max_health", "hits_same_tick"));
        EVENTS.put("item_pickup", List.of("item", "count"));
        EVENTS.put("chat", List.of("message"));
        EVENTS.put("hunger_change", List.of("from", "to", "threshold"));
        EVENTS.put("baritone_task_end", List.of("result", "goal"));
        EVENTS.put("dimension_change", List.of("from", "to"));
        EVENTS.put("player_death", List.of("message", "damage_type", "attacker", "attacker_type"));
        EVENTS.put("player_respawn", List.of("dimension"));
    }

    private EventCatalog() {
    }

    /** 整个目录，一行一个事件，行尾带换行。 */
    static String render() {
        StringBuilder out = new StringBuilder(512);
        EVENTS.forEach((type, fields) -> {
            out.append(type);
            for (String field : fields) {
                out.append(' ').append(field);
            }
            out.append('\n');
        });
        return out.toString();
    }
}
