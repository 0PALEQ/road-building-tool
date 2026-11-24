package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.core.BlockPos;

public final class RoadMaterialNoise {
    private RoadMaterialNoise() {
    }

    public static int sampleIndex(int x, int y, int z, int variantCount) {
        if (variantCount == 1) {
            return 0;
        }

        // Use the full MurmurHash3 finalizer so low bits also mix all three coordinates.
        long hash = BlockPos.asLong(x, y, z);
        hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL;
        hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) Long.remainderUnsigned(hash ^ (hash >>> 33), variantCount);
    }
}
