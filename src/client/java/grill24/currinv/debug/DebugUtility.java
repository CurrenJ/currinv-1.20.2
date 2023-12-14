package grill24.currinv.debug;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.navigation.NavigationData;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugUtility {

    public static List<BlockPos> getPathAheadOfPlayer(NavigationData navigationData) {
        List<BlockPos> pathAheadOfPlayer = navigationData.getPath();
        pathAheadOfPlayer = pathAheadOfPlayer.subList(navigationData.getCurrentPathNodeIndex(), Math.min(pathAheadOfPlayer.size(), navigationData.getCurrentPathNodeIndex() + 24));

        return pathAheadOfPlayer;
    }

    public static void drawPathLines(NavigationData navigationData) {
        List<BlockPos> pathAheadOfPlayer = getPathAheadOfPlayer(navigationData);

        for (int i = 1; i < pathAheadOfPlayer.size(); i++) {
            CurrInvClient.currInvDebugRenderer.addLine(pathAheadOfPlayer.get(i - 1).toCenterPos(), pathAheadOfPlayer.get(i).toCenterPos(), 1000, CurrInvDebugRenderer.GREEN);
        }
    }

    public static List<Vec3d> interpolateBetweenPoints(Vec3d from, Vec3d to, int numPoints) {
        // interpolate between from and to
        if (from == null)
            return Collections.singletonList(to);
        if (to == null)
            return Collections.singletonList(from);

        List<Vec3d> points = new ArrayList<>();
        for (int i = 1; i < numPoints; i++) {
            double t = (double) i / numPoints;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            points.add(new Vec3d(x, y, z));
        }
        return points;
    }

    public static void print(CommandContext<?> commandContext, String message) {
        if (commandContext.getSource() instanceof FabricClientCommandSource) {
            ((FabricClientCommandSource) commandContext.getSource()).sendFeedback(Text.literal(message));
        }
    }

    public static void print(MinecraftClient client, String message) {
        client.inGameHud.getChatHud().addMessage(Text.of(message));
    }

    static List<Vec3d> getCorners(BlockPos pos) {
        List<Vec3d> corners = new ArrayList<>();
        corners.add(new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
        corners.add(new Vec3d(pos.getX() + 1, pos.getY(), pos.getZ()));
        corners.add(new Vec3d(pos.getX(), pos.getY(), pos.getZ() + 1));
        corners.add(new Vec3d(pos.getX() + 1, pos.getY(), pos.getZ() + 1));
        corners.add(new Vec3d(pos.getX(), pos.getY() + 1, pos.getZ()));
        corners.add(new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ()));
        corners.add(new Vec3d(pos.getX(), pos.getY() + 1, pos.getZ() + 1));
        corners.add(new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
        return corners;
    }

    static List<Vec3d> getEdgeCenters(BlockPos pos) {
        List<Vec3d> edgeCenters = new ArrayList<>();
        edgeCenters.add(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ()));
        edgeCenters.add(new Vec3d(pos.getX() + 1, pos.getY(), pos.getZ() + 0.5));
        edgeCenters.add(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1));
        edgeCenters.add(new Vec3d(pos.getX(), pos.getY(), pos.getZ() + 0.5));
        edgeCenters.add(new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ()));
        edgeCenters.add(new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 0.5));
        edgeCenters.add(new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1));
        edgeCenters.add(new Vec3d(pos.getX(), pos.getY() + 1, pos.getZ() + 0.5));
        return edgeCenters;
    }
}
