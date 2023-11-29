package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.MinecraftClient;

public class InventorySurroundingContainersFeature extends TickingFeature<MinecraftClient> {
    public InventorySurroundingContainersFeature() {
        super("invSort", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        super.startAction(commandContext, client);
        System.out.println("CurrInvAction");
    }

    @Override
    public void onUpdate(MinecraftClient client) {

    }
}
