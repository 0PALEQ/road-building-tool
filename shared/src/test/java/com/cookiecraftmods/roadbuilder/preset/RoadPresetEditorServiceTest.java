package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetEditorServiceTest {
    @Test
    void authoritativeEditorRejectsStaleAndPublishesSortedCatalog() {
        RoadPresetRepository repository = new RoadPresetRepository();
        UUID operator = new UUID(0, 1);
        RoadPresetEditorService service = new RoadPresetEditorService(
                repository,
                operator::equals
        );
        ResourceLocation id = MinecraftIds.id("roadbuilder", "authored");
        RoadPreset revisionOne = preset(1);

        assertInstanceOf(
                RoadPresetEditorService.EditResult.Accepted.class,
                service.save(operator, id, 0, revisionOne)
        );
        assertEquals(revisionOne, repository.find(id).orElseThrow());
        assertTrue(repository.catalog().entries().get(0).editable());
        assertInstanceOf(
                RoadPresetEditorService.EditResult.Rejected.class,
                service.save(operator, id, 0, revisionOne)
        );
    }

    private static RoadPreset preset(int revision) {
        return new RoadPreset(
                1,
                revision,
                3.0,
                MinecraftIds.id("minecraft", "stone"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        2.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
    }
}
