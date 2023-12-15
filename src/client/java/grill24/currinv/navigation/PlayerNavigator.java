package grill24.currinv.navigation;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.sizzlib.component.ClientTick;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandAction;
import grill24.sizzlib.component.CommandOption;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2d;

import java.util.List;
import java.util.Optional;

@Command("nav")
public class PlayerNavigator implements IClientPlayerController {

    private static final int MAX_MARKER_SEARCH_RADIUS = 512;

    AStarAsyncMinecraft pathfinder;

    public Block markerBlock = Blocks.CHISELED_NETHER_BRICKS;

    public NavigationData navigationData;

    public ClientPlayerController playerController;

    @CommandOption("debugParticles")
    public boolean debugParticlesEnabled = false;

    @CommandOption("debugLines")
    public boolean debugLinesEnabled = false;


    public PlayerNavigator() {
    }

    public boolean isNavigating() {
        return navigationData != null && navigationData.isNavigating();
    }

    public boolean isSearchingForPath() {
        return pathfinder != null && pathfinder.isStarted() && !pathfinder.isFinished();
    }


    @ClientTick
    public void onUpdate(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            if (navigationData != null && playerController != null) {
                boolean arrived = navigationData.onUpdate(client.world, client.player);
                playerController.onUpdate(client);
                if (arrived)
                    reset();
            } else {
                if (pathfinder != null && pathfinder.isStarted() && !pathfinder.isFinished()) {
                    pathfinder.onUpdate(client.world, client.player);
                    if (pathfinder.isFinished()) {
                        Optional<List<BlockPos>> path = pathfinder.tryGetPath();
                        path.ifPresent(this::startNavigationByPath);
                    }
                }
            }
        }
    }

    public boolean startNavigationToPosition(BlockPos start, BlockPos goal, boolean acceptHighestElevationAlternativeGoal, long executionTimeLimit) {
        pathfinder = new AStarAsyncMinecraft(acceptHighestElevationAlternativeGoal, executionTimeLimit);
        pathfinder.tryStartPathfinding(start, goal);

        return true;
    }

    public boolean startNavigationToPosition(BlockPos start, BlockPos goal, boolean acceptHighestElevationAlternativeGoal) {
        pathfinder = new AStarAsyncMinecraft(acceptHighestElevationAlternativeGoal, 20000);
        pathfinder.tryStartPathfinding(start, goal);

        return true;
    }

    public boolean startNavigationToPosition(BlockPos start, BlockPos goal) {
        pathfinder = new AStarAsyncMinecraft(false, 20000);
        pathfinder.tryStartPathfinding(start, goal);

        return true;
    }

    private void startNavigationByPath(List<BlockPos> path) {
        navigationData = new NavigationData(path);
        playerController = new LookAndAdvanceClientPlayerController(navigationData);
    }

    @CommandAction("toMarker")
    public void navigateToMarker(MinecraftClient client) {
        if (client.world != null && client.player != null) {
            navigateToMarker(client.world, client.player, markerBlock);
        }
    }

    private void navigateToMarker(ClientWorld world, ClientPlayerEntity player, Block block) {
        BlockPos playerPos = player.getBlockPos();

        if (navigateIfMarker(world, player, block, player.getBlockX(), player.getBlockY(), player.getBlockZ()))
            return;

        for (int cubeSideLength = 3; cubeSideLength <= MAX_MARKER_SEARCH_RADIUS; cubeSideLength += 2) {
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

            if (closestMarkerBlockPos != null && startNavigationToPosition(player.getBlockPos(), closestMarkerBlockPos.up(), false))
                break;
        }
    }

    private boolean isBlockAtPos(ClientWorld world, Block block, BlockPos pos) {
        return world.getBlockState(pos).getBlock().equals(block);
    }

    private BlockPos getCloserMarker(ClientWorld world, BlockPos playerPos, Block markerBlock, BlockPos pos, BlockPos lastClosestPos) {
        BlockPos posWithPlayerOffset = pos.add(playerPos);
        if (isBlockAtPos(world, markerBlock, posWithPlayerOffset)) {
            if (lastClosestPos == null)
                return posWithPlayerOffset;

            return playerPos.getManhattanDistance(posWithPlayerOffset) < playerPos.getManhattanDistance(lastClosestPos)
                    ? posWithPlayerOffset : lastClosestPos;
        }
        return lastClosestPos;
    }

    private boolean navigateIfMarker(ClientWorld world, ClientPlayerEntity player, Block block, int x, int y, int z) {
        BlockPos searchPos = new BlockPos(x, y, z).add(player.getBlockPos());
        if (world.getBlockState(searchPos).getBlock().equals(block)) {
            return startNavigationToPosition(player.getBlockPos(), searchPos.up(), false);
        }
        return false;
    }

    @CommandAction("escapeRope")
    public void navigateEscapeRope(MinecraftClient client) {
        if (client.world != null && client.player != null) {
            BlockPos playerPos = client.player.getBlockPos();

            for (int y = 320; y > playerPos.getY(); y--) {
                BlockPos searchPos = new BlockPos(playerPos.getX(), y, playerPos.getZ());
                Block block = client.world.getBlockState(searchPos).getBlock();
                if (!(client.world.isAir(searchPos) || block instanceof LeavesBlock)) {
                    startNavigationToPosition(client.player.getBlockPos(), searchPos.up(), true);
                    break;
                }
            }
        }
    }

    public void reset() {
        navigationData = null;
    }

    @Override
    public boolean shouldJump(boolean jumping) {
        if (playerController != null)
            return playerController.shouldJump(jumping);
        else
            return jumping;
    }

    @Override
    public float getMovementForward(float movementForward, GameOptions settings) {
        if (playerController != null)
            return playerController.getMovementForward(movementForward, settings);
        else
            return movementForward;
    }

    @Override
    public float getMovementSideways(float movementSideways, GameOptions settings) {
        if (playerController != null)
            return playerController.getMovementSideways(movementSideways, settings);
        else
            return movementSideways;
    }

    @Override
    public Vector2d getPitchAndYaw(Vector2d pitchAndYaw, ClientPlayerEntity player) {
        if (playerController != null)
            return playerController.getPitchAndYaw(pitchAndYaw, player);
        else
            return pitchAndYaw;
    }

    // ----- Debug -----
    @ClientTick(20)
    public void updateDebugParticles(MinecraftClient client) {
        if (debugLinesEnabled)
            DebugUtility.drawPathLines(navigationData);

        if (debugParticlesEnabled)
            DebugParticles.setDebugParticles(DebugParticles.NAVIGATION_PARTICLE_KEY, DebugUtility.getPathAheadOfPlayer(CurrInvClient.navigator.navigationData), ParticleTypes.END_ROD, DebugParticles.DebugParticleData.RenderType.PATH);
        else
            DebugParticles.clearDebugParticles(DebugParticles.NAVIGATION_PARTICLE_KEY);
    }
}
