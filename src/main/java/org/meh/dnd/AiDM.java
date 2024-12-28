package org.meh.dnd;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

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
    private static final String DIALOGUE_PROMPT_POSTFIX = """
            It is important to keep the dialogue brief.
            
            Your response must be a phrase, followed by a list of possible
            answers that the player must choose from.
            
            To mark the beginning of the answers section, place the
            following text before them:
            
            <new line>
            *** ANSWERS ***
            <new line>
            
            Each answer can either be a phrase to continue the dialogue, or an
            dialogue-ending phrase plus one goal quest to be added as a result
            of the dialogue, separated by an arrow, "=>":
            
            - <phrase>
            - <phrase> => <goal>
            
            The <goal> can be either of:
            
            - explore <place>
            - kill <type> <who or what>
            - talk <type> <to whom>
            
            where:
            
            - <type> can be either 'warrior' or 'magic' or 'beast'
            
            If a goal is specified, then the corresponding phrase must be one
            that would end the dialogue.
            
            For example:
            
            * Hello there! How are you?
            * Farewell, and good luck. => explore Dark Dungeon
            * Good luck defeating the dragon! => kill beast The Red Dragon
            * Tell my old Elf friend that I sent you. => talk magic Elf Sage
            
            In these examples, the first one, without a goal, does not end the
            dialogue. The others, such as "Farewell...", end the dialogue and
            provide a goal.
            
            Each answer must consist of a phrase that the character would say,
            it must not be an action.
            """;

    private Assistant assistant() {
        ChatLanguageModel model = createModel();
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private static ChatLanguageModel createModel() {
        return new OpenAiChatModel.OpenAiChatModelBuilder()
                .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                .apiKey(OPENAI_API_KEY)
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
                            Given this background:
                            
                            "%s"
                            
                            Lay out a bullet list of goals that would lead the
                            character to reaching their goal. Each goal must be
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
                            """, game.background()));
            List<QuestGoal> quest = parseQuest(questContent);

            String content = assistant().chat(
                            start.place() != null && !start.place().isBlank()
                                ? context(game) + String.format("""
                                
                                Let's begin. The character is currently exploring %s, what happens?
                                
                                """, start.place()) + getExplorePromptPostfix(game)
                                : context(game) + """
                                
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
            String content = assistant().chat(
                            String.format(context(game) +
                                    """
                                    
                                    The character is currently exploring %s, what happens?
                                    
                                    """ + getExplorePromptPostfix(game),
                                    e.place()));

            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(Quests.addQuestGoal(output.choices(), game.quest()));
            gameRepository.save(g -> g
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine()));
            playerChannel.post(newOutput);
        }
        if (action instanceof Dialogue d) {
            String prompt = String.format(context(game) + """
                    
                    The character wants to speak to '%s', what does '%s' say to start the dialogue?
                    
                    """ + DIALOGUE_PROMPT_POSTFIX,
                    d.target(),
                    d.target());
            String content = assistant().chat(prompt);
            DialogueOutput output =
                    parseDialogueOutput(content);
            gameRepository.save(g -> g
                    .withChat(new ChatWith(d.target(), List.of(new ChatMessage(ChatRole.DM, d.target(), output.phrase()))))
                    .withLastOutput(output)
            );
            playerChannel.post(output);
        }
        if (action instanceof Say s) {
            String prompt = String.format(context(game) + """
                    
                    The character says '%s', what's the answer?
                    
                    """ + DIALOGUE_PROMPT_POSTFIX,
                    s.what());

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

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(createModel())
                    .chatMemory(memory)
                    .build();

            String content = assistant.chat(prompt);

            DialogueOutput output =
                    parseDialogueOutput(content);
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

            String content = assistant().chat(
                            context(game) + chat(game) + String.format("""
                            
                            The character is currently exploring %s, what happens?
                            
                            """, game.place()) + getExplorePromptPostfix(game));

            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(Quests.addQuestGoal(output.choices(), newGoals));

            gameRepository.save(g -> g
                    .withChat(new NoChat())
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine())
                    .withQuest(newGoals)
            );
            playerChannel.post(newOutput);
        }
    }

    private static String context(Game game) {
        return game.background() + "\n" + describeDiary(game);
    }

    private static String describeDiary(Game game) {
        return game.diary().isEmpty()
                ? ""
                : "\nHere's a list of noteworthy events happened so far:\n" +
                game.diary().stream()
                        .map(d -> "* " + d)
                        .collect(Collectors.joining("\n")) + "\n\n";
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
            String content,
            String place
    ) {
        ParsedExploreResponse parsed =
                parseExploreResponse(content);
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
            String content
    ) {
        ParsedDialogueResponse parsed = parseDialogueResponse(content);
        return new DialogueOutput(parsed.phrase(), parsed.answers());
    }

    public interface Assistant
    {
        @SystemMessage(SYSTEM_PROMPT)
        String chat(String prompt);
    }
}
