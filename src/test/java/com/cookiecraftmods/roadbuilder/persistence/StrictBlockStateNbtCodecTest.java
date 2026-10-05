package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictBlockStateNbtCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void roundTripsCanonicalStateWithProperties() {
        BlockState original = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP)
                .setValue(BlockStateProperties.WATERLOGGED, true);

        CompoundTag encoded = StrictBlockStateNbtCodec.write(original);
        BlockState decoded = StrictBlockStateNbtCodec.read(encoded).result().orElseThrow();

        assertEquals(original, decoded);
    }

    @Test
    void roundTripsCanonicalPropertylessState() {
        BlockState original = Blocks.STONE.defaultBlockState();

        assertEquals(original, StrictBlockStateNbtCodec.read(
                StrictBlockStateNbtCodec.write(original)
        ).result().orElseThrow());
    }

    @Test
    void rejectsMissingOrNonStringName() {
        assertError(new CompoundTag(), "Name must be a string");

        CompoundTag numericName = new CompoundTag();
        numericName.putInt("Name", 1);
        assertError(numericName, "Name must be a string");
    }

    @Test
    void rejectsMalformedAndUnknownBlockIdsWithoutDefaultingToAir() {
        CompoundTag malformed = new CompoundTag();
        malformed.putString("Name", "not a valid id");
        assertError(malformed, "invalid block state Name");

        CompoundTag unknown = new CompoundTag();
        unknown.putString("Name", new ResourceLocation(
                "roadbuilder", "definitely_missing"
        ).toString());
        assertError(unknown, "unknown block state Name");
    }

    @Test
    void rejectsNonCompoundProperties() {
        CompoundTag encoded = StrictBlockStateNbtCodec.write(
                Blocks.OAK_STAIRS.defaultBlockState()
        );
        encoded.putString("Properties", "facing=north");

        assertError(encoded, "Properties must be a compound");
    }

    @Test
    void rejectsUnknownPropertyNames() {
        CompoundTag encoded = StrictBlockStateNbtCodec.write(
                Blocks.OAK_STAIRS.defaultBlockState()
        );
        encoded.getCompound("Properties").putString("not_a_property", "value");

        assertError(encoded, "unknown block state property: not_a_property");
    }

    @Test
    void rejectsNonStringPropertyValues() {
        CompoundTag encoded = StrictBlockStateNbtCodec.write(
                Blocks.OAK_STAIRS.defaultBlockState()
        );
        encoded.getCompound("Properties").putInt("facing", 1);

        assertError(encoded, "property facing must be a string");
    }

    @Test
    void rejectsUnsupportedPropertyValues() {
        CompoundTag encoded = StrictBlockStateNbtCodec.write(
                Blocks.OAK_STAIRS.defaultBlockState()
        );
        encoded.getCompound("Properties").putString("facing", "sideways");

        assertError(encoded, "invalid value for block state property facing");
    }

    @Test
    void rejectsMissingPropertiesInsteadOfUsingBlockDefaults() {
        CompoundTag encoded = StrictBlockStateNbtCodec.write(
                Blocks.OAK_STAIRS.defaultBlockState()
        );
        encoded.getCompound("Properties").remove("facing");

        assertError(encoded, "missing block state property: facing");

        encoded.remove("Properties");
        assertError(encoded, "Properties is required");
    }

    private static void assertError(CompoundTag tag, String expectedMessagePart) {
        DataResult<BlockState> result = StrictBlockStateNbtCodec.read(tag);

        String message = result.error().orElseThrow().message();
        assertTrue(message.contains(expectedMessagePart), () -> "Unexpected error: " + message);
        assertTrue(result.result().isEmpty());
    }
}
