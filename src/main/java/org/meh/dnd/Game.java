package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;

public record Game(
        GameMode mode,
        List<PlayerOutput> events,
        GameChar playerChar,
        CombatStatus combatStatus,
        Chat chat,
        String background,
        String place,
        DialogueTarget dialogueTarget
)
{
    public Game withMode(GameMode mode) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withFightStatus(CombatStatus combatStatus) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withPlayerChar(GameChar playerChar) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withLastOutput(PlayerOutput lastOutput) {
        List<PlayerOutput> newEvents = new ArrayList<>(events);
        newEvents.add(lastOutput);
        return new Game(mode, newEvents, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withChat(Chat chat) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withPlace(String place) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }

    public Game withDialogueTarget(DialogueTarget dialogueTarget) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget);
    }
}
