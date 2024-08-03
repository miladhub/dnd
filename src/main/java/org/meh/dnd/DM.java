package org.meh.dnd;

public interface DM
{
    Game process(
            Game game,
            PlayerInput input
    );
}
