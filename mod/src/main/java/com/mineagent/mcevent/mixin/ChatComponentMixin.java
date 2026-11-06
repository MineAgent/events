package com.mineagent.mcevent.mixin;

import com.mineagent.mcevent.ChatTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 所有进入聊天 HUD 的消息（玩家聊天、指令反馈、系统提示、模组输出）都会汇聚到
 * ChatComponent#addMessage，在这里统一上报。
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
                    + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void mcevent$onChat(Component content, MessageSignature signature, GuiMessageSource source,
                                GuiMessageTag tag, CallbackInfo callbackInfo) {
        ChatTracker.onChat(content, Minecraft.getInstance());
    }
}
