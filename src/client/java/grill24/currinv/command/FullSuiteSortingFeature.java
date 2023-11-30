package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;

public class FullSuiteSortingFeature extends Feature {

    public FullSuiteSortingFeature() {
        super("fullSort", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        CurrInvClient.fullSuiteSorter.tryStartFullSuiteSort(client);
    }
}
