package com.cookiecraftmods.roadbuilder.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

final class RoadVertices {
    private RoadVertices() {
    }

    static void color(
            VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            float red, float green, float blue, float alpha
    ) {
        vertices.addVertex(pose.pose(), x, y, z).setColor(red, green, blue, alpha);
    }

    static void line(
            VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            float red, float green, float blue, float alpha
    ) {
        vertices.addVertex(pose.pose(), x, y, z).setColor(red, green, blue, alpha)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
