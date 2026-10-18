package org.meh.dnd;

/** A cell in the combat battlefield. Rows and columns are zero based. */
public record GridPosition(int column, int row) {
    public static final int COLUMNS = 12;
    public static final int ROWS = 8;
    public static final int CELL_FEET = 5;

    public GridPosition {
        if (column < 0 || column >= COLUMNS)
            throw new IllegalArgumentException("Grid column must be between 0 and 11");
        if (row < 0 || row >= ROWS)
            throw new IllegalArgumentException("Grid row must be between 0 and 7");
    }

    public int distanceInFeet(GridPosition other) {
        return Math.max(Math.abs(column - other.column), Math.abs(row - other.row)) * CELL_FEET;
    }

    public int distanceTo(GridPosition other) { return distanceInFeet(other); }
    public int distance(GridPosition other) { return distanceInFeet(other); }

    public GridPosition move(Dir direction) {
        return switch (direction) {
            case UP -> new GridPosition(column, row - 1);
            case DOWN -> new GridPosition(column, row + 1);
            case LEFT -> new GridPosition(column - 1, row);
            case RIGHT -> new GridPosition(column + 1, row);
            case TOWARDS_ENEMY, AWAY_FROM_ENEMY -> this;
        };
    }

    public boolean canMove(Dir direction) {
        return switch (direction) {
            case UP -> row > 0;
            case DOWN -> row < ROWS - 1;
            case LEFT -> column > 0;
            case RIGHT -> column < COLUMNS - 1;
            case TOWARDS_ENEMY, AWAY_FROM_ENEMY -> false;
        };
    }

    /** Compatibility mapping for the old one-dimensional distance. */
    public static GridPosition fromLegacyDistance(int distance) {
        return new GridPosition(Math.min(COLUMNS - 1, Math.max(0, distance / CELL_FEET)), 0);
    }
}
