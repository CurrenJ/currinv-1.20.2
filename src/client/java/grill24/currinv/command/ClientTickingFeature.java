package grill24.currinv.command;

import net.minecraft.client.MinecraftClient;

public abstract class ClientTickingFeature extends TickingFeature<MinecraftClient> {
    public ClientTickingFeature(String commandText, boolean isToggleable) {
        super(commandText, isToggleable);
    }
}
