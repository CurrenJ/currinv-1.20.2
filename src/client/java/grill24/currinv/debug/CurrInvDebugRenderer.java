package grill24.currinv.debug;

import grill24.currinv.component.Command;
import grill24.currinv.component.CommandOption;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.render.debug.PathfindingDebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import javax.sound.sampled.Line;
import java.util.ArrayList;
import java.util.List;

@Command("debugRenderer")
public class CurrInvDebugRenderer implements DebugRenderer.Renderer{
    private record LineSegment(Vec3d a, Vec3d b, long expiryTime) {}

    public List<LineSegment> lineSegments = new ArrayList<>();

    @CommandOption("lineSegmentRenderer")
    public boolean isEnabled;

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        if(isEnabled)
            drawLines(matrices, vertexConsumers, cameraX, cameraY, cameraZ, lineSegments);

        List<LineSegment> expiredSegments = lineSegments.stream().filter((lineSegment) ->  lineSegment.expiryTime < System.currentTimeMillis()).toList();
        lineSegments.removeAll(expiredSegments);
    }

    public void addLine(Vec3d a, Vec3d b, long durationMs) {
        lineSegments.add(new LineSegment(a, b, System.currentTimeMillis() + durationMs));
    }

    private static void drawLines(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, List<LineSegment> lineSegments) {
        for (int i = 0; i < lineSegments.size(); ++i) {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(6.0));
            LineSegment lineSegment = lineSegments.get(i);
            int j = MathHelper.hsvToRgb((float)0, (float)0.9f, (float)0.9f);
            int k = j >> 16 & 0xFF;
            int l = j >> 8 & 0xFF;
            int m = j & 0xFF;
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(lineSegment.a.x - cameraX), (float)(lineSegment.a.y - cameraY), (float)(lineSegment.a.z - cameraZ)).color(k, l, m, 255).next();
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(lineSegment.b.x - cameraX), (float)(lineSegment.b.y - cameraY), (float)(lineSegment.b.z - cameraZ)).color(k, l, m, 255).next();
        }
    }
}
