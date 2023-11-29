package grill24.currinv.navigation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2d;

import java.util.List;
import java.util.Optional;

public class PlayerNavigator implements IClientPlayerController {

    private static final int MAX_MARKER_SEARCH_RADIUS = 512;
    public boolean shouldStartNavigation;

    AStarAsyncMinecraft pathfinder;

    public enum NavigationMode { TO_MARKER, ESCAPE_ROPE }
    public NavigationMode navigationMode;
    public Block markerBlock = Blocks.CHISELED_NETHER_BRICKS;

    public NavigationData navigationData;

    public ClientPlayerController playerController;

    public PlayerNavigator()
    {
    }

    public boolean isNavigating()
    {
        return navigationData != null && navigationData.isNavigating();
    }


    public void onUpdate(ClientWorld world, ClientPlayerEntity player)
    {
        if(navigationData != null && playerController != null && player != null) {
            boolean arrived = navigationData.onUpdate(world, player);
            playerController.onUpdate(world, player);
            if(arrived)
               reset();
        } else {
            if(pathfinder != null && pathfinder.isStarted() && !pathfinder.isFinished()) {
                pathfinder.onUpdate(world, player);
                if(pathfinder.isFinished()) {
                    Optional<List<BlockPos>> path = pathfinder.tryGetPath();
                    path.ifPresent(this::startNavigationByPath);
                }
            }
        }
    }

    public void startNavigation(ClientWorld world, ClientPlayerEntity player, NavigationMode navigationMode)
    {
        reset();
        switch (navigationMode)
        {
            case TO_MARKER -> navigateToMarker(world, player, markerBlock);
            case ESCAPE_ROPE -> navigateEscapeRope(world, player);
        }
    }

    public boolean startNavigationToPosition(BlockPos start, BlockPos goal, boolean acceptHighestElevationAlternativeGoal)
    {
        pathfinder = new AStarAsyncMinecraft(acceptHighestElevationAlternativeGoal);
        pathfinder.tryStartPathfinding(start, goal);

        return true;
    }

    private void startNavigationByPath(List<BlockPos> path)
    {
        navigationData = new NavigationData(path);
        playerController = new LookAndAdvanceClientPlayerController(navigationData);
    }

    public void navigateToMarker(ClientWorld world, ClientPlayerEntity player, Block block) {
        BlockPos playerPos = player.getBlockPos();

        if(navigateIfMarker(world, player, block, player.getBlockX(), player.getBlockY(), player.getBlockZ()))
            return;

        for (int cubeSideLength = 3; cubeSideLength <= MAX_MARKER_SEARCH_RADIUS; cubeSideLength+=2) {
            // Each iteration of this outerloop is O(n^2) where n is cubeSideLength. It's better than the other solutions.

            // Shift cube to center it
            int offset = -Math.floorDiv(cubeSideLength, 2);

            BlockPos closestMarkerBlockPos = null;
            // Iterate through the faces of the cube
            for (int x = offset; x < cubeSideLength + offset; x += cubeSideLength - 1) {
                for (int y = offset; y < cubeSideLength + offset; y++) {
                    for (int z = offset; z < cubeSideLength + offset; z++) {
                        closestMarkerBlockPos = getCloserMarker(world, playerPos, block, new BlockPos(x, y, z), closestMarkerBlockPos);
                        closestMarkerBlockPos = getCloserMarker(world, playerPos, block, new BlockPos(y, x, z), closestMarkerBlockPos);
                        closestMarkerBlockPos = getCloserMarker(world, playerPos, block, new BlockPos(y, z, x), closestMarkerBlockPos);
                    }
                }
            }

            if(closestMarkerBlockPos != null && startNavigationToPosition(player.getBlockPos(), closestMarkerBlockPos.up(), false))
                break;
        }
    }

    private boolean isBlockAtPos(ClientWorld world, Block block, BlockPos pos)
    {
        return world.getBlockState(pos).getBlock().equals(block);
    }

    private BlockPos getCloserMarker(ClientWorld world, BlockPos playerPos, Block markerBlock, BlockPos pos, BlockPos lastClosestPos)
    {
        BlockPos posWithPlayerOffset = pos.add(playerPos);
        if(isBlockAtPos(world, markerBlock, posWithPlayerOffset))
        {
            if(lastClosestPos == null)
                return posWithPlayerOffset;

            return playerPos.getManhattanDistance(posWithPlayerOffset) < playerPos.getManhattanDistance(lastClosestPos)
                    ? posWithPlayerOffset : lastClosestPos;
        }
        return lastClosestPos;
    }

    private boolean navigateIfMarker(ClientWorld world, ClientPlayerEntity player, Block block, int x, int y, int z)
    {
        BlockPos searchPos = new BlockPos(x, y, z).add(player.getBlockPos());
        if (world.getBlockState(searchPos).getBlock().equals(block)) {
            return startNavigationToPosition(player.getBlockPos(), searchPos.up(), false);
        }
        return false;
    }

    private void navigateEscapeRope(ClientWorld world, ClientPlayerEntity player)
    {
        BlockPos playerPos = player.getBlockPos();

        for(int y = 320; y > playerPos.getY(); y--)
        {
            BlockPos searchPos = new BlockPos(playerPos.getX(), y, playerPos.getZ());
            Block block = world.getBlockState(searchPos).getBlock();
            if(!(world.isAir(searchPos) || block instanceof LeavesBlock))
            {
                startNavigationToPosition(player.getBlockPos(), searchPos.up(), true);
                break;
            }
        }
    }

    public void reset()
    {
        navigationData = null;
    }

    @Override
    public boolean shouldJump(boolean jumping) {
        if(playerController != null)
            return playerController.shouldJump(jumping);
        else
            return jumping;
    }

    @Override
    public float getMovementForward(float movementForward, GameOptions settings) {
        if(playerController != null)
            return playerController.getMovementForward(movementForward, settings);
        else
            return movementForward;
    }

    @Override
    public float getMovementSideways(float movementSideways, GameOptions settings) {
        if(playerController != null)
            return playerController.getMovementSideways(movementSideways, settings);
        else
            return movementSideways;
    }

    @Override
    public Vector2d getPitchAndYaw(Vector2d pitchAndYaw, ClientPlayerEntity player) {
        if(playerController != null)
            return playerController.getPitchAndYaw(pitchAndYaw, player);
        else
            return pitchAndYaw;
    }
}
