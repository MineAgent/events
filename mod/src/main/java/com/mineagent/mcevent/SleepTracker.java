package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * “可以睡觉了”事件：从不能睡变为能睡时上报一次，并带上触发原因。
 *
 * <p>“能睡”直接采用客户端当前维度的原版床规则（{@code BED_RULE}），
 * 因此与 26.3 服务端判断一致：天黑（夜晚或雷暴使天空变暗）时返回 true，
 * 下界 / 末地等固定时间维度返回 false。
 *
 * <p>触发原因：
 * <ul>
 *   <li>{@code thunder}：状态变化时雷暴已经开始（雷等级 &gt; 0），它使天空变暗并解锁睡觉；</li>
 *   <li>{@code night}：其余情况，即入夜 / 天黑让睡觉变为可行。</li>
 * </ul>
 *
 * <p>进入世界、重生或切换维度的第一 tick 只记录基线，不补报；否则“进游戏时正好是夜晚”
 * 会误发。已经在能睡状态时雷暴开始也不重复上报，因为睡眠能力没有发生 false -&gt; true。
 */
public final class SleepTracker {
    private static boolean lastCanSleep;
    private static Identifier lastDimension;

    private SleepTracker() {
    }

    public static void onEndTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            reset();
            return;
        }

        Identifier dimension = level.dimension().identifier();
        boolean canSleep = canSleep(level);

        if (!dimension.equals(lastDimension)) {
            // 新维度 / 刚进世界：只建立基线，避免把“进入时已经能睡”当成一次状态变化。
            lastCanSleep = canSleep;
            lastDimension = dimension;
            return;
        }

        if (!lastCanSleep && canSleep) {
            Map<String, Object> data = new LinkedHashMap<>();
            // 不用 isThundering()：它按雷等级 > 0.9 判定，天气过渡时天空可能已经暗到
            // 能睡觉，但雷等级还没超过 0.9，会把雷暴误标成 night。雷等级只要大于 0
            // 就说明这次变暗有雷暴参与。
            data.put("reason", level.getThunderLevel(1.0F) > 0.0F ? "thunder" : "night");
            Events.report("can_sleep", data);
        }

        lastCanSleep = canSleep;
    }

    /** 当前维度是否允许睡觉；取不到规则时按不能睡处理。 */
    private static boolean canSleep(ClientLevel level) {
        BedRule rule = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.BED_RULE);
        return rule != null && rule.canSleep(level);
    }

    private static void reset() {
        lastCanSleep = false;
        lastDimension = null;
    }
}
