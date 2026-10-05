package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorTargetTest {
    @Test
    void mapsTheSelectedSurfaceBlockToCanonicalCenterCoordinates() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(-4, 71, 9);

        CursorTarget target = CursorTarget.at(mutable);
        mutable.set(0, 0, 0);

        assertEquals(new BlockPos(-4, 71, 9), target.blockPosition());
        assertEquals(new RoadPoint(-3.5, 71.0, 9.5), target.roadPoint());
    }

    @Test
    void cursorResultEnforcesItsDiscriminatedVariant() {
        CursorTarget target = CursorTarget.at(BlockPos.ZERO);

        assertEquals(target, CursorTargetResult.success(target).target().orElseThrow());
        assertEquals(
                CursorTargetProblem.MISS,
                CursorTargetResult.failure(CursorTargetProblem.MISS).problem()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CursorTargetResult(CursorTargetProblem.NONE, Optional.empty())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CursorTargetResult(CursorTargetProblem.MISS, Optional.of(target))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CursorTargetResult.failure(CursorTargetProblem.NONE)
        );
    }
}
