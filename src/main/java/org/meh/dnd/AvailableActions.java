package org.meh.dnd;

public record AvailableActions(
        int actions,
        int bonusActions,
        int remainingSpeed
)

{
    public AvailableActions {
        if (actions < 0 || bonusActions < 0 || remainingSpeed < 0)
            throw new IllegalStateException("negative value");
    }

    public AvailableActions subtractSpeed(int amount) {
        return new AvailableActions(actions, bonusActions, remainingSpeed - amount);
    }

    public boolean hasActionsLeft() {
        return actions > 0 || bonusActions > 0 || remainingSpeed > 0;
    }

    public AvailableActions subtractAction(boolean bonusAction) {
        if (bonusAction)
            return new AvailableActions(actions, bonusActions - 1, remainingSpeed);
        else
            return new AvailableActions(actions - 1, bonusActions, remainingSpeed);
    }
}
