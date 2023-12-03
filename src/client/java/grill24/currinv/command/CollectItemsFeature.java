package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class CollectItemsFeature extends Feature {

    public Item itemToCollect;

    public CollectItemsFeature() {
        super("collect", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        ArrayList<Item> items = new ArrayList<>();
        items.add(itemToCollect);

        CurrInvClient.fullSuiteSorter.takeItemsFromNearbyContainers(items);
    }
}
