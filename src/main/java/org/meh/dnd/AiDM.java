package org.meh.dnd;

import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.meh.dnd.Quests.*;
import static org.meh.dnd.ResponseParser.*;

public record AiDM(DMChannel dmChannel,
                   PlayerChannel playerChannel,
                   OpenAiClient openAiClient,
                   GameRepository gameRepository
) implements DM {
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
            
            Present the answers with a bullet list, for example:
            
            * Hello there!
            * Bye
            
            Each answer must consist of a phrase that the character would say,
            it must not be an action.
            """;

    @Override
    public void process(
            Actions action
    ) throws Exception {
        Game game = gameRepository.game().orElseThrow();
        if (action instanceof Start start) {
            ChatResponse questResponse = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user, String.format(
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
                            """, game.background()))
            ), List.of());
            String questContent =
                    ((ChatResponse.MessageChatResponse) questResponse).content();
            List<QuestGoal> quest = parseQuest(questContent);

            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            start.place() != null && !start.place().isBlank()
                                ? context(game) + String.format("""
                                
                                Let's begin. The character is currently exploring %s, what happens?
                                
                                """, start.place()) + getExplorePromptPostfix(game)
                                : context(game) + """
                                
                                Let's begin, what happens?
                                
                                """ + getExplorePromptPostfix(game))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            List<QuestGoal> newQuest =
                    updateQuestFromExploring(quest, start.place());
            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(Quests.addQuestGoal(output.choices(), newQuest));
            gameRepository.save(g -> g
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine())
                    .withQuest(newQuest));
            playerChannel.post(newOutput);
        }
        if (action instanceof Explore e) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            String.format(context(game) +
                                    """
                                    
                                    The character is currently exploring %s, what happens?
                                    
                                    """ + getExplorePromptPostfix(game),
                                    e.place()))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
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
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user, prompt)),
                    List.of());
            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            DialogueOutput output =
                    parseDialogueOutput(content, d.target(), d.type());
            gameRepository.save(g -> g
                    .withChat(new Chat(List.of(new ChatMessage(ChatRole.DM, output.phrase()))))
                    .withLastOutput(output)
            );
            playerChannel.post(output);
        }
        if (action instanceof Say s) {
            String prompt = String.format(context(game) + """
                    
                    The character says '%s', what's the answer?
                    
                    """ + DIALOGUE_PROMPT_POSTFIX,
                    s.what());
            List<OpenAiRequestMessage> messages = new ArrayList<>(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT)
            ));
            messages.addAll(gameRepository.game().orElseThrow().chat().messages().stream()
                    .map(m -> new OpenAiRequestMessage(
                            switch (m.role()) {
                                case DM -> Role.assistant;
                                case PLAYER -> Role.user;
                            },
                            m.message()))
                    .toList());
            messages.add(new OpenAiRequestMessage(Role.user, prompt));

            ChatResponse response = openAiClient.chatCompletion(messages,
                    List.of());
            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            Somebody somebody = (Somebody) game.dialogueTarget();
            DialogueOutput output =
                    parseDialogueOutput(content, somebody.who(), somebody.type());
            gameRepository.save(g -> g
                    .withChat(g.chat().add(
                            new ChatMessage(ChatRole.PLAYER, s.what()),
                            new ChatMessage(ChatRole.DM, output.phrase())))
            );
            playerChannel.post(output);
        }
        if (action instanceof EndDialogue ed) {
            ChatResponse questResponse = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user, String.format(
                            """
                            Given this background:
                            
                            "%s"
                            
                            Given how this dialogue went:
                            
                            %s
                            
                            Given the current quest goals:
                            
                            %s
                            
                            Tell me if there is any other quest goal that should
                            be added to the current quest goals. Present the new
                            goals as a bullet list. Each goal must be of either
                            of these types:
                            
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
                            """,
                            game.background(),
                            printChat(
                                    game.chat(),
                                    game.playerChar().name(),
                                    ed.target()),
                            printGoals(game.quest())))
            ), List.of());
            String questContent =
                    ((ChatResponse.MessageChatResponse) questResponse).content();
            List<QuestGoal> newGoals = parseQuest(questContent);
            gameRepository.save(g -> g.withNewQuestGoals(newGoals));

            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            context(game) + chat(game) + """
                            
                            The character is currently exploring, what happens?
                            
                            """ + getExplorePromptPostfix(game))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content, game.place());
            ExploreOutput newOutput =
                    output.withChoices(Quests.addQuestGoal(output.choices(), newGoals));

            gameRepository.save(g -> g
                    .withChat(new Chat(List.of()))
                    .withLastOutput(newOutput)
                    .withStoryLine(newOutput.storyLine())
            );
            playerChannel.post(newOutput);
        }
    }

    private String printChat(
            Chat chat,
            String playerChar,
            String target
    ) {
        return chat.messages().stream()
                .map(m -> switch (m.role()) {
                    case DM -> target + ": " + m.message();
                    case PLAYER -> playerChar + ": " + m.message();
                })
                .map(m -> "* " + m)
                .collect(Collectors.joining("\n"));
    }

    private String printGoals(List<QuestGoal> quest) {
        return quest.stream()
                .map(g -> switch (g) {
                    case KillGoal kg -> "Kill " + kg.target();
                    case ExploreGoal eg -> "Explore " + eg.target();
                    case TalkGoal tg -> "Talk to " + tg.target();
                })
                .map(m -> "* " + m)
                .collect(Collectors.joining("\n"));
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
        return "\n\nHere's how the dialogue went, see if you find anything" +
                " useful for the story line:\n" +
                game.chat().messages().stream()
                        .map(d -> "* " + d.message())
                        .collect(Collectors.joining("\n"));
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
            String content,
            String target,
            NpcType type
    ) {
        ParsedDialogueResponse parsed = parseDialogueResponse(content);
        parsed.answers().add(new Attack(target, type));
        parsed.answers().add(new EndDialogue(target));
        return new DialogueOutput(target, parsed.phrase(), parsed.answers());
    }
}
