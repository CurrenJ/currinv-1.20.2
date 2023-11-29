package grill24.currinv;

import grill24.currinv.command.*;
import grill24.currinv.navigation.PlayerNavigator;
import grill24.currinv.sorting.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

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

	public static void registerTickEvents() {
		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			for (ClientTickingFeature action : CurrInvCommandRegistry.CLIENT_TICKING_FEATURES) {
				action.onUpdate(client);
			}
		});
	}

	public static void registerScreenEvents() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof HandledScreen<?>) {
				// Repeatedly called while screen is being rendered (each tick).
				ScreenEvents.afterTick(screen).register((tickScreen) -> {
					for (ScreenTickingFeature action : CurrInvCommandRegistry.SCREEN_TICKING_FEATURES) {
						action.onUpdate(new ScreenTickingFeatureDto(client, tickScreen));
					}
				});
			}
		});
	}
}

