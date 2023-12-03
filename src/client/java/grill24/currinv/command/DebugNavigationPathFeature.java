package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.command.ticking.ClientSlowTickingFeature;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

public class DebugNavigationPathFeature extends ClientSlowTickingFeature {
    public DebugNavigationPathFeature() {
        super("debugNav", true, 20);
    }

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled)
    {
        super.setEnabled(commandContext, isEnabled);
        if(isEnabled)
        {
            DebugParticles.setDebugParticles(DebugParticles.NAVIGATION_PARTICLE_KEY, DebugUtility.getNavigationParticles(CurrInvClient.navigator.navigationData), ParticleTypes.END_ROD, DebugParticles.DebugParticleData.RenderType.PATH);
        }
        else
        {
            DebugParticles.clearDebugParticles(DebugParticles.NAVIGATION_PARTICLE_KEY);
        }
    }

    @Override
    public void onSlowUpdate(MinecraftClient args) {
        if(isEnabled())
            DebugParticles.setDebugParticles(DebugParticles.NAVIGATION_PARTICLE_KEY, DebugUtility.getNavigationParticles(CurrInvClient.navigator.navigationData), ParticleTypes.END_ROD, DebugParticles.DebugParticleData.RenderType.PATH);
    }
}
