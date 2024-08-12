package org.meh.dnd;

import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

import java.util.ArrayList;
import java.util.List;

import static org.meh.dnd.ResponseParser.*;

public record AiDM(DMChannel dmChannel,
                   PlayerChannel playersChannel,
                   OpenAiClient openAiClient,
                   GameRepository gameRepository
) implements DM {
    private static final int NO_COMBAT_ZONE = 3;
    private static final String SYSTEM_PROMPT = """
            You are a Dungeons and Dragons master, and I'm going
            to provide to you what the players are doing.
            You have to briefly describe to them what's happening,
            and then you must provide them with choices on how
            to move forward in their story.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX = """
            Your response must be made of a description,
            followed by a list of non-playing characters (NPCs) and finally
            a list of places to explore.
            
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

            You cannot add anything beyond the format above.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX_NO_COMBAT = """
            Your response must be made of a description,
            followed by a list of non-playing characters (NPCs) and finally
            a list of places to explore.
            
            To mark the beginning of the NPCs section, place the following
            text before them:
            
            <new line>
            *** NPCs ***
            <new line>
            
            Each NPC must be listed with this format (all must be friendly):
            - friendly <type> <name>
            
            where
            - <type> can be either 'warrior' or 'magic' or 'beast'
            
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

            You cannot add anything beyond the format above.
            """;
    private static final String DIALOGUE_PROMPT_POSTFIX = """
            It is important to keep the dialogue brief.
            
            Your response must be a phrase, followed by a list of choices
            that the characters must choose from.
            
            To mark the beginning of the choices section, place the
            following text before them:
            
            <new line>
            *** CHOICES ***
            <new line>
            
            Present the choices with a bullet list, for example:
            
            * Hello there!
            * Bye
            """;

    @Override
    public void process(
            PlayerInput input
    ) throws Exception {
        Game game = gameRepository.game().orElseThrow();
        if (input.action() instanceof Start) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            game.background() + """
                            
                            Let's begin, what happens?
                            
                            """ + getExplorePromptPostfix(game))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content, game.place());
            gameRepository.save(g -> g.withLastOutput(output));
            playersChannel.post(output);
        }
        if (input.action() instanceof Explore e) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            String.format(game.background() +
                                    """
                                    
                                    The characters are currently exploring %s, what happens?
                                    
                                    """ + getExplorePromptPostfix(game),
                                    e.place()))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content, game.place());
            gameRepository.save(g -> g.withLastOutput(output));
            playersChannel.post(output);
        }
        if (input.action() instanceof Dialogue d) {
            String prompt = String.format(game.background() + """
                    
                    The characters choose to speak to '%s', what does '%s' say to start the dialogue?
                    
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
            playersChannel.post(output);
        }
        if (input.action() instanceof Say s) {
            String prompt = String.format(game.background() + """
                    
                    The characters say '%s', what's the answer?
                    
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
            playersChannel.post(output);
        }
        if (input.action() instanceof EndDialogue) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            game.background() + """
                            
                            The characters are currently exploring, what happens?
                            
                            """ + getExplorePromptPostfix(game))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content, game.place());
            gameRepository.save(g -> g
                    .withChat(new Chat(List.of()))
                    .withLastOutput(output)
            );
            playersChannel.post(output);
        }
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
        ParsedResponse parsed =
                parseExploreResponse(content);

        if (parsed.npcs().stream().anyMatch(NPC::hostile)) {
            return new ExploreOutput(
                    place,
                    parsed.description(),
                    parsed.npcs().stream()
                            .filter(NPC::hostile)
                            .map(npc -> new Attack(npc.name(), npc.type()))
                            .map(a -> (Actions) a)
                            .toList()
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
                    actions
            );
        }
    }
}
