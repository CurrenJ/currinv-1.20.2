package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.MinecraftClient;

public interface IAction {
    void startAction(CommandContext<?> commandContext, MinecraftClient client);
}
