package grill24.currinv;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.debug.DebugUtility;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandAction;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

@Command("tools")
public class PlayerTools {
    private BlockPos lastPosPLayerTravelledThroughPortalTo;
    private Identifier lastDimension;

    public PlayerTools() {

    }

    public void setLastPosPLayerTravelledThroughPortalTo(BlockPos pos) {
        this.lastPosPLayerTravelledThroughPortalTo = pos;
    }

    public void setLastDimension(Identifier identifier) {
        lastDimension = identifier;
    }

    @CommandAction("whereIsMyPortal")
    public void whereIsMyPortal(CommandContext commandContext) {
        DebugUtility.print(commandContext, lastPosPLayerTravelledThroughPortalTo.toShortString() + " in " + lastDimension.toString() + ".");
    }
}
