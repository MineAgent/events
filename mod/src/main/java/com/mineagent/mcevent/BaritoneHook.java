package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Baritone 任务结束事件。
 *
 * <p>只在确认装了 Baritone（mod id {@code baritone}）之后才会加载本类，没装时不会触发
 * 任何 Baritone 类。
 *
 * <p>检测方式是每 tick 看 {@link IPathingBehavior#getGoal()}：任务进行时它非空，任务结束
 * （到达、被取消、计算失败）后被 Baritone 清空。清空后延迟 2 tick 再判定，避免任务切换
 * 的瞬间误报。用末态目标是否包含玩家所在方块区分「完成」和「被取消」。
 */
final class BaritoneHook {
    private static final int IDLE_TICKS_BEFORE_END = 2;

    private static IBaritone baritone;
    private static Goal activeGoal;
    private static int idleTicks;

    private BaritoneHook() {
    }

    static void init() {
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    static void onEndTick() {
        if (baritone == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            activeGoal = null;
            idleTicks = 0;
            return;
        }
        if (Thread.currentThread() != minecraft.getRunningThread()) {
            return;
        }

        Goal goal = baritone.getPathingBehavior().getGoal();
        if (goal != null) {
            activeGoal = goal;
            idleTicks = 0;
            return;
        }

        if (activeGoal == null) {
            return;
        }
        if (++idleTicks < IDLE_TICKS_BEFORE_END) {
            return;
        }

        boolean completed = activeGoal.isInGoal(player.blockPosition());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", completed ? "completed" : "canceled");
        data.put("goal", activeGoal.toString());
        Events.report("baritone_task_end", data);

        activeGoal = null;
        idleTicks = 0;
    }
}
