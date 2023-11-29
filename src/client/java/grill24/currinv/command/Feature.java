package grill24.currinv.command;

public abstract class Feature extends Command implements IAction {

    public Feature(String commandText, boolean isToggleable) {
        super(commandText, isToggleable);
    }
}
