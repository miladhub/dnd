package org.meh.dnd;

public record Game(
        String id,
        GameMode mode,
        PlayerOutput lastOutput,
        GameChar playerChar,
        CombatStatus combatStatus,
        Chat chat
)
{
    public Game withMode(GameMode mode) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus, chat);
    }

    public Game withFightStatus(CombatStatus combatStatus) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus, chat);
    }

    public Game withPlayerChar(GameChar playerChar) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus, chat);
    }

    public Game withLastOutput(PlayerOutput lastOutput) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus, chat);
    }

    public Game withChat(Chat chat) {
        return new Game(id, mode, lastOutput, playerChar, combatStatus, chat);
    }
}
