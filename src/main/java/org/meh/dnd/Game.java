package org.meh.dnd;

public record Game(
        String id,
        GameMode mode,
        PlayerOutput lastOutput,
        GameChar playerChar,
        FightStatus fightStatus
)
{
    public Game withMode(GameMode mode) {
        return new Game(id, mode, lastOutput, playerChar, fightStatus);
    }

    public Game withFightStatus(FightStatus fightStatus) {
        return new Game(id, mode, lastOutput, playerChar, fightStatus);
    }
}
