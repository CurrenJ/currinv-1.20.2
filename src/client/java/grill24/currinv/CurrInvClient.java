package grill24.currinv;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.currinv.navigation.PlayerNavigator;
import grill24.currinv.sorting.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;

import java.util.Optional;

public class CurrInvClient implements ClientModInitializer {
	public static final int ANALYSIS_FREQUENCY_IN_TICKS = 20;
	private int ticksRemainingUntilSlowTick = 0;
	private Sorter sorter;
	public static PlayerNavigator navigator = new PlayerNavigator();

	private LiteralArgumentBuilder<FabricClientCommandSource> commandRoot;

	@Override
	public void onInitializeClient() {
		sorter = new Sorter();

		registerScreenEvents();
		registerClientCommands();
		registerTickEvents();
	}

	public boolean slowTick() {
		boolean isSlowTick = false;
		if (ticksRemainingUntilSlowTick <= 0) {
			ticksRemainingUntilSlowTick = ANALYSIS_FREQUENCY_IN_TICKS;
			isSlowTick = true;
		}

		ticksRemainingUntilSlowTick--;

		return isSlowTick;
	}

	public void registerTickEvents() {
		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			if(client.player != null && navigator.shouldStartNavigation)
			{
                navigator.startNavigation(client.world, client.player, navigator.navigationMode);
				navigator.shouldStartNavigation = false;
			}
			navigator.onUpdate(client.world, client.player);
		});
	}

	public void registerScreenEvents() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof HandledScreen<?>) {
				// Repeatedly called while screen is being rendered (each tick).
				ScreenEvents.afterTick(screen).register((tickScreen) -> {


					HandledScreen<?> handledScreen = ((HandledScreen<?>) screen);
					handledScreen.getScreenHandler().addListener(new ScreenHandlerListener() {
						@Override
						public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
							sorter.markDirty();
						}

						@Override
						public void onPropertyUpdate(ScreenHandler handler, int property, int value) {}
					});
					Optional<Inventory> screenInventory = TryGetInventoryFromScreen(handledScreen);
                    screenInventory.ifPresent(inventory -> sorter.OnUpdate(client, handledScreen, inventory));
				});
			}
		});
	}

	public <T extends ScreenHandler> Optional<Inventory> TryGetInventoryFromScreen(HandledScreen<T> screen)
	{
		if(screen instanceof GenericContainerScreen)
		{
			return Optional.of(((GenericContainerScreen) screen).getScreenHandler().getInventory());
		}
		if(screen instanceof ShulkerBoxScreen)
		{
			ShulkerBoxScreenHandler shulkerBoxScreenHandler = ((ShulkerBoxScreen) screen).getScreenHandler();
			ScreenWithInventory screenWithInventory = ((ScreenWithInventory) shulkerBoxScreenHandler);
			return Optional.of(screenWithInventory.currinv_1_20_2$getInventory());
		}
		return Optional.empty();
	}

	public void registerClientCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			commandRoot = ClientCommandManager.literal("currinv");
			commandRoot = commandRoot.executes(context -> {
				context.getSource().sendFeedback(Text.literal("CurrInv is a mod developed by Curren Jeandell."));
				return 1;
			});

			commandRoot = commandRoot.then(buildToggleableFeatureCommand("sorting", (isEnabled) -> { sorter.isEnabled = isEnabled; }));
			commandRoot = commandRoot.then(buildToggleableFeatureCommand("quickStack", (isEnabled) -> { sorter.isQuickStackEnabled = isEnabled; }));
			commandRoot = commandRoot.then(buildToggleableFeatureCommand("navigatorToMarker", (isEnabled) -> {
				navigator.navigationMode = PlayerNavigator.NavigationMode.TO_MARKER;
				navigator.shouldStartNavigation = isEnabled;
			}));
			commandRoot = commandRoot.then(buildToggleableFeatureCommand("navigatorEscapeRope", (isEnabled) -> {
				navigator.navigationMode = PlayerNavigator.NavigationMode.ESCAPE_ROPE;
				navigator.shouldStartNavigation = isEnabled;
			}));

			dispatcher.register(commandRoot);
		});
	}

	LiteralArgumentBuilder<FabricClientCommandSource> buildToggleableFeatureCommand(String text, ToggleableFeature toggleableFeature)
	{
		var toggleable = ClientCommandManager.literal(text).executes(context -> -1);
		toggleable = toggleable.then(ClientCommandManager.literal("enabled").executes(context -> {
			toggleableFeature.setEnabled(true);
			return 1;
		}));
		toggleable = toggleable.then(ClientCommandManager.literal("disabled").executes(context -> {
			toggleableFeature.setEnabled(false);
			return 1;
		}));
		return toggleable;
	}
}

