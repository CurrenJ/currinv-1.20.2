package grill24.currinv.command.ticking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public record ScreenTickingFeatureDto(MinecraftClient client, Screen screen) { }
