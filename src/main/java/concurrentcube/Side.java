package concurrentcube;

import java.util.Arrays;

public enum Side {
    UP(0),
    LEFT(1),
    FRONT(2),
    RIGHT(3),
    BACK(4),
    DOWN(5);

    private static final Side[] defultSides = new Side[]{FRONT, RIGHT, UP};
    private final int id;

    Side(int id) {
        this.id = id;
    }

    public Side getOpposite() {
        switch (this) {
            case UP:
                return DOWN;
            case FRONT:
                return BACK;
            case RIGHT:
                return LEFT;
            case BACK:
                return FRONT;
            case LEFT:
                return RIGHT;
            case DOWN:
                return UP;
        }
        return null;
    }


    public int getId() {
        return id;
    }

    public static Side getSideOfId(int id) {
        for (Side side : Side.values()) {
            if (side.id == id) {
                return side;
            }
        }
        return null;
    }

    public boolean isDefault() {
        return Arrays.asList(defultSides).contains(this);
    }

    public Side getDefault() {
        return isDefault() ? this : this.getOpposite();
    }
}
