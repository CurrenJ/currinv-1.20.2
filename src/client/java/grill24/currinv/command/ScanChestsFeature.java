package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;

public class ScanChestsFeature extends Feature {

    public ScanChestsFeature() {
        super("scan", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        CurrInvClient.fullSuiteSorter.analyzeNearbyContainers(client);
    }
}
