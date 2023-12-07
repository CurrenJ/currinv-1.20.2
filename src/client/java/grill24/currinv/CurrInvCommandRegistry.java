package grill24.currinv;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.currinv.command.*;
import grill24.currinv.command.ticking.ClientTickingFeature;
import grill24.currinv.command.ticking.ScreenTickingFeature;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.sorting.Sorter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;

import java.util.ArrayList;
import java.util.List;

public class CurrInvCommandRegistry {
    public static final Feature CURRINV_ROOT_COMMAND = new PrintModInfoFeature();

    public static final Feature NAV_TO_MARKER_FEATURE = new NavigateToMarkerFeature();
    public static final Feature USE_ESCAPE_ROPE_FEATURE = new UseEscapeRopeFeature();

    public static final Feature SORTING_TOGGLE = new SortingFeature();
    public static final Feature QUICK_STACK_TOGGLE = new QuickStackFeature();
    public static final Feature SCAN_CHESTS = new ScanChestsFeature();
    public static final Feature COLLECT_ITEMS = new CollectItemsFeature();
    public static final Feature CONSOLIDATE_AND_SORT = new ConsolidateAndSortFeature();

    public static final Feature DEBUG_PARTICLES = new DebugParticlesFeature();
    public static final Feature DEBUG_PATH = new DebugNavigationPathFeature();
    public static final Feature DEBUG_FSS = new DebugFullSuiteSorter();

    public static final Feature SET_CONFIG = new SetConfigFeature();

    public static final List<Feature> FEATURES;
    public static final List<ClientTickingFeature> CLIENT_TICKING_FEATURES;
    public static final List<ScreenTickingFeature> SCREEN_TICKING_FEATURES;

    static {
        FEATURES = new ArrayList<>();
        CLIENT_TICKING_FEATURES = new ArrayList<>();
        SCREEN_TICKING_FEATURES = new ArrayList<>();

        registerFeature(NAV_TO_MARKER_FEATURE);
        registerFeature(USE_ESCAPE_ROPE_FEATURE);

        registerFeature(SORTING_TOGGLE);
        registerFeature(QUICK_STACK_TOGGLE);
        registerFeature(SCAN_CHESTS);
        registerFeature(COLLECT_ITEMS);
        registerFeature(CONSOLIDATE_AND_SORT);

        registerFeature(DEBUG_PARTICLES);
        registerFeature(DEBUG_PATH);
        registerFeature(DEBUG_FSS);

        registerFeature(SET_CONFIG);
    }

    private static void registerFeature(Feature feature) {
        FEATURES.add(feature);

        if(feature instanceof ClientTickingFeature)
            CLIENT_TICKING_FEATURES.add(((ClientTickingFeature) feature));
        else if(feature instanceof ScreenTickingFeature)
            SCREEN_TICKING_FEATURES.add(((ScreenTickingFeature) feature));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> commandRoot;

    public static void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            commandRoot = buildCommand(CurrInvCommandRegistry.CURRINV_ROOT_COMMAND, registryAccess);
            for (Feature feature : CurrInvCommandRegistry.FEATURES)
            {
                commandRoot = commandRoot.then(buildCommand(feature, registryAccess));
            }

            dispatcher.register(commandRoot);
        });
    }

//            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("give").requires(source -> source.hasPermissionLevel(2))).then(CommandManager.argument("targets", EntityArgumentType.players())
//            .then((ArgumentBuilder<ServerCommandSource, ?>)((RequiredArgumentBuilder)CommandManager.argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess)).executes(context -> GiveCommand.execute((ServerCommandSource)context.getSource(), ItemStackArgumentType.getItemStackArgument(context, "item"), EntityArgumentType.getPlayers(context, "targets"), 1)))
//            .then(CommandManager.argument("count", IntegerArgumentType.integer(1)).executes(context -> GiveCommand.execute((ServerCommandSource)context.getSource(), ItemStackArgumentType.getItemStackArgument(context, "item"), EntityArgumentType.getPlayers(context, "targets"), IntegerArgumentType.getInteger(context, "count")))))));

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(Feature feature, CommandRegistryAccess commandRegistryAccess)
    {
        if(feature instanceof CollectItemsFeature)
        {
            return buildActionFeatureCommand(feature).then(ClientCommandManager.argument("value", ItemStackArgumentType.itemStack(commandRegistryAccess))
                    .executes(context -> {
                        final ItemStackArgument value = ItemStackArgumentType.getItemStackArgument(context, "value");

                        CollectItemsFeature collectItemsFeature = ((CollectItemsFeature) feature);
                        collectItemsFeature.itemToCollect = value.getItem();
                        collectItemsFeature.startAction(context, MinecraftClient.getInstance());
                        return 1;
                    }));
        } else if(feature instanceof SetConfigFeature) {
            var command = buildActionFeatureCommand(feature);

            command = command.then(ClientCommandManager.literal("quantity").executes(context -> {
                CurrInvClient.config.setSortingStyle(Sorter.SortingStyle.QUANTITY);
                return 1;
            }));
            command = command.then(ClientCommandManager.literal("type").executes(context -> {
                CurrInvClient.config.setSortingStyle(Sorter.SortingStyle.CREATIVE_MENU);
                return 1;
            }));
            command = command.then(ClientCommandManager.literal("lexicographical").executes(context -> {
                CurrInvClient.config.setSortingStyle(Sorter.SortingStyle.LEXICOGRAPHICAL);
                return 1;
            }));

            return command;
        } else if(feature instanceof DebugFullSuiteSorter) {
            var command =  buildToggleableFeatureCommand(feature).then(ClientCommandManager.literal("verbose").executes(context -> {
                feature.setEnabled(context,true);
                CurrInvClient.fullSuiteSorter.isDebugVerbose = !CurrInvClient.fullSuiteSorter.isDebugVerbose;
                DebugUtility.print(context, "Verbose mode: " + (CurrInvClient.fullSuiteSorter.isDebugVerbose ? "enabled" : "disabled"));
                return 1;
            }));
            command = command.then(ClientCommandManager.literal("particles").executes(context -> {
                feature.setEnabled(context,true);
                CurrInvClient.fullSuiteSorter.isDebugParticlesEnabled = !CurrInvClient.fullSuiteSorter.isDebugParticlesEnabled;
                DebugUtility.print(context, "Particles mode: " + (CurrInvClient.fullSuiteSorter.isDebugParticlesEnabled ? "enabled" : "disabled"));
                return 1;
            }));

            return command;
        }

        if(feature.isToggleable())
        {
            return buildToggleableFeatureCommand(feature);
        }
        else
        {
            return buildActionFeatureCommand(feature);
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildToggleableFeatureCommand(Feature feature)
    {
        var toggleableCommand = ClientCommandManager.literal(feature.getCommandText()).executes(context -> {
            feature.toggleEnabled(context);
            return 1;
        });
        toggleableCommand = toggleableCommand.then(ClientCommandManager.literal("enabled").executes(context -> {
            feature.setEnabled(context,true);
            return 1;
        }));
        toggleableCommand = toggleableCommand.then(ClientCommandManager.literal("disabled").executes(context -> {
            feature.setEnabled(context, false);
            return 1;
        }));
        return toggleableCommand;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildActionFeatureCommand(Feature feature)
    {
        return ClientCommandManager.literal(feature.getCommandText()).executes(context -> {
            assert MinecraftClient.getInstance() != null;
            feature.startAction(context, MinecraftClient.getInstance());
            return 1;
        });
    }
}
