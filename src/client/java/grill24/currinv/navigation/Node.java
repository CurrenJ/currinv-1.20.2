package grill24.currinv.navigation;

import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
            if (canPlayerMoveBetween(world, player, pos, neighborPos)) {
                // Check downwards (up to fall damage dist)
                int y = 0;
                BlockPos neighbor = neighborPos;
                while (!canPlayerStandOnBlockBelow(world, player, neighbor) && canPathfindThrough(world, neighbor) && y > -3) {
                    neighbor = neighbor.down();
                    y--;
                }

                if (canPlayerStandOnBlockBelow(world, player, neighbor) && canPathfindThrough(world, neighbor))
                    neighbors.add(new Node(neighbor));
            } else {
                // Check upwards (1 block up)
                BlockPos neighbor = neighborPos.up();
                if (canPlayerMoveBetween(world, player, pos, neighbor) && canPlayerStandOnBlockBelow(world, player, neighbor))
                    neighbors.add(new Node(neighbor));
            }
        }
    }

    private static boolean canPlayerStandOnBlockBelow(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        return world.getBlockState(pos.down()).hasSolidTopSurface(world, pos, player)
                || world.getBlockState(pos.down()).getBlock() instanceof StairsBlock
                || world.getBlockState(pos.down()).getBlock() instanceof SlabBlock
                || world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER)
                || world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)
                || world.getBlockState(pos.down()).getBlock().equals(Blocks.DIRT_PATH);
    }

    private static boolean canPlayerMoveBetween(ClientWorld world, ClientPlayerEntity player, BlockPos from, BlockPos to) {
        boolean spaceForPlayerAboveToPos = hasSpaceForPlayerAboveBlockPos(world, player, to);
        if (to.getY() <= from.getY()) {
            if (isDirectlyAdjacent(from, to)) {
                return spaceForPlayerAboveToPos;
            } else {
                BlockPos diagonal1 = new BlockPos(from.getX(), from.getY(), to.getZ());
                BlockPos diagonal2 = new BlockPos(to.getX(), to.getY(), from.getZ());
                boolean noLava = isNotLava(world, diagonal1) && isNotLava(world, diagonal1.up())
                        && isNotLava(world, diagonal2) && isNotLava(world, diagonal2.up());
                return spaceForPlayerAboveToPos && noLava
                        && (hasSpaceForPlayerAboveBlockPos(world, player, diagonal1)
                        || hasSpaceForPlayerAboveBlockPos(world, player, diagonal2));
            }
        } else if (to.getY() == from.getY() + 1) {
            boolean canJumpTo = canPathfindThrough(world, from.up(2));
            if (isDirectlyAdjacent(from, to)) {
                return spaceForPlayerAboveToPos && canJumpTo;
            } else {
                BlockPos diagonal1 = new BlockPos(from.getX(), to.getY(), to.getZ());
                BlockPos diagonal2 = new BlockPos(to.getX(), to.getY(), from.getZ());
                boolean noLava = isNotLava(world, diagonal1) && isNotLava(world, diagonal1.up()) && isNotLava(world, diagonal1.up().up())
                        && isNotLava(world, diagonal2) && isNotLava(world, diagonal2.up()) && isNotLava(world, diagonal2.up().up());
                return spaceForPlayerAboveToPos && canJumpTo && noLava
                        && (hasSpaceForPlayerAboveBlockPos(world, player, diagonal1)
                        || hasSpaceForPlayerAboveBlockPos(world, player, diagonal2));
            }
        }
        return false;
    }

    public static boolean hasSpaceForPlayerAboveBlockPos(ClientWorld world, ClientPlayerEntity player, BlockPos blockPos) {
        return (canPathfindThrough(world, blockPos) || world.getBlockState(blockPos).getBlock() instanceof CarpetBlock) && canPathfindThrough(world, blockPos.up());
    }

    public static boolean isNotLava(ClientWorld world, BlockPos pos)
    {
        return !world.getBlockState(pos).getFluidState().isIn(FluidTags.LAVA);
    }

    public static boolean isDirectlyAdjacent(BlockPos from, BlockPos to) {
        return from.getX() == to.getX() || from.getZ() == to.getZ();
    }

    private static boolean canPathfindThrough(ClientWorld world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        boolean landNav = blockState.canPathfindThrough(world, pos, NavigationType.LAND);
        // Navigate through water but not waterlogged blocks
        boolean waterNav = blockState.canPathfindThrough(world, pos, NavigationType.WATER) && blockState.getCollisionShape(world, pos).isEmpty();
        boolean notLava = !blockState.getBlock().equals(Blocks.LAVA);

        // Breaks on carpets - better code here!
        boolean noCollision = blockState.getCollisionShape(world, pos).isEmpty();

        return (landNav || waterNav) && notLava;
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
