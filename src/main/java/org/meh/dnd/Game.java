package org.meh.dnd;

public record Game(
        String id,
        GameMode mode,
        PlayerOutput lastOutput,
        GameChar playerChar,
        CombatStatus combatStatus
)
{
    public Game withMode(GameMode mode) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus);
    }

    public Game withFightStatus(CombatStatus combatStatus) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus);
    }
}
