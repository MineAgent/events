package com.mineagent.mcevent.mixin;

import com.mineagent.mcevent.DimensionTracker;
import com.mineagent.mcevent.HungerTracker;
import com.mineagent.mcevent.HurtTracker;
import com.mineagent.mcevent.LifecycleTracker;
import com.mineagent.mcevent.McEventClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mcevent$endTick(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        HurtTracker.onEndTick(minecraft);
        HungerTracker.onEndTick(minecraft);
        LifecycleTracker.onEndTick(minecraft);
        DimensionTracker.onEndTick(minecraft);
        McEventClient.tickBaritone();
    }
}
