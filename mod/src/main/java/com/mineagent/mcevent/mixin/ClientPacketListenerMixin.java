package com.mineagent.mcevent.mixin;

import com.mineagent.mcevent.HurtTracker;
import com.mineagent.mcevent.LifecycleTracker;
import com.mineagent.mcevent.PickupTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void mcevent$onDamage(ClientboundDamageEventPacket packet, CallbackInfo callbackInfo) {
        HurtTracker.onDamage(packet, Minecraft.getInstance());
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void mcevent$onTakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo callbackInfo) {
        PickupTracker.onTakeItem(packet, Minecraft.getInstance());
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"))
    private void mcevent$onDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo callbackInfo) {
        LifecycleTracker.onDeath(packet, Minecraft.getInstance());
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void mcevent$onRespawn(ClientboundRespawnPacket packet, CallbackInfo callbackInfo) {
        LifecycleTracker.onRespawn(packet, Minecraft.getInstance());
    }
}
