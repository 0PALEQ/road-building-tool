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
        vertices.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).endVertex();
    }

    static void line(
            VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            float red, float green, float blue, float alpha
    ) {
        vertices.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }
}
