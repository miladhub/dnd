package org.meh.dnd;

public interface DM
{
    void process(
            String gameId,
            PlayerInput input
    ) throws Exception;
}
