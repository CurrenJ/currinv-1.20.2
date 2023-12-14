package grill24.currinv;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.component.Command;
import grill24.currinv.component.CommandAction;
import grill24.currinv.component.ModComponentRegistry;
import grill24.currinv.component.StaticToString;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.navigation.PlayerNavigator;
import grill24.currinv.persistence.Config;
import grill24.currinv.sorting.FullSuiteSorter;
import grill24.currinv.sorting.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

@Command("currInv")
public class CurrInvClient implements ClientModInitializer {
    public static ModComponentRegistry modComponentRegistry;
    public static Config config;
    public static Sorter sorter;
    public static PlayerNavigator navigator;
    public static FullSuiteSorter fullSuiteSorter;
    public static CurrInvDebugRenderer currInvDebugRenderer;

    @Override
    public void onInitializeClient() {
        config = new Config();
        sorter = new Sorter();
        navigator = new PlayerNavigator();
        fullSuiteSorter = new FullSuiteSorter();
        currInvDebugRenderer = new CurrInvDebugRenderer();

        registerUseBlockEvents();

        modComponentRegistry = new ModComponentRegistry(CurrInvClient.class);
        modComponentRegistry.registerComponent(CurrInvClient.config);
        modComponentRegistry.registerComponent(CurrInvClient.navigator);
        modComponentRegistry.registerComponent(CurrInvClient.sorter);
        modComponentRegistry.registerComponent(CurrInvClient.fullSuiteSorter);
        modComponentRegistry.registerComponent(DebugParticles.class);
        modComponentRegistry.registerComponent(CurrInvClient.currInvDebugRenderer);
        modComponentRegistry.registerComponents();
    }

    public static void registerUseBlockEvents() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
            if (world.isClient() && blockEntity instanceof LootableContainerBlockEntity) {
                CurrInvClient.sorter.onUseContainer(MinecraftClient.getInstance(), ((LootableContainerBlockEntity) blockEntity));
            }
            return ActionResult.PASS;
        });
    }

    @CommandAction("info")
    public static void printModInfo(CommandContext<FabricClientCommandSource> commandContext) {
        DebugUtility.print(commandContext, toStringStatic());
    }

    @StaticToString
    public static String toStringStatic() {
        return "CurrInv is a mod developed by Curren Jeandell.";
    }
}

