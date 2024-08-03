package org.meh.dnd;

public record Game(
        String id,
        GameMode mode,
        PlayerOutput lastOutput
)
{
    public Game withLastOutput(PlayerOutput output) {
        return new Game(id, mode, output);
    }

    public Game withMode(GameMode mode) {
        return new Game(id, mode, lastOutput);
    }
}
