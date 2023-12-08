package grill24.currinv.mixin.client;

import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(at = @At("TAIL"), method = "updateMouse()V")
    private void updateMouse(CallbackInfo ci) {
        assert this.client.player != null;

        Vector2d pitchAndYaw = CurrInvClient.navigator.getPitchAndYaw(
                new Vector2d(this.client.player.getPitch(), this.client.player.getYaw()), this.client.player);
        this.client.player.setYaw((float) pitchAndYaw.y);
        this.client.player.setPitch((float) pitchAndYaw.x);
    }
}

