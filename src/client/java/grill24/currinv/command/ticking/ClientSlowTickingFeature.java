package grill24.currinv.command.ticking;

import net.minecraft.client.MinecraftClient;

public abstract class ClientSlowTickingFeature extends ClientTickingFeature {

    private final int slowTickRate;
    private int ticksRemaining = 0;
    public ClientSlowTickingFeature(String commandText, boolean isToggleable, int slowTickRate) {
        super(commandText, isToggleable);
        this.slowTickRate = slowTickRate;
    }

    @Override
    public void onUpdate(MinecraftClient args) {
        if(ticksRemaining == 0)
        {
            ticksRemaining = slowTickRate;
            onSlowUpdate(args);
        }
        else
        {
            ticksRemaining--;
        }
    }

    public abstract void onSlowUpdate(MinecraftClient args);
}
