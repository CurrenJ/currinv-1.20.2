package grill24.currinv.mixin.client;

import grill24.currinv.CurrInvClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Shadow
    @Final
    private GameOptions settings;

    @Inject(at = @At("TAIL"), method = "tick(ZF)V")
    private void tick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (isMovementKeyPressed())
            CurrInvClient.navigator.reset();

        this.movementForward = CurrInvClient.navigator.getMovementForward(this.movementForward, settings);
        this.jumping = CurrInvClient.navigator.shouldJump(this.jumping);
    }

    @Unique
    public boolean isMovementKeyPressed() {
        return settings.forwardKey.isPressed()
                || settings.backKey.isPressed()
                || settings.leftKey.isPressed()
                || settings.rightKey.isPressed();
    }
}

