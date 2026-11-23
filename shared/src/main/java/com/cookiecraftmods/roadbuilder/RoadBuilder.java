package com.cookiecraftmods.roadbuilder;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import com.cookiecraftmods.roadbuilder.networking.RoadBuilderServerNetworking;
import com.cookiecraftmods.roadbuilder.testmatrix.RoadBuilderLiveMatrixServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoadBuilder implements ModInitializer {
    public static final String MOD_ID = "roadbuilder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RoadPresetResources.registerReloadListener();
        RoadBuilderServerNetworking.register();
        RoadBuilderLiveMatrixServer.registerIfEnabled();
        LOGGER.info("Initializing Road Builder");
    }
}
