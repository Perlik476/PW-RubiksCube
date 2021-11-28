
import concurrentcube.Cube;
import concurrentcube.Side;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class CubeTest {

    private final int numberOfRandomTests = 5;

    private void randomize(Cube cube) {
        Random random = new Random();

        for (int i = 0; i < 100; i++) {
            try {
                cube.rotate(random.nextInt(6), random.nextInt(cube.getSize()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkNumberOfColors(Cube cube) {
        try {
            String s = cube.show();
            HashMap<Character, Integer> map = new HashMap<>();

            for (int i = 0; i < 6; i++) {
                map.put(Character.forDigit(i, 10), 0);
            }

            for (int i = 0; i < s.length(); i++) {
                map.put(s.charAt(i), map.get(s.charAt(i)) + 1);
            }

            int value = map.get('0');
            for (int i = 1; i < 6; i++) {
                if (value != map.get(Character.forDigit(i, 10))) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException ignored) {

        }
        return true;
    }

    private void randomizeAndRevert(Cube cube) {
        Random random = new Random();
        ArrayList<Integer> sides = new ArrayList<>();
        ArrayList<Integer> layers = new ArrayList<>();

        final int N = 100;
        for (int i = 0; i < N; i++) {
            try {
                sides.add(random.nextInt(6));
                layers.add(random.nextInt(cube.getSize()));
                cube.rotate(sides.get(sides.size() - 1), layers.get(layers.size() - 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (int i = N - 1; i >= 0; i--) {
            try {
                for (int k = 0; k < 3; k++)
                    cube.rotate(sides.get(i), layers.get(i));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    @DisplayName("Checks short sequences of rotations, which result in no change in cube's state")
    void shouldCancelOutTest() {
        try {
            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                Cube cube = new Cube(cubeSize,
                        (x, y) -> {},
                        (x, y) -> {},
                        () -> {},
                        () -> {}
                );

                for (int testNumber = 0; testNumber <= numberOfRandomTests; testNumber++) {
                    if (testNumber != 0) {
                        randomize(cube);
                        Assertions.assertTrue(checkNumberOfColors(cube));
                    }

                    //System.out.println(cube.show());
                    for (Side side : Side.values()) {
                        for (int layer = 0; layer < cube.getSize(); layer++) {
                            String statusBefore = cube.show();

                            for (int i = 0; i < 4; i++) {
                                cube.rotate(side.getId(), layer);
                            }

                            String statusAfter = cube.show();
                            Assertions.assertEquals(statusBefore, statusAfter);

                            Assertions.assertTrue(checkNumberOfColors(cube));

                            cube.rotate(side.getId(), layer);
                            cube.rotate(side.getOpposite().getId(), cube.getSize() - 1 - layer);

                            statusAfter = cube.show();
                            Assertions.assertEquals(statusBefore, statusAfter);

                            Assertions.assertTrue(checkNumberOfColors(cube));
                        }
                    }
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }



    @Test
    @DisplayName("Checks short sequences of rotations, which result in a change in cube's state")
    void shouldChangeResultTest() {
        try {
            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                Cube cube = new Cube(cubeSize,
                        (x, y) -> {},
                        (x, y) -> {},
                        () -> {},
                        () -> {}
                );

                for (int testNumber = 0; testNumber <= numberOfRandomTests; testNumber++) {
                    if (testNumber != 0) {
                        randomize(cube);
                    }

                    //System.out.println(cube.show());
                    for (Side side : Side.values()) {
                        for (int layer = 0; layer < cube.getSize(); layer++) {
                            String statusBefore = cube.show();
                            String statusAfter;

                            for (int i = 0; i < 4; i++) {
                                cube.rotate(side.getId(), layer);
                                statusAfter = cube.show();
                                Assertions.assertNotEquals(statusBefore, statusAfter);
                                statusBefore = statusAfter;
                            }
                        }
                    }
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }



    @Test
    @DisplayName("Checks if threads can rotate concurrently")
    void concurrencyTestRotate() {
        try {
            AtomicInteger numberOfOK = new AtomicInteger(0);

            for (int cubeSize = 2; cubeSize <= 10; cubeSize++) {
                numberOfOK.set(0);
                AtomicInteger counter = new AtomicInteger(0);

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                    counter.incrementAndGet();
                    numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                            try {
                                Thread.sleep(2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        },
                        (side, layer) -> {
                    numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                    counter.decrementAndGet();
                        },
                        () -> {},
                        () -> {}
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 10; threadId++) {
                    int finalThreadId = threadId;
                    threads.add(new Thread(() -> {
                        try {
                            for (int rotation = 0; rotation < 10; rotation++) {
                                int currentSide = (finalThreadId <= 5) ? 0 : 1;
                                int currentLayer = finalThreadId % finalCubeSize;

                                cube.rotate(currentSide, currentLayer);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }
                System.err.println(numberOfOK.get());
                Assertions.assertTrue(numberOfOK.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }


    @Test
    @DisplayName("Checks if threads can show concurrently")
    void concurrencyTestShow() {
        try {
            AtomicInteger numberOfOK = new AtomicInteger(0);

            for (int cubeSize = 2; cubeSize <= 10; cubeSize++) {
                numberOfOK.set(0);
                AtomicInteger counter = new AtomicInteger(0);

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {},
                        (side, layer) -> {},
                        () -> {
                            counter.incrementAndGet();
                            numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                            try {
                                Thread.sleep(2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        },
                        () -> {
                            numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                            counter.decrementAndGet();
                        }
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 10; threadId++) {
                    threads.add(new Thread(() -> {
                        try {
                            for (int rotation = 0; rotation < 10; rotation++) {
                                cube.show();
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }
                System.err.println(numberOfOK.get());
                Assertions.assertTrue(numberOfOK.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }



    @Test
    @DisplayName("Checks long sequences of rotations, which result in no change in cube's state")
    void shouldCancelOutTest2() {
        try {
            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                Cube cube = new Cube(cubeSize,
                        (x, y) -> {},
                        (x, y) -> {},
                        () -> {},
                        () -> {}
                );

                String statusBefore, statusAfter;
                statusBefore = cube.show();

                for (int testNumber = 1; testNumber <= numberOfRandomTests; testNumber++) {
                    randomizeAndRevert(cube);
                    statusAfter = cube.show();
                    Assertions.assertEquals(statusBefore, statusAfter);
                    statusBefore = statusAfter;
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }



    @Test
    @DisplayName("Checks if predefined rotations give correct result")
    void someSequentialTests() {
        try {
            Cube cube = new Cube(3,
                    (x, y) -> {},
                    (x, y) -> {},
                    () -> {},
                    () -> {}
            );

            cube.rotate(Side.FRONT.getId(), 0);
            cube.rotate(Side.RIGHT.getId(), 0);
            cube.rotate(Side.UP.getId(), 0);
            cube.rotate(Side.BACK.getId(), 0);
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.DOWN.getId(), 0);

            Assertions.assertEquals("533100122100112443400125553144335225002445335220153144", cube.show());

            cube.rotate(Side.FRONT.getId(), 0);
            cube.rotate(Side.RIGHT.getId(), 0);
            cube.rotate(Side.UP.getId(), 0);
            cube.rotate(Side.BACK.getId(), 0);
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.DOWN.getId(), 0);

            Assertions.assertEquals("244003104315411552221223021000234054305341551352153443", cube.show());


            cube.rotate(Side.BACK.getId(), 2);
            cube.rotate(Side.LEFT.getId(), 2);
            cube.rotate(Side.DOWN.getId(), 2);
            cube.rotate(Side.FRONT.getId(), 2);
            cube.rotate(Side.RIGHT.getId(), 2);
            cube.rotate(Side.UP.getId(), 2);

            Assertions.assertEquals("343202200501015320514123253135033035412444440124155251", cube.show());

            cube.clear();

            cube.rotate(Side.BACK.getId(), 0);
            cube.rotate(Side.BACK.getId(), 0);
            cube.rotate(Side.UP.getId(), 0);
            cube.rotate(Side.BACK.getId(), 2); // FRONT counterclockwise
            cube.rotate(Side.RIGHT.getId(), 0);
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.FRONT.getId(), 2); // BACK counterclockwise
            cube.rotate(Side.FRONT.getId(), 2); // BACK counterclockwise
            cube.rotate(Side.DOWN.getId(), 0);
            cube.rotate(Side.BACK.getId(), 0);

            Assertions.assertEquals("500502032413013313421425522554334122131144055200051443", cube.show());

            cube.clear();

            cube.rotate(Side.RIGHT.getId(), 0);
            cube.rotate(Side.UP.getId(), 0);
            cube.rotate(Side.UP.getId(), 0);
            cube.rotate(Side.BACK.getId(), 2); // FRONT counterclockwise
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.UP.getId(), 2); // DOWN counterclockwise
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.FRONT.getId(), 2); // BACK counterclockwise
            cube.rotate(Side.DOWN.getId(), 0);
            cube.rotate(Side.DOWN.getId(), 0);
            cube.rotate(Side.FRONT.getId(), 0);
            cube.rotate(Side.FRONT.getId(), 0);
            cube.rotate(Side.FRONT.getId(), 0);

            Assertions.assertEquals("221500450513413045522520442310330133411240533110552442", cube.show());

            cube.clear();

            cube.rotate(Side.FRONT.getId(), 1);
            cube.rotate(Side.UP.getId(), 1);
            cube.rotate(Side.LEFT.getId(), 0);
            cube.rotate(Side.LEFT.getId(), 1);

            Assertions.assertEquals("440151440121525121002113002303444303455133455225303225", cube.show());

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }


    @RepeatedTest(1000)
    @DisplayName("Checks if a thread can be starved")
    void concurrencyLivenessTest() {
        try {
            int cubeSize = 10;
            int maxRotations = 30;

            for (int testId = 0; testId < 20; testId++) {
                AtomicInteger counterOfRightRotations = new AtomicInteger(0);
                AtomicInteger counterOfRotations = new AtomicInteger(0);

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {},
                        (side, layer) -> {
                            counterOfRightRotations.addAndGet(side == Side.RIGHT.getId() ? 1 : 0);
                            counterOfRotations.addAndGet(1);
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        },
                        () -> {},
                        () -> {}
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 20; threadId++) {
                    int finalThreadId = threadId;
                    threads.add(new Thread(() -> {
                        try {
                            while (counterOfRightRotations.get() == 0 && counterOfRotations.get() < maxRotations) {
                                cube.rotate(Side.FRONT.getId(), finalThreadId % cubeSize);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                threads.add(new Thread(() -> {
                    try {
                        for (int rotation = 0; rotation < 10 && counterOfRotations.get() < maxRotations; rotation++) {
                            cube.rotate(Side.RIGHT.getId(), 0);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                System.out.println("test: " + testId + ", right: " + counterOfRightRotations.get() + ", rot: " + counterOfRotations.get());
                Assertions.assertTrue(counterOfRightRotations.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

    @RepeatedTest(1000)
    @DisplayName("Checks if a thread can be starved")
    void concurrencyLivenessTest2() {
        try {
            int cubeSize = 1000;
            int maxRotations = 20;

            for (int testId = 0; testId < 20; testId++) {
                AtomicInteger counterOfRightRotations = new AtomicInteger(0);
                AtomicInteger counterOfRotationsFinished = new AtomicInteger(0);
                AtomicInteger counterOfRotationsCalled = new AtomicInteger(0);
                AtomicInteger cont = new AtomicInteger(1);
                CyclicBarrier barrier = new CyclicBarrier(10, () -> {
                    if (counterOfRightRotations.get() > 0 || counterOfRotationsFinished.get() > maxRotations) {
                        cont.set(0);
                    }
                });
                Semaphore front = new Semaphore(1);
                Semaphore right = new Semaphore(0);

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                        },
                        (side, layer) -> {
                            System.out.println(side + ", " + layer);
                            counterOfRightRotations.addAndGet(side == Side.RIGHT.getId() ? 1 : 0);
                            counterOfRotationsFinished.addAndGet(1);
                            System.out.println(counterOfRightRotations.get() + " | " + counterOfRotationsFinished.get());
                        },
                        () -> {},
                        () -> {}
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 20; threadId++) {
                    int finalThreadId = threadId;
                    threads.add(new Thread(() -> {
                        try {
                            while (cont.get() == 1) {
                                try {
                                    barrier.await();
                                } catch (InterruptedException | BrokenBarrierException ignored) {

                                }
                                if (counterOfRotationsCalled.incrementAndGet() == 10) {
                                    right.release();
                                }
                                cube.rotate(Side.FRONT.getId(), finalThreadId % cubeSize);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                threads.add(new Thread(() -> {
                    try {
                        right.acquireUninterruptibly();
                        cube.rotate(Side.RIGHT.getId(), 0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                System.err.println("test: " + testId + ", right: " + counterOfRightRotations.get() + ", rot: " + counterOfRotationsFinished.get());
                Assertions.assertTrue(counterOfRightRotations.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

    @RepeatedTest(10)
    @DisplayName("Checks if interrupts are handled properly")
    void concurrencyInterruptTest() {
        AtomicInteger counter = new AtomicInteger(0);
        int cubeSize = 2;

        Cube cube = new Cube(cubeSize,
                (side, layer) -> {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException ignored) {

                    }
                },
                (side, layer) -> {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException ignored) {

                    }
                    counter.incrementAndGet();
                },
                () -> {},
                () -> {}
        );

        ArrayList<Thread> threads = new ArrayList<>();
        for (int threadId = 0; threadId < 10; threadId++) {
            int finalThreadId = threadId;
            threads.add(new Thread(() -> {
                try {
                    for (int rotation = 0; rotation < 10; rotation++) {
                        int currentSide = (finalThreadId <= 5) ? 0 : 1;
                        int currentLayer = finalThreadId % cubeSize;

                        cube.rotate(currentSide, currentLayer);
                    }

                } catch (InterruptedException ignored) {

                }
            }));
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (int i = 0; i < 6; i++) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {

            }
            threads.get(i).interrupt();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {

            }
        }

        Assertions.assertTrue(counter.get() >= 40);
        Assertions.assertTrue(checkNumberOfColors(cube));
    }


    private boolean checkCountersNumberOfDifferentThreadTypes(int[] counters, Semaphore mutex) {
        int howMany = 0;

        mutex.acquireUninterruptibly();
        for (int counter : counters) {
            howMany += counter > 0 ? 1 : 0;
        }
        mutex.release();

        return (howMany <= 1);
    }

    @RepeatedTest(20)
    @DisplayName("Checks if there are threads rotating non-opposite sides nor showing in the same time")
    void concurrencySafetySidesTest() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);
            int sleepMax = 2;
            Random random = new Random();

            for (int cubeSize = 2; cubeSize <= 10; cubeSize++) {
                Semaphore mutex = new Semaphore(1);
                int[] counters = new int[4];

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                            mutex.acquireUninterruptibly();
                            counters[Side.getThreadTypeId(side)]++;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        (side, layer) -> {
                            mutex.acquireUninterruptibly();
                            counters[Side.getThreadTypeId(side)]--;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            mutex.acquireUninterruptibly();
                            counters[0]++;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            mutex.acquireUninterruptibly();
                            counters[0]--;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        }
                );

                for (int testNumber = 0; testNumber <= numberOfRandomTests; testNumber++) {
                    if (testNumber != 0) {
                        randomize(cube);
                    }

                    ArrayList<Thread> threads = new ArrayList<>();
                    for (int rotationId = 0; rotationId < 40; rotationId++) {
                        int finalRotationId = rotationId;
                        int finalCubeSize = cubeSize;
                        threads.add(new Thread(() -> {
                            try {
                                for (int rotation = 0; rotation < 40; rotation++) {
                                    int currentSide = finalRotationId % 6;
                                    int currentLayer = finalRotationId % finalCubeSize;

                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                                    cube.rotate(currentSide, currentLayer);
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                                }

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }));
                    }

                    for (Thread thread : threads) {
                        thread.start();
                    }

                    for (Thread thread : threads) {
                        thread.join();
                    }
                }
            }
            Assertions.assertEquals(0, numberOfErrors.get());

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

    @RepeatedTest(100)
    @DisplayName("Checks if there are threads rotating non-opposite sides nor showing in the same time")
    void concurrencySafetySidesTestBig() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);
            int sleepMax = 2;
            Random random = new Random();

            int cubeSize = 10;
            Semaphore mutex = new Semaphore(1);
            int[] counters = new int[4];

            Cube cube = new Cube(cubeSize,
                    (side, layer) -> {
                        mutex.acquireUninterruptibly();
                        counters[Side.getThreadTypeId(side)]++;
                        mutex.release();
                    },
                    (side, layer) -> {
                        mutex.acquireUninterruptibly();
                        counters[Side.getThreadTypeId(side)]--;
                        mutex.release();
                    },
                    () -> {
                        mutex.acquireUninterruptibly();
                        counters[0]++;
                        mutex.release();
                    },
                    () -> {
                        mutex.acquireUninterruptibly();
                        counters[0]--;
                        mutex.release();
                    }
            );
            ArrayList<Thread> threads = new ArrayList<>();
            for (int rotationId = 0; rotationId < 10000; rotationId++) {
                int finalRotationId = rotationId;
                int finalCubeSize = cubeSize;
                threads.add(new Thread(() -> {
                    try {
                        for (int rotation = 0; rotation < 10; rotation++) {
                            int currentSide = finalRotationId % 6;
                            int currentLayer = finalRotationId % finalCubeSize;

                            numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                            cube.show();
                            numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                            cube.rotate(currentSide, currentLayer);
                            numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                            cube.show();
                            numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters, mutex) ? 1 : 0);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        Assertions.assertEquals(0, numberOfErrors.get());
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

    private boolean checkCountersNumberOfDifferentThreadsInSameLayer(int[] counters, Semaphore mutex) {
        int howMany = 0;

        mutex.acquireUninterruptibly();
        for (int i = 1; i < counters.length; i++) {
            howMany += counters[i] > 1 ? 1 : 0;
        }

        if (howMany != 0) {
            for (int counter : counters) {
                System.err.print(counter + ", ");
            }
            System.err.println();
        }

        mutex.release();

        return howMany == 0;
    }

    @RepeatedTest(20)
    @DisplayName("Checks if there are threads rotating the same layer of a side or showing in the same time")
    void concurrencySafetyLayersTest() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);
            Random random = new Random();
            int sleepMax = 2;

            for (int cubeSize = 5; cubeSize <= 10; cubeSize++) {
                Semaphore mutex = new Semaphore(1);
                int countersSize = 3 * cubeSize + 1;
                int[] counters = new int[countersSize];

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                            mutex.acquireUninterruptibly();
                            int realLayer = Side.getSideOfId(side).isDefault() ? layer : finalCubeSize - 1 - layer;
                            counters[(Side.getThreadTypeId(side) - 1) * finalCubeSize + realLayer + 1]++;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        (side, layer) -> {
                            mutex.acquireUninterruptibly();
                            int realLayer = Side.getSideOfId(side).isDefault() ? layer : finalCubeSize - 1 - layer;
                            counters[(Side.getThreadTypeId(side) - 1) * finalCubeSize + realLayer + 1]--;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            mutex.acquireUninterruptibly();
                            counters[0]++;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            mutex.acquireUninterruptibly();
                            counters[0]--;
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        }
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int rotationId = 0; rotationId < 50; rotationId++) {
                    int finalRotationId = rotationId;
                    threads.add(new Thread(() -> {
                        try {
                            for (int rotation = 0; rotation < 20; rotation++) {
                                int currentSide = finalRotationId % 6;
                                int currentLayer = finalRotationId % finalCubeSize;

                                numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(counters, mutex) ? 1 : 0);

                                cube.show();

                                numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(counters, mutex) ? 1 : 0);

                                cube.rotate(currentSide, currentLayer);

                                numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(counters, mutex) ? 1 : 0);

                                cube.show();

                                numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(counters, mutex) ? 1 : 0);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }
            }
            Assertions.assertEquals(0, numberOfErrors.get());

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }



    @RepeatedTest(10)
    @DisplayName("Checks safety when interrupting threads")
    void concurrencySafetyAndInterruptionsTest() {
        try {
            AtomicInteger numberOfErrorsLayers = new AtomicInteger(0);
            AtomicInteger numberOfErrorsSides = new AtomicInteger(0);
            AtomicInteger numberOfErrorsPartialCorrectness = new AtomicInteger(0);
            int sleepMax = 3;

            for (int cubeSize = 5; cubeSize <= 10; cubeSize++) {
                Semaphore mutex = new Semaphore(1);
                int countersLayersSize = 3 * cubeSize + 1;
                int[] countersLayers = new int[countersLayersSize];
                int[] countersSides = new int[4];
                AtomicInteger counterRotateActions = new AtomicInteger(0);
                AtomicInteger counterShowActions = new AtomicInteger(0);
                Random random = new Random();

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                            //System.out.println("begin: " + Side.getThreadTypeId(side) + " " + layer);
                            mutex.acquireUninterruptibly();
                            int realLayer = Side.getSideOfId(side).isDefault() ? layer : finalCubeSize - 1 - layer;
                            countersLayers[(Side.getThreadTypeId(side) - 1) * finalCubeSize + realLayer + 1]++;
                            countersSides[Side.getThreadTypeId(side)]++;
                            counterRotateActions.incrementAndGet();
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        (side, layer) -> {
                            //System.out.println("end: " + Side.getThreadTypeId(side) + " " + layer);
                            mutex.acquireUninterruptibly();
                            int realLayer = Side.getSideOfId(side).isDefault() ? layer : finalCubeSize - 1 - layer;
                            countersLayers[(Side.getThreadTypeId(side) - 1) * finalCubeSize + realLayer + 1]--;
                            countersSides[Side.getThreadTypeId(side)]--;
                            counterRotateActions.decrementAndGet();
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            //System.out.println("begin: " + 0);
                            mutex.acquireUninterruptibly();
                            countersLayers[0]++;
                            countersSides[0]++;
                            counterShowActions.incrementAndGet();
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        },
                        () -> {
                            //System.out.println("end: " + 0);
                            mutex.acquireUninterruptibly();
                            countersLayers[0]--;
                            countersSides[0]--;
                            counterShowActions.decrementAndGet();
                            mutex.release();
                            try {
                                Thread.sleep(random.nextInt(sleepMax));
                            } catch (InterruptedException ignored) {

                            }
                        }
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int rotationId = 0; rotationId < 100; rotationId++) {
                    int finalRotationId = rotationId;
                    threads.add(new Thread(() -> {
                        try {
                            for (int rotation = 0; rotation < 20; rotation++) {
                                int currentSide = finalRotationId % 6;
                                int currentLayer = finalRotationId % finalCubeSize;

                                numberOfErrorsLayers.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(countersLayers, mutex) ? 1 : 0);
                                numberOfErrorsSides.addAndGet(!checkCountersNumberOfDifferentThreadTypes(countersSides, mutex) ? 1 : 0);
                                numberOfErrorsPartialCorrectness.addAndGet(checkNumberOfColors(cube) ? 0 : 1);

                                cube.show();

                                numberOfErrorsLayers.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(countersLayers, mutex) ? 1 : 0);
                                numberOfErrorsSides.addAndGet(!checkCountersNumberOfDifferentThreadTypes(countersSides, mutex) ? 1 : 0);
                                numberOfErrorsPartialCorrectness.addAndGet(checkNumberOfColors(cube) ? 0 : 1);

                                cube.rotate(currentSide, currentLayer);

                                numberOfErrorsLayers.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(countersLayers, mutex) ? 1 : 0);
                                numberOfErrorsSides.addAndGet(!checkCountersNumberOfDifferentThreadTypes(countersSides, mutex) ? 1 : 0);
                                numberOfErrorsPartialCorrectness.addAndGet(checkNumberOfColors(cube) ? 0 : 1);

                                cube.show();

                                numberOfErrorsLayers.addAndGet(!checkCountersNumberOfDifferentThreadsInSameLayer(countersLayers, mutex) ? 1 : 0);
                                numberOfErrorsSides.addAndGet(!checkCountersNumberOfDifferentThreadTypes(countersSides, mutex) ? 1 : 0);
                                numberOfErrorsPartialCorrectness.addAndGet(checkNumberOfColors(cube) ? 0 : 1);
                            }

                        } catch (InterruptedException ignored) {

                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    if (random.nextBoolean()) {
                        thread.interrupt();
                        Thread.sleep(random.nextInt(sleepMax));
                    }
                }

                for (Thread thread : threads) {
                    try {
                        thread.join();
                    }
                    catch (InterruptedException ignored) {

                    }
                }
                Assertions.assertEquals(0, counterRotateActions.get());
                Assertions.assertEquals(0, counterShowActions.get());
            }
            System.err.println(numberOfErrorsLayers.get() + " " + numberOfErrorsSides.get() + " " + numberOfErrorsPartialCorrectness.get());
            Assertions.assertEquals(0, numberOfErrorsLayers.get());
            Assertions.assertEquals(0, numberOfErrorsSides.get());
            Assertions.assertEquals(0, numberOfErrorsPartialCorrectness.get());

        } catch (InterruptedException ignored) {

        }
    }


    @Test
    @DisplayName("Checks if multi-threaded rotations cause number of colors to differ")
    void partialCorrectnessTest() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);

            for (int cubeSize = 2; cubeSize <= 10; cubeSize++) {
                numberOfErrors.set(0);

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {},
                        (side, layer) -> {},
                        () -> {},
                        () -> {}
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 100; threadId++) {
                    int finalThreadId = threadId;
                    threads.add(new Thread(() -> {
                        try {
                            for (int rotation = 0; rotation < 100; rotation++) {
                                int currentSide = finalThreadId % 6;
                                int currentLayer = finalThreadId % finalCubeSize;

                                numberOfErrors.addAndGet(checkNumberOfColors(cube) ? 0 : 1);
                                cube.rotate(currentSide, currentLayer);
                                numberOfErrors.addAndGet(checkNumberOfColors(cube) ? 0 : 1);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }
                Assertions.assertEquals(0, numberOfErrors.get());
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }


    @Test
    @DisplayName("Checks if threads can be synchronized using a barrier")
    void synchronizationTest() {
        try {
            for (int cubeSize = 5; cubeSize <= 10; cubeSize++) {
                CyclicBarrier barrier = new CyclicBarrier(5);

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                            try {
                                barrier.await();
                            } catch (InterruptedException | BrokenBarrierException ignored) {

                            }
                        },
                        (side, layer) -> {},
                        () -> {},
                        () -> {}
                );

                ArrayList<Thread> threads = new ArrayList<>();
                for (int threadId = 0; threadId < 5; threadId++) {
                    int finalThreadId = threadId;
                    threads.add(new Thread(() -> {
                        try {
                            cube.rotate(0, finalThreadId);
                        } catch (InterruptedException ignored) {

                        }
                    }));
                    threads.add(new Thread(() -> {
                        try {
                            cube.rotate(1, finalThreadId);
                        } catch (InterruptedException ignored) {

                        }
                    }));
                }

                for (Thread thread : threads) {
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }


}
