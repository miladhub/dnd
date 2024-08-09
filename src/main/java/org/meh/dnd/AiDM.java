package org.meh.dnd;

import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.meh.dnd.ResponseParser.*;

public record AiDM(DMChannel dmChannel,
                   PlayerChannel playersChannel,
                   OpenAiClient openAiClient,
                   GameRepository gameRepository
) implements DM {
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
            - <friendliness> <race> <name>
            
            where
            - <friendliness> can either be 'hostile' or 'friendly'
            - <race> can either be 'humanoid' or 'beast'
            
            Present the NPCs with a bullet list, for example:
            * hostile beast Wolf
            * friendly humanoid Elf
            
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
            
            Each choice must be either of these:
            - Say:
                - format: 'Dialogue <target>'
                - meaning: speak with somebody, e.g., 'Dialogue goblin'; the target match exactly someone that you mention in your description; this is only applicable if the target is not hostile; if there is no target, don't present this option
            - Attack:
                - format: 'Attack <target>'
                - meaning: attack a target; the target must be mentioned in your description; if there is no target, don't present this option
            - EndDialogue:
                - format: 'EndDialogue'
                - meaning: to end the dialogue
            
            You cannot add anything beyond the format above.
            
            Present the choices with a bullet list, for example:
            
            * Say Hello there!
            * Attack goblin
            * EndDialogue
            """;

    @Override
    public void process(
            String gameId,
            PlayerInput input
    ) throws Exception {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        if (input.action() instanceof Start) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            game.story() + """
                            
                            Let's begin, what happens?
                            
                            """ + EXPLORE_PROMPT_POSTFIX)
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content);
            gameRepository.save(gameId, g -> g.withLastOutput(output));
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof Explore e) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            e.place().isBlank()
                                    ? game.story() + """
                                    
                                    The characters are currently exploring, what happens?
                                    
                                    """ + EXPLORE_PROMPT_POSTFIX
                                    : String.format(
                                    game.story() + """
                                    
                                    The characters are currently exploring %s, what happens?
                                    
                                    """ + EXPLORE_PROMPT_POSTFIX,
                                    e.place()))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content);
            gameRepository.save(gameId, g -> g.withLastOutput(output));
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof Dialogue d) {
            String prompt = String.format(game.story() + """
                    
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
            DialogueOutput output = parseDialogueOutput(content);
            gameRepository.save(gameId, g -> g
                    .withChat(new Chat(List.of(new ChatMessage(ChatRole.DM, output.phrase()))))
                    .withLastOutput(output)
            );
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof Say s) {
            String prompt = String.format(game.story() + """
                    
                    The characters say '%s', what's the answer?
                    
                    """ + DIALOGUE_PROMPT_POSTFIX,
                    s.what());
            List<OpenAiRequestMessage> messages = new ArrayList<>(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT)
            ));
            messages.addAll(gameRepository.gameById(gameId).orElseThrow().chat().messages().stream()
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
            DialogueOutput output = parseDialogueOutput(content);
            gameRepository.save(gameId, g -> g
                    .withChat(g.chat().add(
                            new ChatMessage(ChatRole.PLAYER, s.what()),
                            new ChatMessage(ChatRole.DM, output.phrase())))
                    .withLastOutput(output)
            );
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof EndDialogue) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            game.story() + """
                            
                            The characters are currently exploring, what happens?
                            
                            """ + EXPLORE_PROMPT_POSTFIX)
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseExploreOutput(content);
            gameRepository.save(gameId, g -> g
                    .withChat(new Chat(List.of()))
                    .withLastOutput(output)
            );
            playersChannel.post(gameId, output);
        }
    }

    static ExploreOutput parseExploreOutput(String content) {
        ParsedResponse parsed =
                parseExploreResponse(content);

        if (parsed.npcs().stream().anyMatch(NPC::hostile)) {
            return new ExploreOutput(
                    parsed.description(),
                    parsed.npcs().stream()
                            .filter(NPC::hostile)
                            .map(npc -> new Attack(npc.name()))
                            .map(a -> (Actions) a)
                            .toList()
            );
        } else {
            List<Actions> actions = new ArrayList<>();
            actions.addAll(parsed.npcs().stream()
                    .map(npc -> new Attack(npc.name()))
                    .toList());
            actions.addAll(parsed.npcs().stream()
                    .map(npc -> new Dialogue(npc.name()))
                    .toList());
            actions.addAll(parsed.places().stream()
                    .map(p -> new Explore(p.name()))
                    .toList());
            actions.add(new Rest());
            return new ExploreOutput(
                    parsed.description(),
                    actions
            );
        }
    }

    static DialogueOutput parseDialogueOutput(String content) {
        String[] split = content
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** CHOICES ***\\E");
        String phrase = split[0];
        String answersContent = split[1];
        List<Actions> answers = Arrays.stream(answersContent.split("\n"))
                .filter(c -> !c.trim().isBlank())
                .map(c -> c.substring(2).trim())
                .map(c ->
                        !c.contains(" ")
                                ? ActionParser.actionFrom(c, "")
                                : ActionParser.actionFrom(c.substring(0, c.indexOf(" ")).trim(),
                                c.substring(c.indexOf(" ")).trim()))
                .toList();
        return new DialogueOutput(phrase.trim(), answers);
    }
}
