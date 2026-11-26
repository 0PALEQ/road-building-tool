package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

    @Test
    void importRequiresAuthorizationAndPublishesNewPresetsTogether() {
        var repository = new RoadPresetRepository();
        UUID operator = new UUID(0, 1);
        var service = new RoadPresetEditorService(repository, operator::equals);
        var existing = MinecraftIds.id("example", "existing");
        var added = MinecraftIds.id("example", "main");
        repository.replaceAll(Map.of(existing, preset(4)));
        var pack = Map.of(existing, preset(7), added, preset(7));
        long revision = repository.catalog().revision();

        assertEquals(new RoadPresetEditorService.ImportResult.Rejected(
                RoadPresetEditorService.EditFailure.NOT_AUTHORIZED),
                service.importPack(new UUID(0, 2), pack, 512));
        assertEquals(revision, repository.catalog().revision());

        assertEquals(new RoadPresetEditorService.ImportResult.Accepted(1, 1),
                service.importPack(operator, pack, 512));
        assertEquals(preset(4), repository.find(existing).orElseThrow());
        assertEquals(preset(1), repository.authoredSnapshot().get(added));
        assertEquals(revision + 1, repository.catalog().revision());

        assertEquals(new RoadPresetEditorService.ImportResult.Accepted(0, 2),
                service.importPack(operator, pack, 512));
        assertEquals(revision + 1, repository.catalog().revision());
    }

    @Test
    void failedImportLeavesTheCatalogUntouched() {
        var repository = new RoadPresetRepository();
        var actor = new UUID(0, 1);
        var first = MinecraftIds.id("example", "a");
        var second = MinecraftIds.id("example", "b");
        var pack = Map.of(first, preset(1), second, preset(2));
        var full = new RoadPresetEditorService(repository, actor::equals);
        var invalid = new RoadPresetEditorService(repository, actor::equals, ignored -> false);

        assertEquals(new RoadPresetEditorService.ImportResult.Rejected(
                RoadPresetEditorService.EditFailure.PAYLOAD_LIMIT),
                full.importPack(actor, pack, 1));
        assertEquals(new RoadPresetEditorService.ImportResult.Rejected(
                RoadPresetEditorService.EditFailure.UNKNOWN_BLOCK),
                invalid.importPack(actor, pack, 512));
        assertTrue(repository.authoredSnapshot().isEmpty());
        assertEquals(0L, repository.catalog().revision());
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
