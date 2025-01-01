package org.meh.dnd;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.meh.dnd.Quests.*;
import static org.meh.dnd.AiEntities.*;

public record AiDM(
        PlayerChannel playerChannel,
        GameRepository gameRepository
)
        implements DM
{
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String SYSTEM_PROMPT = """
            You are a Dungeons and Dragons master, and I'm going
            to provide to you what the player is doing.
            You have to briefly describe to them what's happening,
            and then you must provide them with choices on how
            to move forward in their story.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX = """
            Your response for what happens when they explore must consist of a description,
            followed by a list of non-playing characters (NPCs), and
            a list of places to explore. Finally, if you deem the description
            relevant enough in the context of the character's story,
            add a storyline at the end (max 10 words).
            """;

    private Assistant assistant() {
        ChatLanguageModel model = createModel();
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        Game g = gameRepository.game().orElseThrow();
        String systemPrompt = SYSTEM_PROMPT + String.format("""
                
                The character name is '%s'. Their background is as follows:
                
                "%s"
                
                """, g.playerChar().name(), g.background());

        String questPrompt = g.quest().isEmpty()
                ? "" : String.format("""
                
                The character's goals so far are:
                %s
                """,
                g.quest().stream()
                        .map(qg -> "* " + describeGoal(qg))
                        .collect(Collectors.joining("\n")));

        List<String> diary = gameRepository.game().map(Game::diary).orElse(List.of());
        String diaryPrompt = diary.isEmpty()? "" : String.format("""
                
                This is a list of noteworthy events happened so far:
                %s
                """, diary.stream()
                .map(e -> "* " + e)
                .collect(Collectors.joining("\n")));

        memory.add(new SystemMessage(
                systemPrompt + questPrompt + diaryPrompt));

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();
    }

    private static ChatLanguageModel createModel() {
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .apiKey(OPENAI_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public void process(
            Actions action
    ) {
        Game game = gameRepository.game().orElseThrow();
        if (action instanceof Start start) {
            QuestStartModel startModel = assistant().startQuest(String.format(
                    """
                    Let's begin the quest. Your job is twofold.
                    
                    First, lay out a list of goals that make sense given their
                    background.
                    
                    Secondly, as the character is currently exploring '%s',
                    describe what happens.
                    """, start.place()) + EXPLORE_PROMPT_POSTFIX
            );

            List<QuestGoal> quest = startModel.questGoals().stream()
                    .map(AiDM::parseGoal)
                    .toList();

            ParsedExploreResponse explore = startModel.exploreResponse();

            List<QuestGoal> newQuest =
                    updateQuestFromExploring(quest, start.place());
            ExploreOutput output = parseExploreOutput(explore, game.place());
            ExploreOutput newOutput =
                    output.withChoices(addQuestGoal(output.choices(), newQuest));
            gameRepository.save(g -> g
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine())
                    .withQuest(newQuest));
            playerChannel.post(newOutput);
        }
        if (action instanceof Explore e) {
            ParsedExploreResponse content = assistant().explore(
                            String.format(
                                    """
                                    The character is currently exploring %s, what happens?
                                    
                                    """ + EXPLORE_PROMPT_POSTFIX,
                                    e.place()));

            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(addQuestGoal(output.choices(), game.quest()));
            gameRepository.save(g -> g
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine()));
            playerChannel.post(newOutput);
        }
        if (action instanceof Dialogue d) {
            ParsedDialogueResponse content = assistant().dialogue(String.format(
                    """
                    The character wants to speak to NPC '%s'.
                    
                    Provide a phrase that the NPC '%s' says to start off the dialogue.
                    """,
                    d.target(), d.target()));
            DialogueOutput output =
                    parseDialogueOutput(game, content);
            gameRepository.save(g -> g
                    .withChat(new ChatWith(d.target(), List.of(new ChatMessage(ChatRole.DM, d.target(), output.phrase()))))
                    .withLastOutput(output)
            );
            playerChannel.post(output);
        }
        if (action instanceof Say s) {
            ChatWith chat =
                    (ChatWith) gameRepository.game().orElseThrow().chat();

            MessageWindowChatMemory memory =
                    MessageWindowChatMemory.withMaxMessages(100);

            chat.messages().forEach(m -> memory.add(
                    switch (m.role()) {
                        case DM -> new AiMessage(m.speaker() + ": " + m.message());
                        case PLAYER -> new UserMessage(m.speaker() + ": " + m.message());
                    }
            ));

            memory.add(new UserMessage(game.playerChar().name() + ": " + s.what()));

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(createModel())
                    .chatMemory(memory)
                    .build();

            ParsedDialogueResponse content = assistant.dialogue(String.format(
                    """
                    Provide a phrase as the answer from NPC '%s'.
                    """,
                    ((Somebody) game.dialogueTarget()).who()));

            DialogueOutput output =
                    parseDialogueOutput(game, content);
            gameRepository.save(g -> g
                    .withChat(chat.add(
                            new ChatMessage(ChatRole.PLAYER, game.playerChar().name(), s.what()),
                            new ChatMessage(ChatRole.DM, chat.target(), output.phrase())))
            );
            playerChannel.post(output);
        }
        if (action instanceof EndDialogue ed) {
            List<QuestGoal> newGoals = new ArrayList<>(game.quest());
            newGoals.add(ed.goal());

            ChatWith chat =
                    (ChatWith) gameRepository.game().orElseThrow().chat();

            MessageWindowChatMemory memory =
                    MessageWindowChatMemory.withMaxMessages(100);

            chat.messages().forEach(m -> memory.add(
                    switch (m.role()) {
                        case DM -> new AiMessage(m.speaker() + ": " + m.message());
                        case PLAYER -> new UserMessage(m.speaker() + ": " + m.message());
                    }
            ));

            memory.add(new UserMessage(game.playerChar().name() + ": " + ed.phrase()));

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(createModel())
                    .chatMemory(memory)
                    .build();

            ParsedExploreResponse content = assistant.explore(
                            String.format("""
                            
                            The character is currently exploring %s, what happens?
                            
                            """, game.place()) + EXPLORE_PROMPT_POSTFIX);

            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(addQuestGoal(output.choices(), newGoals));

            gameRepository.save(g -> g
                    .withChat(new NoChat())
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine())
                    .withQuest(newGoals)
            );
            playerChannel.post(newOutput);
        }
    }

    static ExploreOutput parseExploreOutput(
            ParsedExploreResponse parsed,
            String place
    ) {
        if (parsed.npcs().stream().anyMatch(NPC::hostile)) {
            return new ExploreOutput(
                    place,
                    parsed.description(),
                    parsed.npcs().stream()
                            .filter(NPC::hostile)
                            .map(npc -> new Attack(npc.name(), npc.type()))
                            .map(a -> (Actions) a)
                            .toList(),
                    parsed.storyLine()
            );
        } else {
            List<Actions> actions = new ArrayList<>();
            actions.addAll(parsed.npcs().stream()
                    .map(npc -> new Attack(npc.name(), npc.type()))
                    .toList());
            actions.addAll(parsed.npcs().stream()
                    .filter(npc -> npc.type() != NpcType.BEAST)
                    .map(npc -> new Dialogue(npc.name(), npc.type()))
                    .toList());
            actions.addAll(parsed.places().stream()
                    .map(p -> new Explore(p.name()))
                    .toList());
            actions.add(new Rest());
            return new ExploreOutput(
                    place,
                    parsed.description(),
                    actions,
                    parsed.storyLine()
            );
        }
    }

    static DialogueOutput parseDialogueOutput(
            Game game,
            ParsedDialogueResponse parsed
    ) {
        ParsedDialogueResponse dedup = dedupDialogueResponse(parsed, game);
        return new DialogueOutput(
                dedup.phrase(),
                dedup.answers().stream().map(AiDM::parseAction).toList());
    }

    private static Actions parseAction(DialogueActionModel m) {
        return switch (m.actionType()) {
            case SAY -> new Say(m.say().what());
            case END_DIALOGUE -> new EndDialogue(
                    m.endDialogue().endDialoguePhrase(),
                    parseGoal(m.endDialogue()));
        };
    }

    private static QuestGoal parseGoal(QuestGoalModel m) {
        return switch (m.goalType()) {
            case KILL -> new KillGoal(m.killGoal().killNpcType(), m.killGoal().killTarget(), false);
            case EXPLORE -> new ExploreGoal(m.exploreGoal().place(), false);
            case TALK -> new TalkGoal(m.talkGoal().talkNpcType(), m.talkGoal().talkTarget(), false);
        };
    }

    private static QuestGoal parseGoal(EndDialogueModel m) {
        return switch (m.goalType()) {
            case KILL -> new KillGoal(m.killGoal().killNpcType(), m.killGoal().killTarget(), false);
            case EXPLORE -> new ExploreGoal(m.exploreGoal().place(), false);
            case TALK -> new TalkGoal(m.talkGoal().talkNpcType(), m.talkGoal().talkTarget(), false);
        };
    }

    private static ParsedDialogueResponse dedupDialogueResponse(
            ParsedDialogueResponse parsed,
            Game game
    ) {
        return new ParsedDialogueResponse(
                parsed.phrase(),
                parsed.answers().stream()
                        .filter(a ->
                                a.actionType() != DialogueActionType.END_DIALOGUE ||
                                game.quest().stream()
                                        .noneMatch(g -> goalMatches(g, a.endDialogue()))
                ).toList());
    }

    private static boolean goalMatches(
            QuestGoal l,
            EndDialogueModel m
    ) {
        return switch (l) {
            case ExploreGoal le -> m.goalType() == GoalType.EXPLORE && le.target().equals(m.exploreGoal().place());
            case KillGoal lk -> m.goalType() == GoalType.KILL && lk.type() == m.killGoal().killNpcType() && lk.target().equals(m.killGoal().killTarget());
            case TalkGoal lt -> m.goalType() == GoalType.TALK && lt.type() == m.talkGoal().talkNpcType() && lt.target().equals(m.talkGoal().talkTarget());
        };
    }

    private static String describeGoal(QuestGoal g) {
        return switch (g) {
            case ExploreGoal e -> "explore " + e.target();
            case KillGoal k -> "kill " + k.target();
            case TalkGoal t -> "talk to " + t.target();
        };
    }

    public interface Assistant
    {
        QuestStartModel startQuest(String prompt);

        ParsedExploreResponse explore(String prompt);

        @dev.langchain4j.service.UserMessage("""
        {prompt}
        
        Also, provide a list of answers for the character to choose from.
        
        Each answer can either be of type "say" or "end dialogue", not both.
        
        In the "end dialogue" case, you must provide a goal.
        
        A goal must allow the character to reach one of their current goals,
        either directly or indirectly. In the "end dialogue phrase", hint as to
        why reaching this additional goal would help achieving one of the current
        goals. Do not specify goals that are already part of the quest's
        current goals.
        """)
        ParsedDialogueResponse dialogue(String prompt);
    }
}
