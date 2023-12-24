package grill24.currinv.mixin.client;

import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin extends ClientCommonNetworkHandler {

    protected ClientPlayNetworkHandlerMixin(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
    }

    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        CurrInvClient.setBiomeAccessSeed(packet.commonPlayerSpawnInfo().seed());
    }

    @Inject(method = "onPlayerRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;joinWorld(Lnet/minecraft/client/world/ClientWorld;)V"))
    private void onPlayerRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        CurrInvClient.playerTools.setLastPosPLayerTravelledThroughPortalTo(MinecraftClient.getInstance().player.getBlockPos());
        CurrInvClient.playerTools.setLastDimension(packet.commonPlayerSpawnInfo().dimensionType().getValue());
    }
}
