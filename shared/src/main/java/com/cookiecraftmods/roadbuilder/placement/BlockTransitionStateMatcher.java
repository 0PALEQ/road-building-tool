package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public final class BlockTransitionStateMatcher {
    private BlockTransitionStateMatcher() {
    }

        public static boolean matchesRecordedSource(
            BlockState current,
            BlockState recordedSource
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(recordedSource, "recordedSource");
        if (matchesUpdateDrivenTarget(current, recordedSource)) {
            return true;
        }
        if ((recordedSource.isAir()
                && !current.getFluidState().isEmpty())
                || (current.isAir()
                && !recordedSource.getFluidState().isEmpty())) {
            return true;
        }
        return canDisappearDuringPlannedUpdates(recordedSource)
                && (current.isAir()
                || (!recordedSource.getFluidState().isEmpty()
                && current.equals(recordedSource.getFluidState().createLegacyBlock())));
    }

        public static boolean matchesPlannedTarget(
            BlockState current,
            BlockState plannedTarget
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(plannedTarget, "plannedTarget");
        return current.equals(plannedTarget)
                || current.getBlock() == plannedTarget.getBlock();
    }

        public static boolean matchesUpdateDrivenTarget(
            BlockState current,
            BlockState plannedTarget
    ) {
        return matchesPlannedTarget(current, plannedTarget)
                || isNaturalSoilTransition(
                        plannedTarget.getBlock(),
                        current.getBlock()
                ) || isHardenedConcrete(plannedTarget.getBlock(), current.getBlock());
    }

    private static boolean isHardenedConcrete(Block planned, Block current) {
        if (!(planned instanceof ConcretePowderBlock)) {
            return false;
        }
        var powder = BuiltInRegistries.BLOCK.getKey(planned);
        var solid = BuiltInRegistries.BLOCK.getKey(current);
        return powder.getNamespace().equals("minecraft")
                && solid.getNamespace().equals("minecraft")
                && powder.getPath().equals(solid.getPath() + "_powder");
    }

    public static boolean canDisappearDuringPlannedUpdates(
            BlockState recordedSource
    ) {
        Objects.requireNonNull(recordedSource, "recordedSource");
        return TerrainRestorationPolicy.isDisposable(recordedSource)
                || recordedSource.canBeReplaced()
                // Tall flowers are not replaceable, but either half disappears
                // when a planned write removes its support or the other half.
                || recordedSource.getBlock() instanceof DoublePlantBlock
                || recordedSource.is(BlockTags.LEAVES);
    }

    private static boolean isNaturalSoilTransition(
            Block recorded,
            Block current
    ) {
        if (recorded == Blocks.DIRT) {
            return current == Blocks.GRASS_BLOCK
                    || current == Blocks.MYCELIUM;
        }
        return current == Blocks.DIRT
                && (recorded == Blocks.GRASS_BLOCK
                || recorded == Blocks.MYCELIUM
                || recorded == Blocks.FARMLAND
                || recorded == Blocks.DIRT_PATH);
    }
}
