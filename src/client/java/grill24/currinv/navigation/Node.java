package grill24.currinv.navigation;

import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Node {
    private static final int MAX_SEARCH_RADIUS = Integer.MAX_VALUE;

    int x, y, z; // Coordinates of the node
    BlockPos blockPos;
    int g;       // Cost from the start node to this node
    int h;       // Heuristic estimate from this node to the goal node
    Node parent;  // Parent node in the path

    public Node(BlockPos blockPos) {
        this.x = blockPos.getX();
        this.y = blockPos.getY();
        this.z = blockPos.getZ();
        this.blockPos = blockPos;
        this.g = 0;
        this.h = 0;
        this.parent = null;
    }

    public int f() {
        return g + h;
    }

    // Override equals and hashCode for HashSet usage
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Node node = (Node) obj;
        return x == node.x && y == node.y && z == node.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    public List<Node> getNeighbors(ClientWorld world, ClientPlayerEntity player, BlockPos searchOrigin) {
        List<Node> neighbors = new ArrayList<>();

        // Adjacent
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.north(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.south(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.west(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.up(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.down(), neighbors);

        // Diagonals
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.north().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.north().west(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.south().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.south().west(), neighbors);

        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.up().north().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.up().north().west(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.up().south().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.up().south().west(), neighbors);

        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.down().north().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.down().north().west(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.down().south().east(), neighbors);
        addNeighborIfEligible(world, player, searchOrigin, this.blockPos, this.blockPos.down().south().west(), neighbors);


        return neighbors;
    }

    private static void addNeighborIfEligible(ClientWorld world, ClientPlayerEntity player, BlockPos searchOrigin, BlockPos pos, BlockPos neighborPos, List<Node> neighbors) {
        if (searchOrigin.isWithinDistance(neighborPos, MAX_SEARCH_RADIUS)) {
            if (NavigationUtility.canPlayerMoveBetween(world, player, pos, neighborPos)) {
                // Check downwards (up to fall damage dist)
                int y = 0;
                BlockPos neighbor = neighborPos;
                while (!NavigationUtility.canPlayerStandOnBlockBelow(world, player, neighbor) && NavigationUtility.canPathfindThrough(world, neighbor) && y > -3) {
                    neighbor = neighbor.down();
                    y--;
                }

                if (NavigationUtility.canPlayerStandOnBlockBelow(world, player, neighbor) && NavigationUtility.canPathfindThrough(world, neighbor))
                    neighbors.add(new Node(neighbor));
            } else {
                // Check upwards (1 block up)
                BlockPos neighbor = neighborPos.up();
                if (NavigationUtility.canPlayerMoveBetween(world, player, pos, neighbor) && NavigationUtility.canPlayerStandOnBlockBelow(world, player, neighbor))
                    neighbors.add(new Node(neighbor));
            }
        }
    }

    public int getWeight(ClientWorld world) {
        BlockState blockState = world.getBlockState(this.blockPos);
        if(blockState.getFluidState().isIn(FluidTags.WATER))
            return 2;
        else if(blockState.getBlock().equals(Blocks.COBWEB))
            return 100;
        else
            return 1;
    }
}
