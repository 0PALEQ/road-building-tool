package com.cookiecraftmods.roadbuilder.networking;

final class RoadPacketTypes {
    private RoadPacketTypes() {
    }

    static void register() {
        ClientRoadIntentC2SPacket.TYPE.registerC2S();
        ConstructionJobCancelC2SPacket.TYPE.registerC2S();
        CrosswalkToggleIntentC2SPacket.TYPE.registerC2S();
        LaneConnectorEditC2SPacket.TYPE.registerC2S();
        PresetContentRequestC2SPacket.TYPE.registerC2S();
        PresetDeleteIntentC2SPacket.TYPE.registerC2S();
        PresetSaveIntentC2SPacket.TYPE.registerC2S();
        RemovalIntentC2SPacket.TYPE.registerC2S();
        RoadGraphResyncC2SPacket.TYPE.registerC2S();
        RoadPathIntentC2SPacket.TYPE.registerC2S();
        SegmentDirectionInvertIntentC2SPacket.TYPE.registerC2S();
        SegmentRemovalIntentC2SPacket.TYPE.registerC2S();
        SegmentReplaceIntentC2SPacket.TYPE.registerC2S();
        StraightRoadIntentC2SPacket.TYPE.registerC2S();

        ConstructionJobCancelResultS2CPacket.TYPE.registerS2C();
        ConstructionJobProgressS2CPacket.TYPE.registerS2C();
        PlacementResultS2CPacket.TYPE.registerS2C();
        PresetCatalogS2CPacket.TYPE.registerS2C();
        PresetContentS2CPacket.TYPE.registerS2C();
        PresetSaveResultS2CPacket.TYPE.registerS2C();
        RemovalResultS2CPacket.TYPE.registerS2C();
        RoadGraphDeltaS2CPacket.TYPE.registerS2C();
        RoadGraphSnapshotChunkS2CPacket.TYPE.registerS2C();
        RoadGraphSnapshotS2CPacket.TYPE.registerS2C();
    }
}
