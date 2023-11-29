package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class Command implements ICommand {
    private final String commandText;
    private boolean isEnabled;
    private final boolean isToggleable;
    public Command(String commandText, boolean isToggleable)
    {
        this.commandText = commandText;
        this.isToggleable = isToggleable;
    }

    @Override
    public String getCommandText() {
        return this.commandText;
    }

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Override
    public void toggleEnabled(CommandContext<?> commandContext) {
        setEnabled(commandContext, !isEnabled);
        if(commandContext.getSource() instanceof FabricClientCommandSource)
        {
            ((FabricClientCommandSource)commandContext.getSource()).sendFeedback(Text.of(isEnabled ? "Enabled" : "Disabled"));
        }
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public boolean isToggleable() {
        return isToggleable;
    }
}
