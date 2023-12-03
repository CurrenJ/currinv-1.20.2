package grill24.currinv.command.ticking;

import grill24.currinv.command.TickingFeature;

public abstract class ScreenTickingFeature extends TickingFeature<ScreenTickingFeatureDto> {
    public ScreenTickingFeature(String commandText, boolean isToggleable) {
        super(commandText, isToggleable);
    }
}
