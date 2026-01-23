package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for No Player Shutdown mod.
 * Allows server administrators to customize the shutdown delay.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SHUTDOWN_DELAY_MINUTES = BUILDER
            .comment("The number of minutes to wait before shutting down an empty server.")
            .comment("Default: 30 minutes")
            .defineInRange("shutdownDelayMinutes", 30, 1, 1440);

    static final ModConfigSpec SPEC = BUILDER.build();
}
