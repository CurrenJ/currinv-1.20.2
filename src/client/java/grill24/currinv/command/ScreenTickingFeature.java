package grill24.currinv.command;

import net.minecraft.client.gui.screen.Screen;

public abstract class ScreenTickingFeature extends TickingFeature<ScreenTickingFeatureDto>{
    public ScreenTickingFeature(String commandText, boolean isToggleable) {
        super(commandText, isToggleable);
    }
}
