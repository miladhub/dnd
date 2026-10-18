package org.meh.dnd;

public record WeaponAttack(Weapon weapon, String target)
        implements Attacks
{
    public WeaponAttack(Weapon weapon) {
        this(weapon, "");
    }
}
