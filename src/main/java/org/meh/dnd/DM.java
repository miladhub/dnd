package org.meh.dnd;

public interface DM
{
    void process(
            PlayerInput input
    ) throws Exception;
}
