package grill24.currinv.navigation;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

public class LookAndAdvanceClientPlayerController extends ClientPlayerController {
    public Vector2d desiredPitchAndYaw;
    public boolean shouldJump;
    public boolean isOnGround;
    public boolean isInWater;
    public boolean isNextNodeBelowCurrentNode;
    public boolean isNextNodeBelowPlayer;

    public float movementSpeedForward;


    public LookAndAdvanceClientPlayerController(NavigationData navigationData) {
        super(navigationData);
        desiredPitchAndYaw = new Vector2d();
    }

    @Override
    public void onUpdate(MinecraftClient client) {
        if (CurrInvClient.navigator.isNavigating() && client.player != null && client.world != null) {
            ClientWorld world = client.world;
            ClientPlayerEntity player = client.player;

            isOnGround = player.isOnGround();

            // player.isSubmergedInWater() || world.getBlockState(player.getBlockPos()).getFluidState().isIn(FluidTags.WATER)
            isInWater = world.getBlockState(navigationData.getCurrentNode()).getFluidState().isIn(FluidTags.WATER) || world.getBlockState(navigationData.getNextNode()).getFluidState().isIn(FluidTags.WATER) || world.getBlockState(player.getBlockPos()).getFluidState().isIn(FluidTags.WATER);

            isNextNodeBelowCurrentNode = navigationData.getNextNode().getY() < navigationData.getCurrentNode().getY();
            isNextNodeBelowPlayer = isNextNodeBelowCurrentNode
                    && player.getBlockX() == navigationData.getNextNode().getX()
                    && player.getBlockZ() == navigationData.getNextNode().getZ();

            shouldJump = calculateShouldJump(world, player);
            desiredPitchAndYaw = calculateDesiredPitchAndYaw(player);
            movementSpeedForward = calculateMovementForward(world, player);

            player.getAbilities().flying = false;
            //player.setSprinting(!shouldJump && isOnGround && navigationData.getNextNode().getY() == navigationData.getCurrentNode().getY());
        }
    }


    @Override
    public boolean shouldJump(boolean jumping) {
        if (CurrInvClient.navigator.isNavigating())
            return shouldJump;
        else
            return jumping;
    }

    private boolean calculateShouldJump(ClientWorld world, ClientPlayerEntity player) {
        BlockPos currentNode = navigationData.getCurrentNode();
        BlockPos nextNode = navigationData.getNextNode();

        boolean nextNodeIsAbove = nextNode.getY() > player.getPos().getY() + player.getStepHeight() || nextNode.getY() > currentNode.getY();
        boolean shouldAscendInWater = isInWater && nextNode.getY() > player.getPos().getY();
        boolean shouldAscendOnLadder = world.getBlockState(currentNode).getBlock().equals(Blocks.LADDER) && nextNode.getY() > player.getPos().getY();

        boolean isDiagonal = !NavigationUtility.isDirectlyAdjacent(currentNode, nextNode);
        if (isDiagonal) {
            BlockPos diagonal1 = new BlockPos(currentNode.getX(), currentNode.getY(), nextNode.getZ());
            BlockPos diagonal2 = new BlockPos(nextNode.getX(), currentNode.getY(), currentNode.getZ());
            if (!nextNodeIsAbove) {
                // Logic for jumping over diagonal waist-high "hurdles"
                nextNodeIsAbove = !NavigationUtility.canPathfindThrough(world, diagonal1) && !NavigationUtility.canPathfindThrough(world, diagonal2);
            } else {
                // Weird logic for specific cave escaping
                boolean isPitfall = NavigationUtility.hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal1, 1) ||
                        NavigationUtility.hasSpaceForPlayerToStandAtBlockPos(world, player, diagonal2, 1);
                if (isPitfall) {
                    boolean diagonalPitfallShouldJump = !player.getBlockPos().equals(navigationData.getCurrentNode());
                    nextNodeIsAbove = diagonalPitfallShouldJump;
                } else {
                    boolean hasHeadroom = world.getBlockState(player.getBlockPos().up()).getCollisionShape(world, player.getBlockPos().up()).isEmpty()
                            && currentNode.toCenterPos().subtract(0, 0.5, 0).distanceTo(player.getPos()) < 0.25f;
                    nextNodeIsAbove = hasHeadroom;
                }
            }
        }

        if (isInWater)
            return shouldAscendInWater;

        return nextNodeIsAbove || shouldAscendInWater || shouldAscendOnLadder;
    }

    @Override
    public float getMovementForward(float movementForward, GameOptions settings) {
        if (CurrInvClient.navigator.isNavigating()) {
            return this.movementSpeedForward;
        }
        return movementForward;
    }

    private float calculateMovementForward(ClientWorld world, ClientPlayerEntity player) {
        float speed = 1.0f;

        // Falling
        if (!isOnGround && !shouldJump && isNextNodeBelowCurrentNode) {
            if (!isInWater)
                speed *= 0;
            else if (isNextNodeBelowPlayer)
                speed *= 0;
        }

        BlockPos from = navigationData.getCurrentNode();
        BlockPos to = navigationData.getNextNode();


        if (from.getX() == to.getX() && from.getZ() == to.getZ() && player.getBlockX() == to.getX() && player.getBlockZ() == to.getZ())
            speed *= 0.25f;
        else if (navigationData.getNextNode().getY() < navigationData.getCurrentNode().getY() && !world.getBlockState(to).getFluidState().isIn(FluidTags.WATER))
            speed *= 0.5f;

        return speed;
    }

    @Override
    public float getMovementSideways(float movementSideways, GameOptions settings) {
        if (CurrInvClient.navigator.isNavigating()) {
            return 0;
        } else {
            return movementSideways;
        }
    }

    @Override
    public Vector2d getPitchAndYaw(Vector2d pitchAndYaw, ClientPlayerEntity player) {
        if (CurrInvClient.navigator.isNavigating()) {
            float lerpFactor = 0.1f;

            float yaw = NavigationUtility.angleLerp(player.getYaw(), (float) desiredPitchAndYaw.y, lerpFactor);
            float pitch = NavigationUtility.angleLerp(player.getPitch(), (float) desiredPitchAndYaw.x, lerpFactor);

            return new Vector2d(pitch, yaw);
        } else {
            return pitchAndYaw;
        }
    }

    private Vector2d calculateDesiredPitchAndYaw(ClientPlayerEntity player) {
        // This is to stop the yaw from rapidly spinning when we are directly above the next node but haven't reached it.
        // E.g. sinking in water
//        if(!isOnGround && !shouldJump && isNextNodeBelowCurrentNode)
//            return desiredPitchAndYaw;

        Vec3d playerBodyPos = player.getPos();
        Vec3d playerHeadPos = new Vec3d(playerBodyPos.x, player.getEyeY(), playerBodyPos.z);
        Vec3d target = navigationData.getNextNode().toCenterPos().subtract(0, 0.5, 0);

        Vec3d v = new Vec3d(target.getX() - playerHeadPos.getX(), target.getY() - playerHeadPos.getY(), target.getZ() - playerHeadPos.getZ());
        double yaw = (float) (Math.toDegrees(Math.atan2(v.getZ(), v.getX())) - 90.0f);

        double pitch = Math.toDegrees(-Math.atan2(v.getY(), Math.sqrt(Math.pow(v.getX(), 2) + Math.pow(v.getZ(), 2))));
//        if(!shouldJump && isOnGround) {
        pitch = Math.max(-15, pitch);
        pitch = Math.min(15, pitch);
//        }

//        System.out.println("target: " + navigationData.getNextNode() + " | " + "player: " + player.getPos() + " | " + yaw);

        return new Vector2d(pitch, yaw);
    }
}
