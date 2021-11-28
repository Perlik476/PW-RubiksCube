package concurrentcube;

import java.util.Arrays;

public enum Side {
    UP(0),
    LEFT(1),
    FRONT(2),
    RIGHT(3),
    BACK(4),
    DOWN(5);

    private static final Side[] defaultSides = new Side[]{FRONT, RIGHT, UP};
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
            default:
                return UP;
        }
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
        return Side.UP;
    }

    public boolean isDefault() {
        return Arrays.asList(defaultSides).contains(this);
    }

    public Side getDefault() {
        return this.isDefault() ? this : this.getOpposite();
    }

    public static int getThreadTypeId(int sideId) {
        return getThreadTypeId(Side.getSideOfId(sideId));
    }

    public static int getThreadTypeId(Side side) {
        if (side == Side.FRONT || side == Side.BACK) {
            return 1;
        }
        else if (side == Side.RIGHT || side == Side.LEFT) {
            return 2;
        }
        else if (side == Side.UP || side == Side.DOWN) {
            return 3;
        }
        return -1;
    }
}
