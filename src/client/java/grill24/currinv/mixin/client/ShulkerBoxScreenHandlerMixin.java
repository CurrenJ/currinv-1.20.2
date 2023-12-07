package grill24.currinv.mixin.client;

import grill24.currinv.sorting.ScreenWithInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ShulkerBoxScreenHandler.class)
public abstract class ShulkerBoxScreenHandlerMixin extends ScreenHandler implements ScreenWithInventory {
    protected ShulkerBoxScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Final
    @Shadow
    private Inventory inventory;

    @Override
    public Inventory currinv_1_20_2$getInventory() {
        return inventory;
    }
}
