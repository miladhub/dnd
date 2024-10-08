package org.meh.dnd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record Game(
        GameMode mode,
        List<PlayerOutput> events,
        GameChar playerChar,
        CombatStatus combatStatus,
        Chat chat,
        String background,
        String place,
        DialogueTarget dialogueTarget,
        List<String> diary,
        List<QuestGoal> quest
)
{
    public Game withMode(GameMode mode) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withFightStatus(CombatStatus combatStatus) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withPlayerChar(GameChar playerChar) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withLastOutput(PlayerOutput lastOutput) {
        List<PlayerOutput> newEvents = new ArrayList<>(events);
        newEvents.add(lastOutput);
        return new Game(mode, newEvents, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withChat(Chat chat) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withPlace(String place) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withDialogueTarget(DialogueTarget dialogueTarget) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withStoryLine(String storyLine) {
        List<String> diary = new ArrayList<>(this.diary);
        diary.add(storyLine);
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withDiary(List<String> diary) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, quest);
    }

    public Game withQuest(List<QuestGoal> quest) {
        return new Game(mode, events, playerChar, combatStatus, chat, background, place, dialogueTarget, diary, new ArrayList<>(new LinkedHashSet<>(quest)));
    }
}
