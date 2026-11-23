package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionJobUiStateTest {
    @Test
    void reportsOnlyChangedProgress() {
        ConstructionJobUiState state = activeState();

        assertFalse(state.update(ConstructionJobPhase.PREPARED, 0));
        assertTrue(state.update(ConstructionJobPhase.APPLYING, 1));
        assertFalse(state.update(ConstructionJobPhase.APPLYING, 1));
    }

    @Test
    void cancellationIsConsumedOnceForAnAddressableActiveJob() {
        ConstructionJobUiState state = activeState();

        state.requestCancellation();

        assertTrue(state.consumeCancellationRequest());
        assertFalse(state.consumeCancellationRequest());
    }

    @Test
    void clearDropsIdentityAndPendingCancellation() {
        ConstructionJobUiState state = activeState();
        state.requestCancellation();

        state.clear();

        assertFalse(state.isActive());
        assertFalse(state.consumeCancellationRequest());
    }

    @Test
    void startRetainsServerIssuedIdentityAndDimension() {
        ConstructionJobUiState state = new ConstructionJobUiState();
        UUID id = UUID.randomUUID();
        ResourceLocation dimension = MinecraftIds.id("minecraft", "overworld");

        state.start(id, dimension, 0, ConstructionJobPhase.PREPARED);

        assertTrue(state.isActive());
        assertEquals(id, state.id());
        assertEquals(dimension, state.dimension());
    }

    private static ConstructionJobUiState activeState() {
        ConstructionJobUiState state = new ConstructionJobUiState();
        state.start(
                UUID.randomUUID(),
                MinecraftIds.id("minecraft", "overworld"),
                0,
                ConstructionJobPhase.PREPARED
        );
        return state;
    }
}
