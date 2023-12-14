package grill24.currinv.component;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

@FunctionalInterface
public interface LiteralArgumentBuilderSupplier {
    // Returns the new field value to be set, given a command context. This is used for setting the value of a field when a command is run.
    LiteralArgumentBuilder<FabricClientCommandSource> run(String commandKey);
}
