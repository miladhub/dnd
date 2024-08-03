package org.meh.dnd;

import java.util.List;

public record Game(
        String id,
        GameMode mode,
        List<PC> pcs,
        PlayerOutput lastOutput
)
{
    public Game withLastOutput(PlayerOutput output) {
        return new Game(id, mode, pcs, output);
    }

    public Game withMode(GameMode mode) {
        return new Game(id, mode, pcs, lastOutput);
    }
}
