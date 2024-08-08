package org.meh.dnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Chat(List<ChatMessage> messages)
{
    public Chat add(
            ChatMessage... newOnes
    ) {
        List<ChatMessage> newMessages = new ArrayList<>(messages);
        newMessages.addAll(Arrays.asList(newOnes));
        return new Chat(newMessages);
    }
}
