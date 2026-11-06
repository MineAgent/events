package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 获得物品事件。
 *
 * <p>原版在玩家真正把掉落物收进背包后发送 {@link ClientboundTakeItemEntityPacket}
 * （同时包含物品实体 id、玩家 id、数量），客户端在包处理器里据此播放拾取音效。
 * 我们在包处理前读取仍在场景中的物品实体，拿到物品 id 与显示名。
 * 只关心本地玩家自己的拾取，忽略经验球。
 */
public final class PickupTracker {
    private PickupTracker() {
    }

    public static void onTakeItem(ClientboundTakeItemEntityPacket packet, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        // 网络线程上的第一次调用会被原版重新排队到主线程，跳过以免重复上报。
        if (player == null || packet.getPlayerId() != player.getId()
                || Thread.currentThread() != minecraft.getRunningThread()) {
            return;
        }

        ClientLevel level = minecraft.level;
        Entity entity = level != null ? level.getEntity(packet.getItemId()) : null;
        if (entity instanceof ExperienceOrb) {
            return;
        }

        String item = null;
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            item = key != null ? key.toString() : null;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item", item);
        data.put("count", packet.getAmount());
        Events.report("item_pickup", data);
    }
}
