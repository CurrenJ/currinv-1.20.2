package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;

public class QuickStackFeature extends ScreenTickingFeature {
    public QuickStackFeature() {
        super("quickStack", true);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {}

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled)
    {
        super.setEnabled(commandContext, isEnabled);
        CurrInvClient.sorter.isQuickStackEnabled = isEnabled;
    }

    @Override
    public void onUpdate(ScreenTickingFeatureDto args) {}
}
