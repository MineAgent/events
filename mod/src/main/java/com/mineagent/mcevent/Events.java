package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * 统一的事件信封：{type, time, position, data}。
 *
 * <p>time 取游戏刻（{@code level.getGameTime()}，世界创建以来的总 tick 数），单调递增，
 * 方便排序和与其它日志对账。玩家名 / UUID / 维度不再重复放进信封，
 * 需要时由具体事件自己带（比如 dimension_change 的 from/to）。
 *
 * <p>新增事件时只需要用自己的 data 调用 {@link #report(String, Map)}。
 */
public final class Events {
    private Events() {
    }

    public static void report(String type, Map<String, Object> data) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("time", level != null ? level.getGameTime() : 0L);
        if (player != null) {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("x", round(player.getX()));
            position.put("y", round(player.getY()));
            position.put("z", round(player.getZ()));
            event.put("position", position);
        }
        event.put("data", data);
        Reporter.post(Json.write(event));
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
