package grill24.currinv.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.MinecraftClient;

public abstract class TickingFeature<T> extends Feature implements ITickingAction<T> {
    private boolean isActionExecuting;
    public TickingFeature(String commandText, boolean isToggleable){
        super(commandText, isToggleable);
    }

    @Override
    public void startAction(CommandContext<?> commandContext, MinecraftClient client)
    {
        isActionExecuting = true;
    }

    @Override
    public void endAction(T args)
    {
        isActionExecuting = false;
    }

    // ICurrInvTickingAction

    @Override
    public boolean isActionExecuting() {
        return isActionExecuting;
    };
}
