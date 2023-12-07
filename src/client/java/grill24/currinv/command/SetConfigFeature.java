package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.client.MinecraftClient;

public class SetConfigFeature extends Feature {
    public SetConfigFeature() {
        super("config", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        DebugUtility.print(commandContext, CurrInvClient.config.toString());
    }
}
