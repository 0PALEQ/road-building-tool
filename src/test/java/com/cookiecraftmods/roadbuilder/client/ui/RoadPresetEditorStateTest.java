package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoadPresetEditorStateTest {
    @Test
    void selectionMaintainsOnePrimaryMember() {
        RoadPresetEditorSelection selection = new RoadPresetEditorSelection();

        selection.selectOnly(2);
        selection.toggle(4);

        assertEquals(4, selection.primary());
        assertEquals(List.of(2, 4), selection.selected().stream().toList());

        selection.toggle(4);
        assertEquals(2, selection.primary());
        assertEquals(List.of(2), selection.selected().stream().toList());

        selection.clear();
        assertEquals(-1, selection.primary());
        assertTrue(selection.isEmpty());
    }

    @Test
    void selectionRejectsSentinelAsARealComponent() {
        RoadPresetEditorSelection selection = new RoadPresetEditorSelection();

        assertThrows(IllegalArgumentException.class, () -> selection.selectOnly(-1));
        assertThrows(IllegalArgumentException.class, () -> selection.toggle(-1));
    }

    @Test
    void confirmationsRequireTheSameActionTwiceAndResetEachOther() {
        RoadPresetEditorConfirmations confirmations =
                new RoadPresetEditorConfirmations();
        ResourceLocation first = new ResourceLocation("test", "first");
        ResourceLocation second = new ResourceLocation("test", "second");

        assertFalse(confirmations.confirmSwitch(first));
        assertTrue(confirmations.confirmSwitch(first));
        assertFalse(confirmations.confirmSwitch(second));

        assertTrue(confirmations.armDelete());
        assertTrue(confirmations.deleteArmed());
        assertFalse(confirmations.armDelete());

        assertFalse(confirmations.confirmClose());
        assertFalse(confirmations.deleteArmed());
        assertTrue(confirmations.confirmClose());

        confirmations.clear();
        assertFalse(confirmations.confirmClose());
    }

    @Test
    void dragActivatesOnlyAfterCrossingTheDistanceThreshold() {
        RoadPresetEditorDraft draft = new RoadPresetEditorDraft(
                RoadPresetTemplates.blank()
        );
        RoadPresetEditorComponent component =
                RoadPresetEditorComponentCatalog.prototype(
                        RoadPresetEditorComponentCatalog.Kind.FORWARD_LANE,
                        draft
                );
        RoadPresetEditorDragState drag = RoadPresetEditorDragState.palette(
                RoadPresetEditorComponentCatalog.Kind.FORWARD_LANE,
                component,
                10.0,
                10.0
        );

        drag.update(12.0, 12.0, 3);
        assertFalse(drag.active());
        assertEquals(-1, drag.dropIndex());

        drag.update(13.0, 10.0, 3);
        assertTrue(drag.active());
        assertEquals(3, drag.dropIndex());
    }

    @Test
    void catalogOwnsCanonicalComponentDefaults() {
        RoadPresetEditorDraft draft = new RoadPresetEditorDraft(
                RoadPresetTemplates.blank()
        );

        assertEquals(10, RoadPresetEditorComponentCatalog.filtered("").size());
        assertFalse(RoadPresetEditorComponentCatalog.filtered("lane").isEmpty());

        RoadPresetEditorComponent backward =
                RoadPresetEditorComponentCatalog.prototype(
                        RoadPresetEditorComponentCatalog.Kind.BACKWARD_LANE,
                        draft
                );
        assertTrue(backward.isVehicleLane());
        assertEquals(LaneTravelDirection.BACKWARD, backward.direction());

        RoadPresetEditorComponent doubleMarking =
                RoadPresetEditorComponentCatalog.prototype(
                        RoadPresetEditorComponentCatalog.Kind
                                .DOUBLE_CONTINUOUS_MARKING,
                        draft
                );
        assertEquals(3, doubleMarking.width());
    }

    @Test
    void requestStateOwnsTimeoutsAndSavePayloadLifetime() {
        RoadPresetEditorRequestState requests =
                new RoadPresetEditorRequestState();

        assertNotNull(requests.beginContentRequest());
        assertTrue(requests.contentPending());
        assertFalse(requests.tickContent(2));
        assertTrue(requests.tickContent(2));
        requests.finishContentRequest();
        assertFalse(requests.contentPending());

        var candidate = RoadPresetTemplates.blank();
        assertNotNull(requests.beginSave(candidate));
        assertFalse(requests.tickSave(2));
        assertTrue(requests.tickSave(2));
        RoadPresetEditorRequestState.SaveCompletion save =
                requests.finishSave();
        assertSame(candidate, save.candidate());
        assertFalse(save.deleting());
        assertFalse(requests.savePending());

        requests.beginDelete();
        RoadPresetEditorRequestState.SaveCompletion delete =
                requests.finishSave();
        assertNull(delete.candidate());
        assertTrue(delete.deleting());
    }

    @Test
    void copyFactoryPreservesRoadDataAndChoosesAnUnusedId() {
        var source = RoadPresetTemplates.blank();
        ResourceLocation sourceId = new ResourceLocation("built_in", "avenue");
        Set<ResourceLocation> existing = Set.of(
                new ResourceLocation("roadbuilder_world", "avenue_copy"),
                new ResourceLocation("roadbuilder_world", "avenue_copy_1")
        );

        RoadPresetEditorCopyFactory.Copy copy =
                RoadPresetEditorCopyFactory.create(
                        sourceId,
                        source,
                        "Copy of Avenue",
                        "large",
                        existing::contains
                );

        assertEquals(
                new ResourceLocation("roadbuilder_world", "avenue_copy_2"),
                copy.id()
        );
        assertEquals("Copy of Avenue", copy.name());
        assertEquals("large", copy.preset().display().category());
        assertEquals(source.lanes(), copy.preset().lanes());
        assertEquals(source.surfaceBands(), copy.preset().surfaceBands());
        assertEquals(source.materialPalette(), copy.preset().materialPalette());
    }
}
