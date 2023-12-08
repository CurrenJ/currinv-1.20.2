package grill24.currinv.navigation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import org.joml.Vector2d;

public interface IClientPlayerController {
    void onUpdate(MinecraftClient client);

    boolean shouldJump(boolean jumping);

    float getMovementForward(float movementForward, GameOptions settings);

    float getMovementSideways(float movementSideways, GameOptions settings);

    Vector2d getPitchAndYaw(Vector2d pitchAndYaw, ClientPlayerEntity player);

}
