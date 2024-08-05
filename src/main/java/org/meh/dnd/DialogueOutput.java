package org.meh.dnd;

import java.util.List;

public record DialogueOutput(
        String phrase,
        List<String> answers
)
        implements PlayerOutput
{
}
