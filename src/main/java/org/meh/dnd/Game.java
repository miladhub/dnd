package org.meh.dnd;

public record Game(
        String id,
        GameMode mode,
        PlayerOutput lastOutput
)
{
    public Game withMode(GameMode mode) {
        return new Game(id, mode, lastOutput);
    }
}
