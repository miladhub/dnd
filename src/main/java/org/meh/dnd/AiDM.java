package org.meh.dnd;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.meh.dnd.Quests.*;
import static org.meh.dnd.ResponseParser.*;

public record AiDM(
        PlayerChannel playerChannel,
        GameRepository gameRepository
)
        implements DM
{
    private final static Logger LOG = Logger.getLogger(AiDM.class);
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final int NO_COMBAT_ZONE = 3;
    private static final String SYSTEM_PROMPT = """
            You are a Dungeons and Dragons master, and I'm going
            to provide to you what the player is doing.
            You have to briefly describe to them what's happening,
            and then you must provide them with choices on how
            to move forward in their story.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX = """
            Your response must be made of a description,
            followed by a list of non-playing characters (NPCs), and
            a list of places to explore. Finally, if you deem the description
            relevant enough in the context of the character's story,
            add a storyline at the end.
            
            To mark the beginning of the NPCs section, place the following
            text before them:
            
            <new line>
            *** NPCs ***
            <new line>
            
            Each NPC must be listed with this format:
            - <friendliness> <type> <name>
            
            where
            - <friendliness> can either be 'hostile' or 'friendly'
            - <type> can be either 'warrior' or 'magic' or 'beast'
            - <name> is the name of the NPC, nothing more (no description here)
            
            Present the NPCs with a bullet list, for example:
            * hostile beast Wolf
            * friendly warrior Elf
            
            To mark the beginning of the places to explore section, place the
            following text before them:
            
            <new line>
            *** PLACES ***
            <new line>
            
            Present the places with a bullet list, for example:
            * Forest
            * Dungeon
            
            To mark the beginning of the storyline, place the following text
            before it:
            
            <new line>
            *** STORYLINE ***
            <new line>
            
            Present the storyline immediately after, only if noteworthy. It
            must consist of 10 words at most.

            You cannot add anything beyond the format above.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX_NO_COMBAT = """
            Your response must be made of a description,
            followed by a list of non-playing characters (NPCs), and
            a list of places to explore. Finally, if you deem the description
            relevant enough in the context of the character's story,
            add a storyline at the end.
            
            To mark the beginning of the NPCs section, place the following
            text before them:
            
            <new line>
            *** NPCs ***
            <new line>
            
            Each NPC must be listed with this format (all must be friendly):
            - friendly <type> <name>
            
            where
            - <type> can be either 'warrior' or 'magic' or 'beast'
            - <name> is the name of the NPC, nothing more (no description here)
            
            Present the NPCs with a bullet list, for example:
            * friendly beast Owl
            * friendly magic Elf
            
            To mark the beginning of the places to explore section, place the
            following text before them:
            
            <new line>
            *** PLACES ***
            <new line>
            
            Present the places with a bullet list, for example:
            * Forest
            * Dungeon
            
            To mark the beginning of the storyline, place the following text
            before it:
            
            <new line>
            *** STORYLINE ***
            <new line>
            
            Present the storyline immediately after, only if noteworthy. It
            must consist of 10 words at most.

            You cannot add anything beyond the format above.
            """;

    private Assistant assistant() {
        ChatLanguageModel model = createModel();
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(new AiDmToolbox(gameRepository))
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private static ChatLanguageModel createModel() {
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .apiKey(OPENAI_API_KEY)
                .parallelToolCalls(true)
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
            String questContent = assistant().chat(String.format(
                            """
                            This is the background of the character, called "%s":
                            
                            "%s"
                            
                            This character has no current goals, so your job now
                            is to lay out a bullet list of goals that would lead
                            them to reaching their goal. Each goal must be
                            of either of these types:
                            
                            - explore <place>
                            - kill <type> <who or what>
                            - talk <type> <to whom>
                            
                            where:
                            
                            - <type> can be either 'warrior' or 'magic' or 'beast'
                            
                            For example:
                            
                            * explore Dark Dungeon
                            * kill beast The Red Dragon
                            * talk magic Elf Sage
                            
                            Don't add anything but the list in the response. Each
                            element must be either an 'explore' or a 'kill' or a
                            'talk'.
                            """, game.playerChar().name(), game.background()));
            List<QuestGoal> quest = parseQuest(questContent);

            ParsedExploreResponse content = assistant().explore(
                            start.place() != null && !start.place().isBlank()
                                ? String.format("""
                                Let's begin. The character is currently exploring %s, what happens?
                                
                                """, start.place()) + getExplorePromptPostfix(game)
                                : """
                                Let's begin, what happens?
                                
                                """ + getExplorePromptPostfix(game));

            List<QuestGoal> newQuest =
                    updateQuestFromExploring(quest, start.place());
            ExploreOutput output = parseExploreOutput(content, game.place());
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
                                    
                                    """ + getExplorePromptPostfix(game),
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
                    .tools(new AiDmToolbox(gameRepository))
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

            ParsedExploreResponse content = assistant().explore(
                            chat(game) + String.format("""
                            
                            The character is currently exploring %s, what happens?
                            
                            """, game.place()) + getExplorePromptPostfix(game));

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

    private static String chat(Game game) {
        ChatWith chat = (ChatWith) game.chat();
        String chatContents = chat.messages().stream()
                .map(d -> "* " + d.speaker() + ": " + d.message())
                .collect(Collectors.joining("\n"));
        return "Here's how the dialogue went, see if you find anything" +
                " useful for the story line:\n" + chatContents + "\n";
    }

    private static String getExplorePromptPostfix(Game game) {
        if (lastEvents(game).stream().anyMatch(e -> e instanceof CombatOutput))
            return EXPLORE_PROMPT_POSTFIX_NO_COMBAT;
        else
            return EXPLORE_PROMPT_POSTFIX;
    }

    private static List<PlayerOutput> lastEvents(Game game) {
        List<PlayerOutput> events = game.events();
        return events.size() >= NO_COMBAT_ZONE
                ? events.subList(events.size() - NO_COMBAT_ZONE, events.size())
                : events;
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

    public record AiDmToolbox(GameRepository gameRepository)
    {
        @Tool("Gets the character's background")
        public String background() {
            LOG.info("tool - background");
            return gameRepository.game().map(Game::background).orElse("");
        }

        @Tool("Gets a list of noteworthy events happened so far")
        public List<String> diary() {
            LOG.info("tool - diary");
            return gameRepository.game().map(Game::diary).orElse(List.of());
        }

        @Tool("Gets the list of current quest goals")
        public List<String> goals() {
            LOG.info("tool - goals");
            return gameRepository.game()
                    .map(Game::quest)
                    .map(gs -> gs.stream()
                            .filter(g -> !g.reached())
                            .map(this::describeGoal)
                            .toList())
                    .orElse(List.of());
        }

        private String describeGoal(QuestGoal g) {
            return switch (g) {
                case ExploreGoal e -> "explore " + e.target();
                case KillGoal k -> "kill " + k.target();
                case TalkGoal t -> "talk to " + t.target();
            };
        }
    }

    public interface Assistant
    {
        @SystemMessage(SYSTEM_PROMPT)
        String chat(String prompt);

        @SystemMessage(SYSTEM_PROMPT)
        ParsedExploreResponse explore(String prompt);

        @SystemMessage(SYSTEM_PROMPT)
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
