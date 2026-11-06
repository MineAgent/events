package com.mineagent.mcevent;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 聊天栏事件：上报聊天 HUD 收到的每一行文本。
 *
 * <p>原版把玩家聊天、指令反馈、系统提示以及模组输出（Baritone、hint 等）都汇入
 * {@code ChatComponent#addMessage}，在这个入口上挂一次就能全部拿到。
 */
public final class ChatTracker {
    private ChatTracker() {
    }

    public static void onChat(Component content, Minecraft minecraft) {
        if (content == null || minecraft.level == null) {
            return;
        }
        String text = content.getString();
        if (text.isEmpty()) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", text);
        Events.report("chat", data);
    }
}
