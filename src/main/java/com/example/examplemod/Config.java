package com.example.examplemod;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for Empty Server Stopper mod.
 * Allows server administrators to customize the shutdown delay.
 */
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue SHUTDOWN_DELAY_MINUTES = BUILDER
            .comment("The number of minutes to wait before shutting down an empty server.")
            .comment("Default: 30 minutes")
            .defineInRange("shutdownDelayMinutes", 30, 1, 1440);

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
