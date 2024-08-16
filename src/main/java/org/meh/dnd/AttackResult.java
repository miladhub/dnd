package org.meh.dnd;

public sealed interface AttackResult
    permits Hit, Miss
{
    GameChar gameChar();
}
