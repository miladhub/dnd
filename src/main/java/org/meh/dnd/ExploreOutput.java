package org.meh.dnd;

import java.util.List;

public record ExploreOutput(
        String place,
        String description,
        List<Actions> choices,
        String storyLine
)
        implements PlayerOutput
{
    ExploreOutput withChoices(List<Actions> choices) {
        return new ExploreOutput(place, description, choices, storyLine);
    }
}
