package org.meh.dnd;

import java.util.List;

public record ExploreOutput(
        String place,
        String description,
        List<Actions> choices
)
        implements PlayerOutput
{
}
