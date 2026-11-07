package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

/**
 * 玩家死亡 / 重生事件。
 *
 * <p>死亡以 {@link ClientboundPlayerCombatKillPacket} 为准（带原版死亡消息），只处理本机玩家；
 * 最近一次伤害的类型 / 攻击者从 {@link HurtTracker} 取。
 *
 * <p>重生用 {@link ClientboundRespawnPacket} 的 TAIL，但真正的上报推迟到本 tick 结束，
 * 这样玩家坐标已经切到重生点。传送门跨维度同样会走这个包，但死亡标记没置位，
 * 所以只会由 {@link DimensionTracker} 发 dimension_change，不会误报 player_respawn。
 */
public final class LifecycleTracker {
    private static boolean dead;
    private static String respawnDimension;

    private LifecycleTracker() {
    }

    public static void onDeath(ClientboundPlayerCombatKillPacket packet, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || packet.playerId() != player.getId()
                || Thread.currentThread() != minecraft.getRunningThread()
                || dead) {
            return;
        }

        dead = true;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", packet.message().getString());
        data.put("damage_type", HurtTracker.lastDamageType());
        data.put("attacker", HurtTracker.lastAttacker());
        data.put("attacker_type", HurtTracker.lastAttackerType());
        Events.report("player_death", data);
    }

    public static void onRespawn(ClientboundRespawnPacket packet, Minecraft minecraft) {
        if (!dead || Thread.currentThread() != minecraft.getRunningThread()) {
            return;
        }
        respawnDimension = packet.commonPlayerSpawnInfo().dimension().identifier().toString();
    }

    public static void onEndTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            dead = false;
            respawnDimension = null;
            return;
        }
        if (respawnDimension == null) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dimension", respawnDimension);
        Events.report("player_respawn", data);

        respawnDimension = null;
        dead = false;
    }
}
