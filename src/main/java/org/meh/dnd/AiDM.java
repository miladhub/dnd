package org.meh.dnd;

import org.jboss.logging.Logger;
import org.meh.dnd.openai.ChatResponse;
import org.meh.dnd.openai.OpenAiClient;
import org.meh.dnd.openai.OpenAiRequestMessage;
import org.meh.dnd.openai.Role;

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
                        new OpenAiRequestMessage(Role.system, """
                                You are a Dungeons and Dragons master, and I'm going
                                to provide to you what the players are doing.
                                You have to briefly describe to them what's happening
                                and then you have to provide them with choices on how
                                to move forward in their story. Each prompt will tell
                                you the choices you must choose from.
                                """),
                        new OpenAiRequestMessage(Role.user, """
                                The players are currently exploring. Tell
                                them what's happening, and based on that, give them
                                one of these choices:
                                - Attack, if you decide there's some enemy to attack; in this case specify who they would attack - e.g., 'Attack goblin'
                                - Dialogue, if you decide there's some enemy to speak to; in this case specify who they would speak to - e.g., 'Dialogue elf'
                                - Explore, to let the party continue exploring
                                - Rest, to let the party rest; this is only applicable if there's no enemy, so don't provide this choice if there's an enemy
                                """)
                ), List.of());

                ExploreOutput output = new ExploreOutput(((ChatResponse.MessageChatResponse) response).content(),
                        List.of(new Explore(), new Rest()));
                playersChannel.post(game.id(), output);
            } catch(Exception e){
                LOG.error(e);
            }
        }
        return game;
    }
}
