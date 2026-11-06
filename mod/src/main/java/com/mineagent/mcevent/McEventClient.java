package com.mineagent.mcevent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McEventClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mc-event");

    private static boolean baritoneAvailable;

    @Override
    public void onInitializeClient() {
        Reporter.init();

        if (FabricLoader.getInstance().isModLoaded("baritone")) {
            try {
                BaritoneHook.init();
                baritoneAvailable = true;
                LOGGER.info("mc-event: 已接入 Baritone 任务结束事件");
            } catch (Throwable throwable) {
                LOGGER.warn("mc-event: 接入 Baritone 失败，忽略该事件", throwable);
            }
        } else {
            LOGGER.info("mc-event: 未安装 Baritone，跳过任务结束事件");
        }
    }

    /** 每个客户端 tick 调用；没装 Baritone 时不会加载 BaritoneHook。 */
    public static void tickBaritone() {
        if (baritoneAvailable) {
            BaritoneHook.onEndTick();
        }
    }
}
