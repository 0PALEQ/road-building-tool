package com.cookiecraftmods.roadbuilder.client.tool;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementContinuationTest {
    @Test
    void completionReturnsTheSubmittedEndpointAndLatestQueuedTarget() {
        PlacementContinuation continuation = new PlacementContinuation();
        CursorTarget submitted = CursorTarget.at(new BlockPos(1, 64, 2));
        CursorTarget firstQueued = CursorTarget.at(new BlockPos(3, 64, 4));
        CursorTarget latestQueued = CursorTarget.at(new BlockPos(5, 64, 6));

        continuation.submitted(submitted);
        continuation.queue(firstQueued);
        continuation.queue(latestQueued);
        PlacementContinuation.Completion completion = continuation.complete();

        assertEquals(submitted, completion.endpoint());
        assertEquals(latestQueued, completion.queuedTarget());
        assertFalse(continuation.isPending());
    }

    @Test
    void cancellationDropsBothTheContinuationAndQueuedTarget() {
        PlacementContinuation continuation = new PlacementContinuation();
        continuation.submitted(CursorTarget.at(new BlockPos(1, 64, 2)));
        continuation.queue(CursorTarget.at(new BlockPos(3, 64, 4)));

        continuation.clear();

        assertFalse(continuation.isPending());
        PlacementContinuation.Completion completion = continuation.complete();
        assertNull(completion.endpoint());
        assertNull(completion.queuedTarget());
    }

    @Test
    void queueRequiresAnOutstandingSubmission() {
        PlacementContinuation continuation = new PlacementContinuation();

        assertThrows(
                IllegalStateException.class,
                () -> continuation.queue(CursorTarget.at(BlockPos.ZERO))
        );
        assertFalse(continuation.isPending());
    }

    @Test
    void submittedEndpointMarksTheChainPending() {
        PlacementContinuation continuation = new PlacementContinuation();
        CursorTarget endpoint = CursorTarget.at(new BlockPos(1, 64, 2));

        continuation.submitted(endpoint);

        assertTrue(continuation.isPending());
        assertEquals(endpoint, continuation.endpoint());
    }
}
