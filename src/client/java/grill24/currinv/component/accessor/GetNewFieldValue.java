package grill24.currinv.component.accessor;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface GetNewFieldValue<S> {
    // Returns the new field value to be set, given a command context. This is used for setting the value of a field when a command is run.
    Object run(CommandContext<S> context) throws CommandSyntaxException;
}
