package org.meh.dnd;

import org.jboss.logging.Logger;
import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

import java.util.Arrays;
import java.util.List;

public record AiDM(GameRepository gameRepository, DMChannel dmChannel,
                   PlayerChannel playersChannel, OpenAiClient openAiClient)
    implements DM
{
    private final static Logger LOG = Logger.getLogger(AiDM.class);

    @Override
    public Game process(
            Game game,
            PlayerInput input
    ) {
        if (game.mode() == GameMode.EXPLORING) {
            try {
                ChatResponse response = openAiClient.chatCompletion(List.of(
                        new OpenAiRequestMessage(Role.system,
                                """
                                You are a Dungeons and Dragons master, and I'm going
                                to provide to you what the players are doing.
                                You have to briefly describe to them what's happening,
                                and then you must provide them with choices on how
                                to move forward in their story.
                                
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
                                
                                Present the choices with a numbered list,
                                for example:
                                
                                1. Attach goblin
                                2. Dialogue elf
                                3. Explore
                                4. Rest
                                """),
                        new OpenAiRequestMessage(Role.user,
                                ((Explore) input.action()).place().isBlank()?
                                """
                                The players are currently exploring, what happens?
                                """
                                :
                                String.format(
                                """
                                The players are currently exploring %s, what happens?
                                """, ((Explore) input.action()).place()
                                ))
                ), List.of());

                String content =
                        ((ChatResponse.MessageChatResponse) response).content();
                ExploreOutput output = parseOutput(content);
                playersChannel.post(game.id(), output);
            } catch(Exception e){
                LOG.error(e);
            }
        }
        return game;
    }

    static ExploreOutput parseOutput(String content) {
        String[] split = content
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** CHOICES ***\\E");
        String descriptionContent = split[0];
        String choicesContent = split[1];
        List<Actions> choices = Arrays.stream(choicesContent.split("\n"))
                .filter(c -> !c.trim().isBlank())
                .map(c -> c.substring(3))
                .map(c ->
                        !c.contains(" ")
                        ? ActionParser.actionFrom(c, "")
                        : ActionParser.actionFrom(c.substring(0, c.indexOf(" ")).trim(),
                        c.substring(c.indexOf(" ")).trim()))
                .toList();
        return new ExploreOutput(descriptionContent.trim(), choices);
    }
}
