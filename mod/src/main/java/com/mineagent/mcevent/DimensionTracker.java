package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/**
 * 维度切换事件：客户端所在维度发生变化时上报一次。
 *
 * <p>不需要挂网络包：每 tick 结束比较当前维度与上一 tick 的维度即可（传送门、跨维度
 * 指令最终都会让客户端切换 ClientLevel）。死亡后同维度重生不会触发；从主世界进下界
 * 与从下界回主世界都会各上报一条，方向由 data.from / data.to 区分。
 */
public final class DimensionTracker {
    private static Identifier lastDimension;

    private DimensionTracker() {
    }

    public static void onEndTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            lastDimension = null;
            return;
        }

        Identifier current = level.dimension().identifier();
        if (lastDimension != null && !lastDimension.equals(current)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("from", lastDimension.toString());
            data.put("to", current.toString());
            Events.report("dimension_change", data);
        }
        lastDimension = current;
    }
}
