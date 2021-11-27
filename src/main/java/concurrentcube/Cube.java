package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private final int[][][] blocks;
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private static final int numberOfTypesOfThreads = 4;
    private final Semaphore mutex = new Semaphore(1);
    private final Semaphore firstOfType = new Semaphore(0); // TODO fair?
    private final Semaphore[] othersOfType = new Semaphore[]
            {new Semaphore(0), new Semaphore(0), new Semaphore(0), new Semaphore(0)};
    private final Semaphore exit = new Semaphore(0);
    private int howManyToExit = 0;
    private int howManyFirstOfType = 0;
    private final int[] howManyOfType = new int[]{0, 0, 0, 0};
    private int currentThreadType = -1;
    private int howManyThreadsActive = 0;
    private final boolean[] needNewFirstOfType = new boolean[]{false, false, false, false};

    private final Semaphore[] useLayer;
    private final Semaphore mutexAwakening = new Semaphore(1);

    public Cube(int size, BiConsumer<Integer, Integer> beforeRotation, BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing, Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        blocks = new int[6][size][size];
        clear();

        useLayer = new Semaphore[size];
        for (int i = 0; i < size; i++) {
            useLayer[i] = new Semaphore(1);
        }
    }

    public void clear() {
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
            setColumnOfArray(Side.RIGHT.getId(), layer, arrayUp); // TODO
            setRowOfArray(Side.UP.getId(), size - 1 - layer, revert(arrayLeft));
            setRowOfArray(Side.DOWN.getId(), layer, revert(arrayRight));
            setColumnOfArray(Side.LEFT.getId(), size - 1 - layer, arrayDown);
        }
        else {
            setColumnOfArray(Side.RIGHT.getId(), layer, revert(arrayDown));
            setRowOfArray(Side.UP.getId(), size - 1 - layer, arrayRight);
            setRowOfArray(Side.DOWN.getId(), layer, arrayLeft);
            setColumnOfArray(Side.LEFT.getId(), size - 1 - layer, revert(arrayUp));
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

    int[] revert(int[] array) {
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
            setColumnOfArray(Side.BACK.getId(), layer, revert(arrayUp));
            setColumnOfArray(Side.DOWN.getId(), size - 1 - layer, revert(arrayBack));
            setColumnOfArray(Side.FRONT.getId(), size - 1 - layer, arrayDown);
            setColumnOfArray(Side.UP.getId(), size - 1 - layer, arrayFront);
        }
        else {
            setColumnOfArray(Side.BACK.getId(), layer, revert(arrayDown)); // TODO
            setColumnOfArray(Side.DOWN.getId(), size - 1 - layer, arrayFront);
            setColumnOfArray(Side.FRONT.getId(), size - 1 - layer, arrayUp);
            setColumnOfArray(Side.UP.getId(), size - 1 - layer, revert(arrayBack));
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

    private int getThreadTypeId(int sideId) {
        return getThreadTypeId(Side.getSideOfId(sideId));
    }

    private int getThreadTypeId(Side side) {
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

    private void makeFirstOfType(int threadTypeId) throws InterruptedException {
        mutex.release();
        try {
            firstOfType.acquire(); // dziedziczenie ochrony
        } catch (InterruptedException e) {
            System.err.println("beginning: firstOfType");
            mutex.acquireUninterruptibly();
            System.err.println("mutex: beginning: firstOfType");
            howManyOfType[threadTypeId]--;
            if (howManyOfType[threadTypeId] > 0) {
                needNewFirstOfType[threadTypeId] = true;
                othersOfType[threadTypeId].release(); // przekazanie ochrony
            }
            else {
                howManyFirstOfType--;
            }
            mutex.release();
            Thread.currentThread().interrupt();
            throw e;
        }
        howManyFirstOfType--;
        currentThreadType = threadTypeId;
    }

    private void beginningProtocol(int threadTypeId) throws InterruptedException {
        mutex.acquire();
        if (currentThreadType != threadTypeId && currentThreadType != -1) {
            howManyOfType[threadTypeId]++;

            if (howManyOfType[threadTypeId] == 1) {
                howManyFirstOfType++;
                makeFirstOfType(threadTypeId);
            }
            else {
                mutex.release();
                try {
                    othersOfType[threadTypeId].acquire(); // dziedziczenie ochrony
                    if (needNewFirstOfType[threadTypeId]) {
                        makeFirstOfType(threadTypeId);
                    }
                } catch (InterruptedException e) {
                    System.err.println("beginning: othersOfType" + threadTypeId);
                    mutex.acquireUninterruptibly();
                    System.err.println("mutex: beginning: othersOfType" + threadTypeId);
                    howManyOfType[threadTypeId]--;
                    mutex.release();
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }

            howManyOfType[threadTypeId]--;
            howManyThreadsActive++;
            if (howManyOfType[threadTypeId] > 0) {
                //mutexAwakening.acquireUninterruptibly();
                othersOfType[threadTypeId].release(); // przekazanie ochrony
                //mutexAwakening.release();
            }
            else {
                mutex.release();
            }
        }
        else {
            currentThreadType = threadTypeId;
            howManyThreadsActive++;
            mutex.release();
        }
    }

    private void endingProtocol() throws InterruptedException {
        mutex.acquire();

        howManyThreadsActive--;
        if (howManyThreadsActive > 0) {
            howManyToExit++;
            mutex.release();
            try {
                exit.acquire(); // dziedziczenie ochrony
            } catch(InterruptedException e) {
                System.err.println("ending: exit");
                mutex.acquireUninterruptibly();
                System.err.println("mutex: ending: exit");
                howManyToExit--;
                mutex.release();
                Thread.currentThread().interrupt();
                throw e;
            }
            howManyToExit--;
        }

        if (howManyToExit > 0) {
            exit.release(); // przekazanie ochrony
        }
        else if (howManyFirstOfType > 0) {
            firstOfType.release(); // przekazanie ochrony
        }
        else {
            currentThreadType = -1;
            mutex.release();
        }
    }

    public void rotate(int side, int layer) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        beginningProtocol(getThreadTypeId(side));
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        int realLayer = Side.getSideOfId(side).isDefault() ? layer : size - 1 - layer;
        useLayer[realLayer].acquire();

        beforeRotation.accept(side, layer);

        rotateSequential(side, layer);

        afterRotation.accept(side, layer);

        useLayer[realLayer].release();

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        endingProtocol();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

//
//    private void beginningProtocol(int threadTypeId) throws InterruptedException {
//        mutex.acquire();
//        if (currentThreadType != threadTypeId && currentThreadType != -1) {
//            howManyOfType[threadTypeId]++;
//
//            if (howManyOfType[threadTypeId] == 1) {
//                howManyFirstOfType++;
//                mutex.release();
//                firstOfType.acquire(); // dziedziczenie ochrony
//                howManyFirstOfType--;
//                currentThreadType = threadTypeId;
//            }
//            else {
//                mutex.release();
//                othersOfType[threadTypeId].acquire(); // dziedziczenie ochrony
//            }
//
//            howManyOfType[threadTypeId]--;
//            howManyThreadsActive++;
//            if (howManyOfType[threadTypeId] > 0) {
//                othersOfType[threadTypeId].release(); // przekazanie ochrony
//            }
//            else {
//                mutex.release();
//            }
//        }
//        else {
//            currentThreadType = threadTypeId;
//            howManyThreadsActive++;
//            mutex.release();
//        }
//    }
//
//    private void endingProtocol() throws InterruptedException {
//        mutex.acquire();
//
//        howManyThreadsActive--;
//        if (howManyThreadsActive > 0) {
//            howManyToExit++;
//            mutex.release();
//            exit.acquire(); // dziedziczenie ochrony
//            howManyToExit--;
//        }
//
//        if (howManyToExit > 0) {
//            exit.release(); // przekazanie ochrony
//        }
//        else if (howManyFirstOfType > 0) {
//            firstOfType.release(); // przekazanie ochrony
//        }
//        else {
//            currentThreadType = -1;
//            mutex.release();
//        }
//    }

//    public void rotate(int side, int layer) throws InterruptedException {
//        boolean debug = false;
//        if (debug) System.out.println("begin start: " + Thread.currentThread().getName() + ", side: " + side + ", layer: " + layer);
//        beginningProtocol(getThreadTypeId(side));
//        if (debug) System.out.println("begin finish: " + Thread.currentThread().getName() + ", side: " + side + ", layer: " + layer);
//
//        int realLayer = Side.getSideOfId(side).isDefault() ? layer : size - 1 - layer;
//        useLayer[realLayer].acquire();
//        if (debug) System.out.println("useLayer.acquire(): " + Thread.currentThread().getName() + " " + realLayer + " " + side);
//
//        beforeRotation.accept(side, layer);
//
//        if (debug) System.out.println("before rotation ended " + layer);
//
//        rotateSequential(side, layer);
//
//        afterRotation.accept(side, layer);
//
//        if (debug) System.out.println("useLayer.release(): " + Thread.currentThread().getName() + " " + realLayer + " " + side);
//        useLayer[realLayer].release();
//
//        if (debug) System.out.println("end start: " + Thread.currentThread().getName() + ", side: " + side + ", layer: " + layer);
//        endingProtocol();
//        if (debug) System.out.println("end finish: " + Thread.currentThread().getName() + ", side: " + side + ", layer: " + layer);
//    }

    public String show() throws InterruptedException {
        //System.out.println("begin start: " + Thread.currentThread().getName() + ", show");
        beginningProtocol(0);
        //System.out.println("begin finish: " + Thread.currentThread().getName() + ", show");

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

        //System.out.println("end start: " + Thread.currentThread().getName() + ", show");
        endingProtocol();
        //System.out.println("end finish: " + Thread.currentThread().getName() + ", show");

        return result.toString();
    }

    public String showHuman() throws InterruptedException {
        //System.out.println("begin start: " + Thread.currentThread().getName() + ", show");
        beginningProtocol(0);
        //System.out.println("begin finish: " + Thread.currentThread().getName() + ", show");

        beforeShowing.run();

        StringBuilder result = new StringBuilder();
        for (Side side : Side.values()) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    result.append(blocks[side.getId()][row][column]);
                }
                result.append("\n");
            }
            result.append("\n\n");
        }

        afterShowing.run();

        //System.out.println("end start: " + Thread.currentThread().getName() + ", show");
        endingProtocol();
        //System.out.println("end finish: " + Thread.currentThread().getName() + ", show");

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