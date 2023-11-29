package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;

public interface ICommand {
    String getCommandText();

    void setEnabled(CommandContext<?> commandContext, boolean isEnabled);

    void toggleEnabled(CommandContext<?> commandContext);

    boolean isEnabled();
    boolean isToggleable();
}
