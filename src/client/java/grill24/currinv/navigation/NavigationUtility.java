package grill24.currinv.navigation;

import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;

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
                return spaceForPlayerAboveToPos && noLava
                        && (hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal1)
                        || hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal2));
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

    public static boolean hasSpaceForPlayerToStandAtBlockPos(ClientWorld world, ClientPlayerEntity player, BlockPos blockPos) {
        return (canPathfindThrough(world, blockPos) || world.getBlockState(blockPos).getBlock() instanceof CarpetBlock) && canPathfindThrough(world, blockPos.up());
    }

    public static boolean isNotLava(ClientWorld world, BlockPos pos)
    {
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

    public static Vector2d getPitchAndYawToLookTowards(ClientPlayerEntity player, BlockPos pos)
    {
        Vec3d playerBodyPos = player.getPos();
        Vec3d playerHeadPos = new Vec3d(playerBodyPos.x, player.getEyeY(), playerBodyPos.z);
        Vec3d target = pos.toCenterPos();

        Vec3d v = new Vec3d(target.getX() - playerHeadPos.getX(), target.getY() - playerHeadPos.getY(), target.getZ() - playerHeadPos.getZ());
        double yaw = (float) (Math.toDegrees(Math.atan2(v.getZ(), v.getX())) - 90.0f);

        double pitch = Math.toDegrees(-Math.atan2(v.getY(), Math.sqrt(Math.pow(v.getX(), 2) + Math.pow(v.getZ(), 2))));

        return new Vector2d(normalizeAngle(pitch), normalizeAngle(yaw));
    }

    protected static float normalizeAngle(float yaw) {
        return (yaw + 180) % 360 - 180;
    }

    protected static double normalizeAngle(double yaw) {
        return (yaw + 180) % 360 - 180;
    }
}
