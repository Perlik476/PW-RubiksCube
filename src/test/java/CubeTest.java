
import concurrentcube.Cube;
import concurrentcube.Side;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

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

                            cube.rotate(side.getId(), layer);
                            cube.rotate(side.getOpposite().getId(), cube.getSize() - 1 - layer);

                            statusAfter = cube.show();
                            Assertions.assertEquals(statusBefore, statusAfter);
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


    private boolean checkCountersNumberOfDifferentThreadTypes(AtomicIntegerArray counters) {
        int howMany = 0;
        for (int index = 0; index < counters.length(); index++) {
            howMany += counters.get(index) > 0 ? 1 : 0;
        }

        return (howMany <= 1);
    }

    @RepeatedTest(100)
    @DisplayName("Checks if there are threads rotating non-opposite sides nor showing in the same time")
    void concurrencySafetyTest() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);

            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                AtomicIntegerArray counters = new AtomicIntegerArray(7);

                Cube cube = new Cube(cubeSize,
                        (side, layer) -> counters.incrementAndGet(Side.getSideOfId(side).getDefault().getId()),
                        (side, layer) -> counters.decrementAndGet(Side.getSideOfId(side).getDefault().getId()),
                        () -> counters.incrementAndGet(6),
                        () -> counters.decrementAndGet(6)
                );

                for (int testNumber = 0; testNumber <= numberOfRandomTests; testNumber++) {
                    if (testNumber != 0) {
                        randomize(cube);
                    }

                    ArrayList<Thread> threads = new ArrayList<>();
                    for (int rotationId = 0; rotationId < 20; rotationId++) {
                        int finalRotationId = rotationId;
                        int finalCubeSize = cubeSize;
                        threads.add(new Thread(() -> {
                            try {
                                for (int rotation = 0; rotation < 20; rotation++) {
                                    int currentSide = finalRotationId % 6;
                                    int currentLayer = finalRotationId % finalCubeSize;

                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.rotate(currentSide, currentLayer);
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
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
    @DisplayName("Checks if there are threads rotating the same layer of a side or showing in the same time")
    void concurrencySafetyTest2() {
        try {
            AtomicInteger numberOfErrors = new AtomicInteger(0);

            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                AtomicIntegerArray counters = new AtomicIntegerArray(cubeSize * 6 + 1);

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> counters.incrementAndGet(Side.getSideOfId(side).getDefault().getId() * finalCubeSize + layer),
                        (side, layer) -> counters.decrementAndGet(Side.getSideOfId(side).getDefault().getId() * finalCubeSize + layer),
                        () -> counters.incrementAndGet(finalCubeSize * 6),
                        () -> counters.decrementAndGet(finalCubeSize * 6)
                );

                for (int testNumber = 0; testNumber <= numberOfRandomTests; testNumber++) {
                    if (testNumber != 0) {
                        randomize(cube);
                    }

                    ArrayList<Thread> threads = new ArrayList<>();
                    for (int rotationId = 0; rotationId < 20; rotationId++) {
                        int finalRotationId = rotationId;
                        threads.add(new Thread(() -> {
                            try {
                                for (int rotation = 0; rotation < 20; rotation++) {
                                    int currentSide = finalRotationId % 6;
                                    int currentLayer = finalRotationId % finalCubeSize;

                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.rotate(currentSide, currentLayer);
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
                                    cube.show();
                                    numberOfErrors.addAndGet(!checkCountersNumberOfDifferentThreadTypes(counters) ? 1 : 0);
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
                                Thread.sleep(10);
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
                                Thread.sleep(10);
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


    @Test
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
                                Thread.sleep(10);
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

                //System.out.println("test: " + testId + ", right: " + counterOfRightRotations.get() + ", rot: " + counterOfRotations.get());
                Assertions.assertTrue(counterOfRightRotations.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

    @Disabled
    @Test
    @DisplayName("Checks if interrupts are handled properly")
    void interruptTest() {
//        try {
            AtomicInteger counter = new AtomicInteger(0);
            int cubeSize = 2;

            Cube cube = new Cube(cubeSize,
                    (side, layer) -> {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.err.println("bro momento0");
                        }
                    },
                    (side, layer) -> {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            System.err.println("bro momento1");
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

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.err.println("XDDDD");
                    }
                }));
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (int i = 0; i < 6; i++) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("bro momento2");
                }
                threads.get(i).interrupt();
            }

            int i = 0;
            for (Thread thread : threads) {
                try {
                    thread.join();
                    System.err.println("thread " + (i++) + " joined");
                } catch (InterruptedException e) {
                    System.err.println("XDDDD2");
                }
            }

            System.err.println("finished: " + counter.get());
            Assertions.assertTrue(counter.get() >= 40);

//        }
//        catch (InterruptedException e) {
//            e.printStackTrace();
//            Assertions.fail("got InterruptedException");
//        }
    }

    @Disabled
    @Test
    @DisplayName("Testing test")
    void test() {
        try {
            int cubeSize = 10;
            Semaphore semaphore = new Semaphore(10);

            for (int testId = 0; testId < 1; testId++) {
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {
                            try {
                                Thread.sleep(10);
                                semaphore.acquire();
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        },
                        (side, layer) -> {
                            try {
                                Thread.sleep(10);
                                semaphore.release();
                                Thread.sleep(10);
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
                            for (int rotation = 0; rotation < 100; rotation++) {
                                cube.rotate(Side.FRONT.getId(), finalThreadId % cubeSize);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }));
                }

                threads.add(new Thread(() -> {
                    try {
                        for (int rotation = 0; rotation < 10; rotation++) {
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

                //System.out.println("test: " + testId + ", right: " + counterOfRightRotations.get() + ", rot: " + counterOfRotations.get());
                Assertions.assertTrue(true);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

}
