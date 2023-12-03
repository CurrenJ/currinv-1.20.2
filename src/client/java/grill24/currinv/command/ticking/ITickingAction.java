package grill24.currinv.command.ticking;

import grill24.currinv.command.IAction;

public interface ITickingAction<T> extends IAction {

    boolean isActionExecuting();
    void onUpdate(T args);
    void endAction(T args);
}
