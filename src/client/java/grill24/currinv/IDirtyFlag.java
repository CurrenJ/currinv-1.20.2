package grill24.currinv;

public interface IDirtyFlag {
    boolean isDirty();

    void markDirty();

    void markClean();

}
