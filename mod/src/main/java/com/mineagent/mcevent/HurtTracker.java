package com.mineagent.mcevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * 受伤事件。
 *
 * <p>纯客户端拿不到服务端的伤害数值，因此分两步：
 * 1. 收到 {@link ClientboundDamageEventPacket}（原版用来播放受伤倾斜/粒子的包）时记下伤害类型；
 * 2. 等生命值同步包到达后再结算（最多等 10 tick），用生命值/伤害吸收的前后差值。
 *
 * <p>这样「受伤」和「生命值降低」被区分开：被盾牌挡下或完全被伤害吸收吸收时生命值不变，
 * 但伤害事件仍然会上报，health_lost 为 0。同一 tick 内多次受伤会各自上报，
 * 并带上 hits_same_tick 说明它们共享同一份生命值差值。
 */
public final class HurtTracker {
    private static final int MAX_WAIT_TICKS = 10;

    private record Pending(String damageType, String attackerType, String attacker,
                           float healthBefore, float absorptionBefore, long tick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static long tick;
    private static float lastHealth = Float.NaN;
    private static float lastAbsorption = Float.NaN;

    /** 最近一次伤害的信息，player_death 会带上；没有受伤记录时为 null。 */
    private static String lastDamageType;
    private static String lastAttacker;
    private static String lastAttackerType;

    private HurtTracker() {
    }

    public static void onDamage(ClientboundDamageEventPacket packet, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        // 第一次可能是在网络线程上被调用的，原版随后会把包重新排队到主线程，
        // 这里直接跳过，避免同一个包被记录两次。
        if (player == null || packet.entityId() != player.getId()
                || Thread.currentThread() != minecraft.getRunningThread()) {
            return;
        }

        String damageType = packet.sourceType().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElseGet(() -> packet.sourceType().value().msgId());

        String attackerType = null;
        String attacker = null;
        ClientLevel level = minecraft.level;
        if (level != null) {
            DamageSource source = packet.getSource(level);
            Entity cause = source.getEntity();
            if (cause != null) {
                attacker = cause.getName().getString();
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(cause.getType());
                attackerType = key != null ? key.toString() : null;
            }
        }

        lastDamageType = damageType;
        lastAttacker = attacker;
        lastAttackerType = attackerType;
        PENDING.add(new Pending(damageType, attackerType, attacker, lastHealth, lastAbsorption, tick));
    }

    static String lastDamageType() {
        return lastDamageType;
    }

    static String lastAttacker() {
        return lastAttacker;
    }

    static String lastAttackerType() {
        return lastAttackerType;
    }

    public static void onEndTick(Minecraft minecraft) {
        tick++;
        LocalPlayer player = minecraft.player;
        if (player == null) {
            PENDING.clear();
            lastHealth = Float.NaN;
            lastAbsorption = Float.NaN;
            return;
        }

        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();

        if (!PENDING.isEmpty()) {
            boolean healthChanged = Float.isNaN(lastHealth)
                    || health != lastHealth
                    || absorption != lastAbsorption;
            boolean timedOut = tick - PENDING.get(0).tick() >= MAX_WAIT_TICKS;
            if (healthChanged || timedOut) {
                flush(player, health, absorption);
                PENDING.clear();
            }
        }

        lastHealth = health;
        lastAbsorption = absorption;
    }

    private static void flush(LocalPlayer player, float health, float absorption) {
        int hits = PENDING.size();
        for (Pending hit : PENDING) {
            float healthBefore = Float.isNaN(hit.healthBefore()) ? health : hit.healthBefore();
            float absorptionBefore = Float.isNaN(hit.absorptionBefore()) ? absorption : hit.absorptionBefore();
            double healthLost = Math.max(0.0, healthBefore - health);
            double absorptionLost = Math.max(0.0, absorptionBefore - absorption);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("damage_type", hit.damageType());
            data.put("attacker_type", hit.attackerType());
            data.put("attacker", hit.attacker());
            data.put("health_before", Events.round(healthBefore));
            data.put("health_after", Events.round(health));
            data.put("absorption_before", Events.round(absorptionBefore));
            data.put("absorption_after", Events.round(absorption));
            data.put("health_lost", Events.round(healthLost));
            data.put("absorption_lost", Events.round(absorptionLost));
            data.put("damage_taken", Events.round(healthLost + absorptionLost));
            data.put("max_health", Events.round(player.getMaxHealth()));
            data.put("hits_same_tick", hits);
            Events.report("player_hurt", data);
        }
    }
}
