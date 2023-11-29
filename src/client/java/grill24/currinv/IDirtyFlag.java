package grill24.currinv;

public interface IDirtyFlag {
    public boolean isDirty();
    public void markDirty();
    public void markClean();

}
