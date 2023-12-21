package grill24.currinv.debug;

import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandOption;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Command(value = "debug", debug = true)
public class CurrInvDebugRenderer implements DebugRenderer.Renderer {
    private record LineSegment(Vec3d a, Vec3d b, long expiryTime, int rgb) {
    }

    private record Cube(BlockPos pos, float expand, long expiryTime, int rgb) {
    }

    private List<LineSegment> lineSegments = new ArrayList<>();
    private List<Cube> cubes = new ArrayList<>();
    public static final int RED = MathHelper.packRgb(1, 0, 0);
    public static final int GREEN = MathHelper.packRgb(0, 1, 0);
    public static final int BLUE = MathHelper.packRgb(0, 0, 1);

    @CommandOption(value = "debugRenderer", debug = true)
    public boolean isEnabled = true;

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        if (isEnabled) {
            drawLines(matrices, vertexConsumers, cameraX, cameraY, cameraZ, lineSegments);
            drawCubes(matrices, vertexConsumers, cameraX, cameraY, cameraZ, cubes);
        }

        removeExpired((lineSegment) -> lineSegment.expiryTime, lineSegments);
        removeExpired((cube) -> cube.expiryTime, cubes);
    }

    public static <T> void removeExpired(Function<T, Long> getExpiryTime, Collection<T> items) {
        List<T> expiredSegments = items.stream().filter((item) -> getExpiryTime.apply(item) < System.currentTimeMillis()).toList();
        items.removeAll(expiredSegments);
    }

    public void addLine(Vec3d a, Vec3d b, long durationMs, int rgb) {
        lineSegments.add(new LineSegment(a, b, System.currentTimeMillis() + durationMs, rgb));
    }

    public void addCube(BlockPos pos, float expand, long durationMs, int rgb) {
        cubes.add(new Cube(pos, expand, System.currentTimeMillis() + durationMs, rgb));
    }

    private static void drawLines(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, List<LineSegment> lineSegments) {
        for (int i = 0; i < lineSegments.size(); ++i) {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(6.0));
            LineSegment lineSegment = lineSegments.get(i);
            int j = lineSegment.rgb;
            int k = j >> 16 & 0xFF;
            int l = j >> 8 & 0xFF;
            int m = j & 0xFF;
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float) (lineSegment.a.x - cameraX), (float) (lineSegment.a.y - cameraY), (float) (lineSegment.a.z - cameraZ)).color(k, l, m, 255).next();
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float) (lineSegment.b.x - cameraX), (float) (lineSegment.b.y - cameraY), (float) (lineSegment.b.z - cameraZ)).color(k, l, m, 255).next();
        }
    }

    private static void drawCubes(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, List<Cube> cubes) {
        for (int i = 0; i < cubes.size(); i++) {
            Cube cube = cubes.get(i);
            int j = cube.rgb;
            float r = (j >> 16 & 0xFF) / 255.0f;
            float g = (j >> 8 & 0xFF) / 255.0f;
            float b = (j & 0xFF) / 255.0f;
            Box box = new Box(cube.pos).expand(cube.expand);
            DebugRenderer.drawBox(matrices, vertexConsumers, cube.pos, cube.expand, r, g, b, 0.25F);
        }
    }
}
