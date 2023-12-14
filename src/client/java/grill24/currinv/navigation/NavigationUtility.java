package grill24.currinv.navigation;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.sorting.FullSuiteSorter;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class NavigationUtility {
    public static boolean canPlayerStandOnBlockBelow(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        return world.getBlockState(pos.down()).hasSolidTopSurface(world, pos, player)
                || world.getBlockState(pos.down()).getBlock() instanceof StairsBlock
                || world.getBlockState(pos.down()).getBlock() instanceof SlabBlock
                || world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER)
                || world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)
                || world.getBlockState(pos.down()).getBlock().equals(Blocks.DIRT_PATH);
    }

    public static boolean canPlayerMoveBetween(ClientWorld world, ClientPlayerEntity player, BlockPos from, BlockPos to) {
        boolean spaceForPlayerAboveToPos = hasSpaceForPlayerToStandAtBlockPos(world, player, to);
        if (to.getY() <= from.getY()) {
            if (isDirectlyAdjacent(from, to)) {
                return spaceForPlayerAboveToPos;
            } else {
                BlockPos diagonal1 = new BlockPos(from.getX(), from.getY(), to.getZ());
                BlockPos diagonal2 = new BlockPos(to.getX(), to.getY(), from.getZ());
                boolean noLava = isNotLava(world, diagonal1) && isNotLava(world, diagonal1.up())
                        && isNotLava(world, diagonal2) && isNotLava(world, diagonal2.up());
                boolean spaceForPlayerAboveDiagonal1 = hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal1);
                boolean spaceForPlayerAboveDiagonal2 = hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal2);
                return spaceForPlayerAboveToPos && noLava
                        && (spaceForPlayerAboveDiagonal1 && spaceForPlayerAboveDiagonal2);
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
                        && (hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal1)
                        || hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal2));
            }
        }
        return false;
    }

    public static List<Direction> getRelativeDirections(BlockPos from, BlockPos to) {
        List<Direction> directions = new ArrayList<>();
        Vec3i vi = to.subtract(from);
        directions.add(Direction.fromVector(vi.getX(), 0, 0));
        directions.add(Direction.fromVector(0, vi.getY(), 0));
        directions.add(Direction.fromVector(0, 0, vi.getZ()));
        return directions.stream().filter(Objects::nonNull).toList();
    }

    public static boolean canPlayerSeeBlockPosFromBlockPos(ClientPlayerInteractionManager interactionManager, ClientWorld world, ClientPlayerEntity player, BlockPos from, BlockPos see) {
        // Check if the see blockPos is blocked by any of the three blocks facing hte from pos that are immediately adjacent to it.
        // This eases the burden on the raycast. If the player is looking at a blockPos that is blocked by the three blocks immediately adjacent to it, the player cannot see the blockPos.
        // EDIT: Is this still worth it? The random offset handles these cases nicely. But this still is probably more efficient.
        List<Direction> directions = getRelativeDirections(see, from);
        boolean blockedByImmediatelyAdjacentBlocks = true;
        List<BlockPos> adjacentBlocksToCheck = new ArrayList<>();
        for (Direction direction : directions) {
            adjacentBlocksToCheck.add(see.offset(direction));
        }

        for (BlockPos blockPos : adjacentBlocksToCheck) {
            if (world.isAir(blockPos) || world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                blockedByImmediatelyAdjacentBlocks = false;
            }
        }
        if (blockedByImmediatelyAdjacentBlocks)
            return false;

        if(from.equals(player.getBlockPos()))
            System.out.println("ASDSA");

        // This offset helps us avoid hitting the vertices of block collision boxes.
        // IE when three blocks meet at one corner, how do we stop the raycast from perfectly passing through that corner
        double offsetAmount = 0.1;
        Random random = new Random(from.hashCode());
        Vec3d offsetVec = new Vec3d(random.nextDouble(-offsetAmount, offsetAmount), random.nextDouble(-offsetAmount, offsetAmount), random.nextDouble(-offsetAmount, offsetAmount));

        // Check if there are any blocks between player and blockPos that would block the player's line of sight.
        Vec3d fromVec = from.toCenterPos().subtract(0, 0.5, 0).add(0, player.getEyeHeight(player.getPose()), 0).add(offsetVec);
        Vec3d seeVec = getBlockFaceToLookTowards(world, BlockPos.ofFloored(fromVec).toCenterPos(), see.toCenterPos());


        // Get the vector from the player to the blockPos.
        Vec3d rayVec = seeVec.subtract(fromVec);
        double reachDistance = interactionManager.getReachDistance();


        List<BlockPos> blockPosAlongRay = new ArrayList<>();
        //Iterate through each block along the vector from the player to the blockPos.
        double stepSize = 0.05;
        for (double i = 0; i < reachDistance + stepSize; i += stepSize) {
            Vec3d blockPosAlongVector = fromVec.add(rayVec.normalize().multiply(i));
            BlockPos blockPosAlongVectorBlockPos = new BlockPos((int) Math.floor(blockPosAlongVector.getX()), (int) Math.floor(blockPosAlongVector.getY()), (int) Math.floor(blockPosAlongVector.getZ()));
            if(blockPosAlongRay.isEmpty() || blockPosAlongRay.get(blockPosAlongRay.size()-1) != blockPosAlongVectorBlockPos)
                blockPosAlongRay.add(blockPosAlongVectorBlockPos);
        }

        // Check each block pos along raycast to see if we hit it's collision shape
        for(BlockPos blockPos : blockPosAlongRay)
        {
            if (blockPos.equals(see)) {
                if(CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.ALL || CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.SUCCESS)
                    CurrInvClient.currInvDebugRenderer.addLine(fromVec, seeVec, 10000, CurrInvDebugRenderer.GREEN);

                return true;
            }

            VoxelShape voxelShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
            BlockHitResult blockHitResult = voxelShape.raycast(fromVec, seeVec, blockPos);
            if(!voxelShape.isEmpty() || blockHitResult != null) {
                if(CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.ALL || CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.FAIL)
                    CurrInvClient.currInvDebugRenderer.addLine(fromVec, seeVec, 10000, CurrInvDebugRenderer.RED);

                return false;
            }
        }
        if(CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.ALL || CurrInvClient.fullSuiteSorter.debugRays == FullSuiteSorter.DebugRays.FAIL)
            CurrInvClient.currInvDebugRenderer.addLine(fromVec, seeVec, 10000, CurrInvDebugRenderer.GREEN);

        return false;
    }

    public static boolean hasSpaceForPlayerToStandAtBlockPos(ClientWorld world, ClientPlayerEntity player, BlockPos blockPos) {
        return (canPathfindThrough(world, blockPos) || world.getBlockState(blockPos).getBlock() instanceof CarpetBlock) && canPathfindThrough(world, blockPos.up());
    }

    public static boolean hasSpaceForPlayerToStandAtBlockPos(ClientWorld world, ClientPlayerEntity player, BlockPos blockPos, int additionalHeadClearance) {
        boolean hasSpaceForPlayerToStand = (canPathfindThrough(world, blockPos) || world.getBlockState(blockPos).getBlock() instanceof CarpetBlock) && canPathfindThrough(world, blockPos.up());
        for (int i = 1; i <= additionalHeadClearance; i++) {
            hasSpaceForPlayerToStand &= canPathfindThrough(world, blockPos.up(i));
        }

        return hasSpaceForPlayerToStand;
    }

    public static boolean isNotLava(ClientWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getFluidState().isIn(FluidTags.LAVA);
    }

    public static boolean isDirectlyAdjacent(BlockPos from, BlockPos to) {
        return from.getX() == to.getX() || from.getZ() == to.getZ();
    }

    public static boolean canPathfindThrough(ClientWorld world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        boolean landNav = blockState.canPathfindThrough(world, pos, NavigationType.LAND);
        // Navigate through water but not waterlogged blocks
        boolean waterNav = blockState.canPathfindThrough(world, pos, NavigationType.WATER) && blockState.getCollisionShape(world, pos).isEmpty();
        boolean notLava = !blockState.getBlock().equals(Blocks.LAVA);

        // Breaks on carpets - better code here!
        boolean noCollision = blockState.getCollisionShape(world, pos).isEmpty();

        return (landNav || waterNav) && notLava;
    }

    public static List<BlockPos> getCardinals(BlockPos pos) {
        List<BlockPos> cardinals = new ArrayList<>();
        cardinals.add(pos.north());
        cardinals.add(pos.south());
        cardinals.add(pos.east());
        cardinals.add(pos.west());
        return cardinals;
    }

    public static Vector2d getPitchAndYawToLookTowardsBlockFace(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        Vec3d playerBodyPos = player.getPos();
        Vec3d playerHeadPos = new Vec3d(playerBodyPos.x, player.getEyeY(), playerBodyPos.z);

        Vec3d target = getBlockFaceToLookTowards(world, player, pos);

        Vec3d v = new Vec3d(target.getX() - playerHeadPos.getX(), target.getY() - playerHeadPos.getY(), target.getZ() - playerHeadPos.getZ());
        double yaw = (float) (Math.toDegrees(Math.atan2(v.getZ(), v.getX())) - 90.0f);

        double pitch = Math.toDegrees(-Math.atan2(v.getY(), Math.sqrt(Math.pow(v.getX(), 2) + Math.pow(v.getZ(), 2))));

        return new Vector2d(normalizeAngle(pitch), normalizeAngle(yaw));
    }

    public static Vec3d getBlockFaceToLookTowards(ClientWorld world, ClientPlayerEntity player, BlockPos to) {
        return getBlockFaceToLookTowards(world, player.getEyePos(), to.toCenterPos());
    }

    public static Vec3d getBlockFaceToLookTowards(ClientWorld world, Vec3d from, Vec3d to) {
        Vec3d target = to;
        Vec3d vi = target.subtract(from);
        Direction dirFacingPos;

        BlockPos fromBlockPos = new BlockPos((int) Math.floor(from.getX()), (int) Math.floor(from.getY()), (int) Math.floor(from.getZ()));
        BlockPos toBlockPos = new BlockPos((int) Math.floor(to.getX()), (int) Math.floor(to.getY()), (int) Math.floor(to.getZ()));

        // Only want up or down if directly above or below block!
        if (fromBlockPos.getX() == toBlockPos.getX() && fromBlockPos.getZ() == toBlockPos.getZ()) {
            dirFacingPos = from.getY() < to.getY() ? Direction.UP : Direction.DOWN;
        } else {
            // If the player is not directly above or below the block, we want to look towards the block face that is closest to the player (and is not blocked by a block).
            Direction dirFacingX = Direction.fromVector((int) Math.ceil(vi.getX()), 0, 0);
            boolean isXFaceAir = dirFacingX != null && world.isAir(toBlockPos.offset(dirFacingX.getOpposite()));

            Direction dirFacingZ = Direction.fromVector(0, 0, (int) Math.ceil(vi.getZ()));
            boolean isZFaceAir = dirFacingZ != null && world.isAir(toBlockPos.offset(dirFacingZ.getOpposite()));

            Direction dirFacingY = Direction.fromVector(0, (int) Math.ceil(vi.getY()), 0);
            boolean isYFaceAir = dirFacingY != null && world.isAir(toBlockPos.offset(dirFacingY.getOpposite()));

            // If both faces are air, we want to look towards the face that is closest to the player.
            if (isXFaceAir && isZFaceAir)
                dirFacingPos = vi.getX() > vi.getZ() ? dirFacingX : dirFacingZ;
            else if (isXFaceAir)
                dirFacingPos = dirFacingX;
            else if (isZFaceAir)
                dirFacingPos = dirFacingZ;
            else if (isYFaceAir)
                dirFacingPos = dirFacingY;
            else
                dirFacingPos = null;
        }

        if (dirFacingPos != null)
            target = target.offset(dirFacingPos.getOpposite(), 0.5 - 1 / 16.0);

        return target;
    }

    protected static float normalizeAngle(float yaw) {
        return (yaw + 180) % 360 - 180;
    }

    protected static double normalizeAngle(double yaw) {
        return (yaw + 180) % 360 - 180;
    }

    public static float angleLerp(float a, float b, float t) {
        float normalizedA = NavigationUtility.normalizeAngle(a);
        float normalizedB = NavigationUtility.normalizeAngle(b);
        return NavigationUtility.normalizeAngle(normalizedA + (NavigationUtility.normalizeAngle(normalizedB - normalizedA) * t));
    }
}
