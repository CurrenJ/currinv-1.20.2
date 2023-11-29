package grill24.currinv.navigation;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AStarAsyncMinecraft {
    private static final int EXECUTION_TIME_LIMIT_PER_TICK_MS = 50;
    private static final int EXECUTION_TIME_LIMIT = 20000;

    PriorityQueue<Node> openSet;
    Set<Node> closedSet;
    Node start;
    Node current;
    Node goal;

    boolean started;
    boolean finished;
    List<Node> path;

    boolean acceptHighestElevationAlternativeGoal;
    Node highestAccessibleNode;

    private long startTime;

    public AStarAsyncMinecraft(boolean acceptHighestElevationAlternativeGoal)
    {
        openSet = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        closedSet = new HashSet<>();
        path = null;
        started = false;
        finished = false;
        this.acceptHighestElevationAlternativeGoal = acceptHighestElevationAlternativeGoal;
    }

    public void onUpdate(ClientWorld world, ClientPlayerEntity player)
    {
        if(!finished) {
            finished = findPath(world, player);
            if(finished) {
                System.out.println("Finished in " + (System.currentTimeMillis() - startTime) + "ms");
            }
        }
    }

    public void startFindPath(Node start, Node goal)
    {
        System.out.println("Finding path to " + goal.blockPos);

        started = true;
        finished = false;
        openSet.clear();
        closedSet.clear();
        start.g = 0;
        start.h = heuristic(start, goal);
        this.start = start;
        this.goal = goal;
        openSet.add(start);
        if(acceptHighestElevationAlternativeGoal)
            highestAccessibleNode = start;

        startTime = System.currentTimeMillis();
    }

    public boolean findPath(ClientWorld world, ClientPlayerEntity player) {
        long executionStartTime = System.currentTimeMillis();
        while (!openSet.isEmpty() && System.currentTimeMillis() - executionStartTime < EXECUTION_TIME_LIMIT_PER_TICK_MS && System.currentTimeMillis() - startTime < EXECUTION_TIME_LIMIT) {
            current = openSet.poll();

            assert current != null;
            if (current.equals(goal)) {
                path = reconstructPath(current);
                return true;
            }

            if(acceptHighestElevationAlternativeGoal && current.y > highestAccessibleNode.y)
            {
                highestAccessibleNode = current;
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


        if(System.currentTimeMillis() - startTime > EXECUTION_TIME_LIMIT)
        {
            System.out.println("Search time limit elapsed. Searched " + closedSet.size() + " blocks. " + goal.blockPos);
            if(acceptHighestElevationAlternativeGoal) {
                System.out.println("Highest node with path: " + highestAccessibleNode.blockPos);
                path = reconstructPath(highestAccessibleNode);
                return true;
            }
            return true;
        }

        if(openSet.isEmpty())
        {
            System.out.println("Could not find path to goal. Searched " + closedSet.size() + " blocks.");
            if(acceptHighestElevationAlternativeGoal) {
                System.out.println("Highest node with path: " + highestAccessibleNode.blockPos);
                path = reconstructPath(highestAccessibleNode);
                return true;
            }
        }

        return openSet.isEmpty(); // r we still workin'?
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

    public void tryStartPathfinding(BlockPos startPos, BlockPos goalPos) {
        Node start = new Node(startPos);
        Node goal = new Node(goalPos);

        if(!started)
            startFindPath(start, goal);
    }

    public Optional<List<BlockPos>> tryGetPath() {
        if(finished) {
            List<BlockPos> blockPosPath = new ArrayList<>();
            if (path != null) {
                for (Node node : path) {
                    blockPosPath.add(new BlockPos(node.x, node.y, node.z));
                }
                return Optional.of(blockPosPath);
            }
        }
        return Optional.empty();
    }

    public boolean isFinished()
    {
        return finished;
    }

    public boolean isStarted()
    {
        return started;
    }
}
