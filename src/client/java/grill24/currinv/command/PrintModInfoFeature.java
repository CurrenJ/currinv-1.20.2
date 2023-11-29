package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class PrintModInfoFeature extends Feature {
    public PrintModInfoFeature() {
        super("currInv", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        if(commandContext.getSource() instanceof FabricClientCommandSource)
        {
            ((FabricClientCommandSource) commandContext.getSource()).sendFeedback(Text.literal("CurrInv is a mod developed by Curren Jeandell."));
        }
    }
}
