package grill24.currinv.navigation;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

import java.util.List;

public class NavigationData {
    private static final double HORIZONTAL_DISTANCE_THRESHOLD = 0.4;
    private static final double VERTICAL_DISTANCE_THRESHOLD = 1e0;
    private static final long IS_LOST_TIMEOUT_MS = 5000;
    private final List<BlockPos> path;
    private int currentPathNodeIndex;
    private long timeArrivedAtCurrentNode;
    private boolean hasArrivedAtDestination;

    public NavigationData(List<BlockPos> path) {
        this.path = path;
        this.currentPathNodeIndex = 0;
        this.hasArrivedAtDestination = false;
        timeArrivedAtCurrentNode = System.currentTimeMillis();
    }

    public boolean onUpdate(ClientWorld world, ClientPlayerEntity player) {
        assert player != null;

        if (isNavigating()) {
            if (hasArrivedAtNextNode(player)) {
                currentPathNodeIndex++;
                timeArrivedAtCurrentNode = System.currentTimeMillis();
            } else {
                // Our little navigator is not perfect, and the world is a scary and unpredictable place.
                // If we get lost, assume we're at the path step immediately before the most recent one. If still lost, repeat.
                if (!hasArrivedAtDestination && System.currentTimeMillis() - timeArrivedAtCurrentNode > IS_LOST_TIMEOUT_MS) {
                    currentPathNodeIndex = Math.max(0, --currentPathNodeIndex);
                    timeArrivedAtCurrentNode = System.currentTimeMillis();
                    System.out.println("Lost! Resumed pathfinding at " + getCurrentNode());
                }
            }

            if (currentPathNodeIndex == path.size() - 1) {
                hasArrivedAtDestination = true;
            }
        }
        return hasArrivedAtDestination;
    }

    public BlockPos getNextNode() {
        return path.get(currentPathNodeIndex + 1);
    }

    public BlockPos getCurrentNode() {
        return path.get(currentPathNodeIndex);
    }

    private boolean hasArrivedAtNextNode(ClientPlayerEntity player) {
//        System.out.println("target: " + getNextNode().toCenterPos() + " | " + "player: " + player.getPos());

        if (currentPathNodeIndex + 1 < path.size() && hasArrivedAt(player, getNextNode()))
            return true;

        if (currentPathNodeIndex + 2 < path.size()) {
            if (hasArrivedAt(player, path.get(currentPathNodeIndex + 2))) {
                return true;
            } else {
                // Don't skip through diagonals
//                boolean skippedNextNode = Node.isDirectlyAdjacent(getCurrentNode(), path.get(currentPathNodeIndex + 2))
//                        && player.getPos().distanceTo(getNextNode().toCenterPos()) >= player.getPos().distanceTo(path.get(currentPathNodeIndex + 2).toCenterPos());
//                if (skippedNextNode)
//                    System.out.println("Skipped node!");
//                return skippedNextNode;
            }
        }

        return false;
    }

    private boolean hasArrivedAt(ClientPlayerEntity player, BlockPos blockPos) {
        double horiDistance = horizontalDistanceTo(player, blockPos);
        double vertDistance = Math.abs(player.getY() - blockPos.getY());

        return horiDistance <= HORIZONTAL_DISTANCE_THRESHOLD && vertDistance < VERTICAL_DISTANCE_THRESHOLD;
    }

    private double horizontalDistanceTo(ClientPlayerEntity player, BlockPos blockPos) {
        Vec3d blockCenter = blockPos.toCenterPos();
        return Vector2d.distance(player.getX(), player.getZ(), blockCenter.getX(), blockCenter.getZ());
    }

    private int getIndexOfClosestNode(ClientPlayerEntity player) {
        int closestNodeIndex = currentPathNodeIndex;
        double closestDistance = player.getPos().distanceTo(Vec3d.of(getCurrentNode()));
        for (int i = 0; i < path.size(); i++) {
            if (player.getPos().distanceTo(Vec3d.of(path.get(i))) < closestDistance)
                closestNodeIndex = i;
        }
        return closestNodeIndex;
    }

    public boolean isNavigating() {
        return !hasArrivedAtDestination;
    }

    public List<BlockPos> getPath() {
        return path;
    }

    public int getCurrentPathNodeIndex() {
        return currentPathNodeIndex;
    }
}
