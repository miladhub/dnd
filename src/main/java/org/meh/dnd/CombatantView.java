package org.meh.dnd;

public record CombatantView(String id, CharacterView character, boolean ally,
                            int initiative, int position, boolean active) {}
