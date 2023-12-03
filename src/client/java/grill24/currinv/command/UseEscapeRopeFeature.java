package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.command.ticking.ClientTickingFeature;
import grill24.currinv.navigation.PlayerNavigator;
import net.minecraft.client.MinecraftClient;

public class UseEscapeRopeFeature extends ClientTickingFeature {
    public UseEscapeRopeFeature() {
        super("escapeRope", false);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client) {
        super.startAction(commandContext, client);
        CurrInvClient.navigator.startNavigation(client.world, client.player, PlayerNavigator.NavigationMode.ESCAPE_ROPE);
    }

    @Override
    public void onUpdate(MinecraftClient client) {
        if(!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath())
            endAction(client);
        else {
            CurrInvClient.navigator.onUpdate(client.world, client.player);
        }
    }
}
