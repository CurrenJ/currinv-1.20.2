package grill24.currinv;

import grill24.currinv.command.*;
import grill24.currinv.navigation.PlayerNavigator;
import grill24.currinv.sorting.Sorter;
import net.fabricmc.api.ClientModInitializer;
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

import java.util.Optional;

import static grill24.currinv.CurrInvCommandRegistry.registerClientCommands;

public class CurrInvClient implements ClientModInitializer {
	public static Sorter sorter;
	public static PlayerNavigator navigator = new PlayerNavigator();
	@Override
	public void onInitializeClient() {
		sorter = new Sorter();

		registerScreenEvents();
		registerClientCommands();
		registerTickEvents();
	}

	public void registerTickEvents() {
		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			for (ITickingAction action : CurrInvCommandRegistry.CLIENT_TICKING_ACTIONS) {
				action.onUpdate(client);
			}
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
                    screenInventory.ifPresent(inventory -> sorter.onUpdate(client, handledScreen, inventory));
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


}

