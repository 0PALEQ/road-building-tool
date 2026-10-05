package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class RoadPresetTemplates {
    private RoadPresetTemplates() {
    }

    public static RoadPreset blank() {
        ResourceLocation pavement = new ResourceLocation(
                "minecraft",
                "gray_concrete"
        );
        RoadMaterialPalette materials = RoadMaterialPalette.defaults(pavement);
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                6,
                pavement,
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3,
                                -1.5,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3,
                                1.5,
                                LaneTravelDirection.FORWARD
                        )
                ),
                List.of(new RoadSurfaceBand(
                        RoadSurfaceRole.PAVEMENT,
                        6,
                        pavement
                )),
                new RoadPresetDisplay("", "New Road", "medium", "generated"),
                RoadClassification.DEFAULT,
                RoadIntersectionStyle.defaults(pavement),
                materials
        );
    }
}
