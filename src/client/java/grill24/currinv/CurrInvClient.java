package grill24.currinv;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.navigation.PlayerNavigator;
import grill24.currinv.persistence.Config;
import grill24.currinv.sorting.FullSuiteSorter;
import grill24.currinv.sorting.Sorter;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandAction;
import grill24.sizzlib.component.ModComponentRegistry;
import grill24.sizzlib.component.StaticToString;
import grill24.sizzlib.persistence.PersistenceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

import java.io.File;
import java.nio.file.Path;

@Command("currInv")
public class CurrInvClient implements ClientModInitializer {

    private static String DATA_DIR = "data/currinv/";
    public static ModComponentRegistry modComponentRegistry;
    public static Config config;
    public static Sorter sorter;
    public static PlayerNavigator navigator;
    public static FullSuiteSorter fullSuiteSorter;
    public static CurrInvDebugRenderer currInvDebugRenderer;

    @Override
    public void onInitializeClient() {
        navigator = new PlayerNavigator();
        fullSuiteSorter = new FullSuiteSorter();
        currInvDebugRenderer = new CurrInvDebugRenderer();

        registerUseBlockEvents();

        modComponentRegistry = new ModComponentRegistry(CurrInvClient.class);
        modComponentRegistry.setDebug(false);

        modComponentRegistry.registerComponent(CurrInvClient.navigator);
        modComponentRegistry.registerComponent(CurrInvClient.fullSuiteSorter);
        modComponentRegistry.registerComponent(DebugParticles.class);
        modComponentRegistry.registerComponent(CurrInvClient.currInvDebugRenderer);
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

    public static void setBiomeAccessSeed(long biomeAccessSeed) {
        if (config == null || config.getBiomeAccessSeed() != biomeAccessSeed) {
            onBiomeAccessSeedChanged(biomeAccessSeed);
        }
    }

    public static void onBiomeAccessSeedChanged(long biomeAccessSeed) {
        config = new Config(biomeAccessSeed);
        sorter = new Sorter();

        modComponentRegistry.registerComponent(CurrInvClient.config);
        modComponentRegistry.registerComponent(CurrInvClient.sorter);
        modComponentRegistry.registerComponents();
    }

    public static Path getAbsoluteDataDir() {
        return PersistenceManager.getRelativeDirectoryInMinecraftDirectory(DATA_DIR);
    }

    public static File getFileInDataDir(String filename) {
        return new File(getAbsoluteDataDir().toFile(), filename);
    }
}

