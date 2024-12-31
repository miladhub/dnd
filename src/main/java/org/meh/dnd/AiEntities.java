package org.meh.dnd;

import java.util.List;

public class AiEntities
{
    public record ParsedExploreResponse(String description, List<NPC> npcs, List<Place> places, String storyLine) {}
    public enum DialogueActionType { SAY, END_DIALOGUE }
    public enum GoalType { KILL, EXPLORE, TALK }
    public record KillGoalModel(NpcType killNpcType, String killTarget) {}
    public record TalkGoalModel(NpcType talkNpcType, String talkTarget) {}
    public record ExploreGoalModel(String place) {}
    public record SayModel(String what) {}
    public record QuestStartModel(List<QuestGoalModel> questGoals) {}
    public record QuestGoalModel(
            GoalType goalType,
            KillGoalModel killGoal,
            ExploreGoalModel exploreGoal,
            TalkGoalModel talkGoal
    ) {}
    public record EndDialogueModel(
            String endDialoguePhrase,
            GoalType goalType,
            KillGoalModel killGoal,
            ExploreGoalModel exploreGoal,
            TalkGoalModel talkGoal
    ) {}
    public record DialogueActionModel(
            DialogueActionType actionType,
            SayModel say,
            EndDialogueModel endDialogue
    ) {}
    public record ParsedDialogueResponse(String phrase, List<DialogueActionModel> answers) {}
    public record NPC(String name, NpcType type, boolean hostile) {}
    public record Place(String name) {}
}
