package grill24.currinv.command;

public interface ITickingAction<T> extends IAction {

    boolean isActionExecuting();
    void onUpdate(T args);
    void endAction(T args);
}
