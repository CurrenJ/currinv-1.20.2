package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.command.ticking.ScreenTickingFeature;
import grill24.currinv.command.ticking.ScreenTickingFeatureDto;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;

public class SortingFeature extends ScreenTickingFeature {
    public SortingFeature() {
        super("sorting", true);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {}

    @Override
    public void setEnabled(CommandContext<?> commandContext, boolean isEnabled)
    {
        super.setEnabled(commandContext, isEnabled);
        CurrInvClient.sorter.isSortingEnabled = isEnabled;
    }

    @Override
    public void onUpdate(ScreenTickingFeatureDto args) {
        HandledScreen<?> handledScreen = ((HandledScreen<?>) args.screen());
        handledScreen.getScreenHandler().addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
                CurrInvClient.sorter.markDirty();
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {}
        });
        CurrInvClient.sorter.onUpdate(args.client(), handledScreen);
    }
}
