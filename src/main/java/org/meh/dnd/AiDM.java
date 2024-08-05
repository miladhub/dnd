package org.meh.dnd;

import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

import java.util.Arrays;
import java.util.List;

public record AiDM(DMChannel dmChannel,
                   PlayerChannel playersChannel, OpenAiClient openAiClient)
    implements DM
{
    private static final String SYSTEM_PROMPT = """
            You are a Dungeons and Dragons master, and I'm going
            to provide to you what the players are doing.
            You have to briefly describe to them what's happening,
            and then you must provide them with choices on how
            to move forward in their story.
            """;
    private static final String EXPLORE_PROMPT_POSTFIX = """
            Your response must be made of a description,
            followed by a list of choices.
            
            To mark the beginning of the choices section, place the
            following text before them:
            
            <new line>
            *** CHOICES ***
            <new line>
            
            Each choice must be either of these:
            - Attack:
                - format: 'Attack <target>'
                - meaning: attack a target; the target must be mentioned in your description; if there is no target, don't present this option
            - Dialogue:
                - format: 'Dialogue <target>'
                - meaning: speak with somebody, e.g., 'Dialogue goblin'; the target match exactly someone that you mention in your description; this is only applicable if the target is not hostile; if there is no target, don't present this option
            - Explore:
                - format: 'Explore <place>'
                - meaning: to let the party explore; this is only applicable if there's no enemy; don't add any further description after 'Explore'
            - Rest:
                - format: 'Rest'
                - meaning: to let the party rest; this is only applicable if there's no enemy; don't add any further description after 'Rest'
            
            You cannot add anything beyond the format above.
            
            Present the choices with a bullet list, for example:
            
            * Attach goblin
            * Dialogue elf
            * Explore
            * Rest
            """;
    private static final String DIALOGUE_PROMPT_POSTFIX = """
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
        if (input.action() instanceof Explore e) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            e.place().isBlank()
                                    ? """
                                    The characters are currently exploring, what happens?
                                    
                                    """ + EXPLORE_PROMPT_POSTFIX
                                    : String.format(
                                    """
                                    The characters are currently exploring %s, what happens?
                                    
                                    """ + EXPLORE_PROMPT_POSTFIX,
                                    e.place()))
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseOutput(content);
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof Dialogue d) {
            String prompt = String.format("""
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
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof Say s) {
            String prompt = String.format("""
                    The characters say '%s', what's the answer?
                    
                    """ + DIALOGUE_PROMPT_POSTFIX,
                    s.what());
            ChatResponse response = openAiClient.chatCompletion(List.of(
                            new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                            new OpenAiRequestMessage(Role.user, prompt)),
                    List.of());
            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            DialogueOutput output = parseDialogueOutput(content);
            playersChannel.post(gameId, output);
        }
        if (input.action() instanceof EndDialogue) {
            ChatResponse response = openAiClient.chatCompletion(List.of(
                    new OpenAiRequestMessage(Role.system, SYSTEM_PROMPT),
                    new OpenAiRequestMessage(Role.user,
                            """
                            The characters are currently exploring, what happens?
                            
                            """ + EXPLORE_PROMPT_POSTFIX)
            ), List.of());

            String content =
                    ((ChatResponse.MessageChatResponse) response).content();
            ExploreOutput output = parseOutput(content);
            playersChannel.post(gameId, output);
        }
    }

    static ExploreOutput parseOutput(String content) {
        String[] split = content
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** CHOICES ***\\E");
        String descriptionContent = split[0];
        String choicesContent = split[1];
        List<Actions> choices = Arrays.stream(choicesContent.split("\n"))
                .filter(c -> !c.trim().isBlank())
                .map(c -> c.substring(2))
                .map(c ->
                        !c.contains(" ")
                        ? ActionParser.actionFrom(c, "")
                        : ActionParser.actionFrom(c.substring(0, c.indexOf(" ")).trim(),
                        c.substring(c.indexOf(" ")).trim()))
                .toList();
        return new ExploreOutput(descriptionContent.trim(), choices);
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
