package org.meh.dnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public sealed interface Chat
    permits ChatWith, NoChat
{}
