package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.command.ticking.ClientSlowTickingFeature;
import grill24.currinv.debug.DebugParticles;
import net.minecraft.client.MinecraftClient;

public class DebugParticlesFeature extends ClientSlowTickingFeature {

    public DebugParticlesFeature() {
        super("debugParticles", true, 60);
        setEnabled(null, true);
    }

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled) {
        super.setEnabled(commandContext, isEnabled);
        DebugParticles.isEnabled = isEnabled;
    }

    @Override
    public void onSlowUpdate(MinecraftClient args) {
        if(isEnabled)
            DebugParticles.spawnParticles(args);
    }
}
