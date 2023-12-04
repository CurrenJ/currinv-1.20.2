package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

import java.util.ArrayList;

public class ConsolidateAndSortFeature extends Feature {

    public ConsolidateAndSortFeature() {
        super("consolidate", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        CurrInvClient.fullSuiteSorter.consolidateAndSort();
    }
}
