package grill24.currinv.navigation;

import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.util.math.BlockPos;

import java.util.*;

@Deprecated
public class AStarMinecraft {
    public static List<Node> findPath(ClientWorld world, ClientPlayerEntity player, Node start, Node goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Set<Node> closedSet = new HashSet<>();

        start.g = 0;
        start.h = heuristic(start, goal);
        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (Node neighbor : current.getNeighbors(world, player, start.blockPos)) {
                if (closedSet.contains(neighbor)) {
                    continue; // Ignore the neighbor which is already evaluated
                }

                // Retrieve the weight from the Node
                int weight = neighbor.getWeight(world);

                int tentativeG = current.g + weight; // Cost from start to neighbor

                if (!openSet.contains(neighbor) || tentativeG < neighbor.g) {
                    neighbor.parent = current;
                    neighbor.g = tentativeG;
                    neighbor.h = heuristic(neighbor, goal);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return null; // No path found
    }

    private static int heuristic(Node a, Node b) {
        // A simple heuristic, such as Manhattan distance for a 3D grid
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }

    private static List<Node> reconstructPath(Node node) {
        List<Node> path = new ArrayList<>();
        while (node != null) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }

    public static Optional<List<BlockPos>> tryFindPath(ClientWorld world, ClientPlayerEntity player, BlockPos startPos, BlockPos goalPos) {
        Node start = new Node(startPos);
        Node goal = new Node(goalPos);

        List<Node> path = findPath(world, player, start, goal);

        List<BlockPos> blockPosPath = new ArrayList<>();
        if (path != null) {
            for (Node node : path) {
                blockPosPath.add(new BlockPos(node.x, node.y, node.z));
            }
            return Optional.of(blockPosPath);
        } else {
            return Optional.empty();
        }
    }
}
