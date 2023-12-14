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

@Command("debug")
public class CurrInvDebugRenderer implements DebugRenderer.Renderer{
    private record LineSegment(Vec3d a, Vec3d b, long expiryTime, int rgb) {}

    private List<LineSegment> lineSegments = new ArrayList<>();
    public static final int RED = MathHelper.packRgb(1, 0, 0);
    public static final int GREEN = MathHelper.packRgb(0, 1, 0);
    public static final int BLUE = MathHelper.packRgb(0, 0, 1);

    @CommandOption("lineSegmentRenderer")
    public boolean isEnabled = true;

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        if(isEnabled)
            drawLines(matrices, vertexConsumers, cameraX, cameraY, cameraZ, lineSegments);

        List<LineSegment> expiredSegments = lineSegments.stream().filter((lineSegment) ->  lineSegment.expiryTime < System.currentTimeMillis()).toList();
        lineSegments.removeAll(expiredSegments);
    }

    public void addLine(Vec3d a, Vec3d b, long durationMs, int rgb) {
        lineSegments.add(new LineSegment(a, b, System.currentTimeMillis() + durationMs, rgb));
    }

    private static void drawLines(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, List<LineSegment> lineSegments) {
        for (int i = 0; i < lineSegments.size(); ++i) {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(6.0));
            LineSegment lineSegment = lineSegments.get(i);
            int j = lineSegment.rgb;
            int k = j >> 16 & 0xFF;
            int l = j >> 8 & 0xFF;
            int m = j & 0xFF;
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(lineSegment.a.x - cameraX), (float)(lineSegment.a.y - cameraY), (float)(lineSegment.a.z - cameraZ)).color(k, l, m, 255).next();
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(lineSegment.b.x - cameraX), (float)(lineSegment.b.y - cameraY), (float)(lineSegment.b.z - cameraZ)).color(k, l, m, 255).next();
        }
    }
}
