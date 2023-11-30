package grill24.currinv.navigation;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector2d;

public abstract class ClientPlayerController implements IClientPlayerController {

    NavigationData navigationData;

    public ClientPlayerController(NavigationData navigationData)
    {
        this.navigationData = navigationData;
    }

    @Override
    public abstract void onUpdate(ClientWorld world, ClientPlayerEntity player);

    @Override
    public abstract boolean shouldJump(boolean jumping);

    @Override
    public abstract float getMovementForward(float movementForward, GameOptions settings);
    @Override
    public abstract float getMovementSideways(float movementSideways, GameOptions settings);

    @Override
    public abstract Vector2d getPitchAndYaw(Vector2d pitchAndYaw, ClientPlayerEntity player);

    protected float angleLerp(float a, float b, float t)
    {
        float normalizedA = NavigationUtility.normalizeAngle(a);
        float normalizedB = NavigationUtility.normalizeAngle(b);
        return NavigationUtility.normalizeAngle(normalizedA + (NavigationUtility.normalizeAngle(normalizedB - normalizedA) * t));
    }

    public boolean isMovementKeyPressed(GameOptions settings)
    {
        return settings.forwardKey.isPressed()
                || settings.backKey.isPressed()
                || settings.leftKey.isPressed()
                || settings.rightKey.isPressed();
    }
}
