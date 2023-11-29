package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public class SortingToggle extends TickingFeature<Screen> {
    public SortingToggle() {
        super("sorting", true);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {}

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled)
    {
        super.setEnabled(commandContext, isEnabled);
        CurrInvClient.sorter.isEnabled = isEnabled;
    }

    @Override
    public void onUpdate(Screen args) {

    }
}
