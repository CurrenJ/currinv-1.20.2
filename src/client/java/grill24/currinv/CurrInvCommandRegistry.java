package grill24.currinv;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.currinv.command.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.ArrayList;
import java.util.List;

public class CurrInvCommandRegistry {
    public static final Feature CURRINV_ROOT_COMMAND = new PrintModInfoFeature();

    public static final Feature NAV_TO_MARKER_FEATURE = new NavigateToMarkerFeature();
    public static final Feature USE_ESCAPE_ROPE_FEATURE = new UseEscapeRopeFeature();

    public static final Feature SORTING_TOGGLE = new SortingFeature();
    public static final Feature QUICK_STACK_TOGGLE = new QuickStackFeature();
    public static final Feature INVENTORY_SURROUNDING_CONTAINERS_FEATURE = new InventorySurroundingContainersFeature();

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
        registerFeature(INVENTORY_SURROUNDING_CONTAINERS_FEATURE);
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
            commandRoot = buildCommand(CurrInvCommandRegistry.CURRINV_ROOT_COMMAND);
            for (Feature feature : CurrInvCommandRegistry.FEATURES)
            {
                commandRoot = commandRoot.then(buildCommand(feature));
            }

            dispatcher.register(commandRoot);
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(Feature feature)
    {
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
