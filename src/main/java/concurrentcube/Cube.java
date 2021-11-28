package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public class Cube {
    private final int[][][] blocks;
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private int currentThreadType = -1;
    private int howManyThreadsActive = 0;
    private int howManyToExit = 0;
    private int howManyWaiting = 0;

    private final Lock lock = new ReentrantLock();
    private final Condition waiting = lock.newCondition();
    private final Condition entrance = lock.newCondition();
    private final Condition exit = lock.newCondition();

    private final Semaphore[] useLayer;

    public Cube(int size, BiConsumer<Integer, Integer> beforeRotation, BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing, Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        blocks = new int[6][size][size];
        setDefaultColors();

        useLayer = new Semaphore[size];
        for (int i = 0; i < size; i++) {
            useLayer[i] = new Semaphore(1);
        }
    }

    private void setDefaultColors() {
        for (Side side : Side.values()) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    blocks[side.getId()][row][column] = side.getId();
                }
            }
        }
    }

    public int getSize() {
        return size;
    }

    private void rotateSideArrayClockwise(int sideId) {
        for (int i = 0; i < size - 1; i++){
            for (int j = i; j < size - 1 - i; j++) {
                int temp = blocks[sideId][i][j];
                blocks[sideId][i][j] = blocks[sideId][size - 1 - j][i];
                blocks[sideId][size - 1 - j][i] = blocks[sideId][size - 1 - i][size - 1 - j];
                blocks[sideId][size - 1 - i][size - 1 - j] = blocks[sideId][j][size - 1 - i];
                blocks[sideId][j][size - 1 - i] = temp;
            }
        }
    }

    private void rotateSideArrayCounterClockwise(int sideId) {
        for (int i = 0; i < size - 1; i++){
            for (int j = i; j < size - 1 - i; j++) {
                int temp = blocks[sideId][j][size - 1 - i];
                blocks[sideId][j][size - 1 - i] = blocks[sideId][size - 1 - i][size - 1 - j];
                blocks[sideId][size - 1 - i][size - 1 - j] = blocks[sideId][size - 1 - j][i];
                blocks[sideId][size - 1 - j][i] = blocks[sideId][i][j];
                blocks[sideId][i][j] = temp;
            }
        }
    }

    private int[] getColumnOfArray(int sideId, int column) {
        int[] array = new int[size];

        for (int row = 0; row < size; row++) {
            array[row] = blocks[sideId][row][column];
        }

        return array;
    }

    private int[] getRowOfArray(int sideId, int row) {
        return blocks[sideId][row];
    }

    private void setColumnOfArray(int sideId, int column, int[] array) {
        for (int row = 0; row < size; row++) {
            blocks[sideId][row][column] = array[row];
        }
    }

    private void setRowOfArray(int sideId, int row, int[] array) {
        blocks[sideId][row] = array;
    }

    private void rotateSideArray(Side side, int layer, boolean changeDirection) {
        if (layer == 0) {
            if (!changeDirection) {
                rotateSideArrayClockwise(side.getId());
            }
            else {
                rotateSideArrayCounterClockwise(side.getId());
            }
        }
        else if (layer == size - 1) {
            if (!changeDirection) {
                rotateSideArrayCounterClockwise(side.getOpposite().getId());
            }
            else {
                rotateSideArrayClockwise(side.getOpposite().getId());
            }
        }
    }

    private boolean isFirstOrLastLayer(int layer) {
        return layer == 0 || layer == size - 1;
    }

    private void rotateNeighboursOfFront(int layer, boolean changeDirection) {
        int[] arrayUp = getRowOfArray(Side.UP.getId(), size - 1 - layer);
        int[] arrayLeft = getColumnOfArray(Side.LEFT.getId(), size - 1 - layer);
        int[] arrayRight = getColumnOfArray(Side.RIGHT.getId(), layer);
        int[] arrayDown = getRowOfArray(Side.DOWN.getId(), layer);

        if (!changeDirection) {
            setColumnOfArray(Side.RIGHT.getId(), layer, arrayUp);
            setRowOfArray(Side.UP.getId(), size - 1 - layer, revertArray(arrayLeft));
            setRowOfArray(Side.DOWN.getId(), layer, revertArray(arrayRight));
            setColumnOfArray(Side.LEFT.getId(), size - 1 - layer, arrayDown);
        }
        else {
            setColumnOfArray(Side.RIGHT.getId(), layer, revertArray(arrayDown));
            setRowOfArray(Side.UP.getId(), size - 1 - layer, arrayRight);
            setRowOfArray(Side.DOWN.getId(), layer, arrayLeft);
            setColumnOfArray(Side.LEFT.getId(), size - 1 - layer, revertArray(arrayUp));
        }
    }

    private void rotateNeighboursOfUp(int layer, boolean changeDirection) {
        int[] arrayBack = getRowOfArray(Side.BACK.getId(), layer);
        int[] arrayRight = getRowOfArray(Side.RIGHT.getId(), layer);
        int[] arrayFront = getRowOfArray(Side.FRONT.getId(), layer);
        int[] arrayLeft = getRowOfArray(Side.LEFT.getId(), layer);

        if (!changeDirection) {
            setRowOfArray(Side.RIGHT.getId(), layer, arrayBack);
            setRowOfArray(Side.FRONT.getId(), layer, arrayRight);
            setRowOfArray(Side.LEFT.getId(), layer, arrayFront);
            setRowOfArray(Side.BACK.getId(), layer, arrayLeft);
        }
        else {
            setRowOfArray(Side.RIGHT.getId(), layer, arrayFront);
            setRowOfArray(Side.FRONT.getId(), layer, arrayLeft);
            setRowOfArray(Side.LEFT.getId(), layer, arrayBack);
            setRowOfArray(Side.BACK.getId(), layer, arrayRight);
        }
    }

    int[] revertArray(int[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            int temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
        return array;
    }

    private void rotateNeighboursOfRight(int layer, boolean changeDirection) {
        int[] arrayUp = getColumnOfArray(Side.UP.getId(), size - 1 - layer);
        int[] arrayBack = getColumnOfArray(Side.BACK.getId(), layer);
        int[] arrayDown = getColumnOfArray(Side.DOWN.getId(), size - 1 - layer);
        int[] arrayFront = getColumnOfArray(Side.FRONT.getId(), size - 1 - layer);

        if (!changeDirection) {
            setColumnOfArray(Side.BACK.getId(), layer, revertArray(arrayUp));
            setColumnOfArray(Side.DOWN.getId(), size - 1 - layer, revertArray(arrayBack));
            setColumnOfArray(Side.FRONT.getId(), size - 1 - layer, arrayDown);
            setColumnOfArray(Side.UP.getId(), size - 1 - layer, arrayFront);
        }
        else {
            setColumnOfArray(Side.BACK.getId(), layer, revertArray(arrayDown));
            setColumnOfArray(Side.DOWN.getId(), size - 1 - layer, arrayFront);
            setColumnOfArray(Side.FRONT.getId(), size - 1 - layer, arrayUp);
            setColumnOfArray(Side.UP.getId(), size - 1 - layer, revertArray(arrayBack));
        }
    }

    private void rotateSequential(int sideId, int layer) {
        Side side = Side.getSideOfId(sideId);
        boolean changeDirection = false;

        if (!side.isDefault()) {
            side = side.getOpposite();
            layer = size - 1 - layer;
            changeDirection = true;
        }

        if (isFirstOrLastLayer(layer)) {
            rotateSideArray(side, layer, changeDirection);
        }

        if (side == Side.FRONT) {
            rotateNeighboursOfFront(layer, changeDirection);
        }
        else if (side == Side.RIGHT) {
            rotateNeighboursOfRight(layer, changeDirection);
        }
        else if (side == Side.UP) {
            rotateNeighboursOfUp(layer, changeDirection);
        }
    }


    private void beginningProtocol(int threadTypeId) throws InterruptedException {
        lock.lock();
        try {
            while (currentThreadType == -1 && howManyWaiting > 0) {
                waiting.await();
            }
            if (!(currentThreadType == threadTypeId || currentThreadType == -1)) {
                howManyWaiting++;
                while (!(currentThreadType == threadTypeId || currentThreadType == -1)) {
                    try {
                        entrance.await();
                    } catch (InterruptedException e) {
                        howManyWaiting--;
                        if (howManyWaiting == 0) {
                            waiting.signalAll();
                        }
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
                howManyWaiting--;
                howManyThreadsActive++;
                if (currentThreadType == -1) {
                    currentThreadType = threadTypeId;
                    waiting.signalAll();
                }
            }
            else {
                currentThreadType = threadTypeId;
                howManyThreadsActive++;
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void endingProtocol() {
        lock.lock();
        try {
            howManyThreadsActive--;
            if (howManyThreadsActive > 0) {
                howManyToExit++;
                while (howManyThreadsActive > 0) {
                    exit.awaitUninterruptibly();
                }
                howManyToExit--;
            }
            else {
                currentThreadType = -1;
                exit.signalAll();
            }

            if (howManyToExit == 0) {
                entrance.signalAll();
            }
        }
        finally {
            lock.unlock();
        }
    }

    public void rotate(int side, int layer) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        beginningProtocol(Side.getThreadTypeId(side));

        int realLayer = Side.getSideOfId(side).isDefault() ? layer : size - 1 - layer;
        try {
            useLayer[realLayer].acquire();
        } catch (InterruptedException e) {
            lock.lock();
            howManyThreadsActive--;
            if (howManyThreadsActive == 0) {
                currentThreadType = -1;
                exit.signalAll();
            }
            if (howManyToExit == 0) {
                entrance.signalAll();
            }
            lock.unlock();
            Thread.currentThread().interrupt();
            throw e;
        }

        beforeRotation.accept(side, layer);

        rotateSequential(side, layer);

        afterRotation.accept(side, layer);

        useLayer[realLayer].release();

        endingProtocol();
    }

    public String show() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        beginningProtocol(0);

        beforeShowing.run();

        StringBuilder result = new StringBuilder();
        for (Side side : Side.values()) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    result.append(blocks[side.getId()][row][column]);
                }
            }
        }

        afterShowing.run();

        endingProtocol();

        return result.toString();
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Side side : Side.values()) {
            result.append(side).append(":\n");
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    result.append(blocks[side.getId()][row][column]);
                }
                result.append("\n");
            }
        }
        return result.toString();
    }

}