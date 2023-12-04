package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.command.ticking.ClientSlowTickingFeature;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;

public class DebugFullSuiteSorter extends ClientSlowTickingFeature {
    public DebugFullSuiteSorter() {
        super("debugFss", true, 1);
    }

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled)
    {
        super.setEnabled(commandContext, isEnabled);

        CurrInvClient.fullSuiteSorter.isDebugModeEnabled = isEnabled;
        if(CurrInvClient.fullSuiteSorter.isDebugParticlesEnabled) {
            CurrInvClient.fullSuiteSorter.updateDebugParticles(isEnabled);
        }
    }

    @Override
    public void onSlowUpdate(MinecraftClient args) {
        if(CurrInvClient.fullSuiteSorter.isDebugParticlesEnabled) {
            CurrInvClient.fullSuiteSorter.updateDebugParticles(isEnabled);
        }
    }
}
