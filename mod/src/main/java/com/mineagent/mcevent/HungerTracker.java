package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 饥饿值（FoodData 的 foodLevel，0–20）跨过关键阈值时上报。
 *
 * <p>只关心向下跨过这三个阈值，向上（吃东西）或普通变化不报：
 * <ul>
 *   <li>18：从 &gt;=18 变为 &lt;18 —— 停止自然回血；</li>
 *   <li>6：从 &gt;6 变为 &lt;=6 —— 不能疾跑；</li>
 *   <li>0：从 &gt;0 变为 0 —— 开始饿掉血。</li>
 * </ul>
 * 一 tick 内跨过多个阈值（例如被指令瞬间清空饥饿值）时，每个阈值各报一条。
 */
public final class HungerTracker {
    private static int lastFood = -1;

    private HungerTracker() {
    }

    public static void onEndTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastFood = -1;
            return;
        }

        int food = player.getFoodData().getFoodLevel();
        if (lastFood >= 0) {
            if (lastFood >= 18 && food < 18) {
                report(18, lastFood, food);
            }
            if (lastFood > 6 && food <= 6) {
                report(6, lastFood, food);
            }
            if (lastFood > 0 && food == 0) {
                report(0, lastFood, food);
            }
        }
        lastFood = food;
    }

    private static void report(int threshold, int from, int to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from);
        data.put("to", to);
        data.put("threshold", threshold);
        Events.report("hunger_change", data);
    }
}
