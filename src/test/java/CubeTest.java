
import concurrentcube.Cube;
import concurrentcube.Side;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Random;
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

    @Test
    @DisplayName("Checks sequences of rotations, which result in no change in cube's state")
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
    @DisplayName("Checks sequences of rotations, which result in a change in cube's state")
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

    @Test
    @DisplayName("Checks if there are threads rotating non-opposite sides nor showing in the same time")
    void concurrencyTest() {
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


    @Test
    @DisplayName("Checks if there are threads rotating the same layer of a side or showing in the same time")
    void concurrencyTest2() {
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
                AtomicInteger counter = new AtomicInteger(0);

                int finalCubeSize = cubeSize;
                Cube cube = new Cube(cubeSize,
                        (side, layer) -> {},
                        (side, layer) -> {},
                        () -> {
                            counter.incrementAndGet();
                            numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
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

                Assertions.assertTrue(numberOfOK.get() >= 1);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }


    @Test
    @DisplayName("small test")
    void concurrencyTestSmall() {
        try {
            AtomicInteger numberOfOK = new AtomicInteger(0);

            int cubeSize = 2;
            AtomicInteger counter = new AtomicInteger(0);

            Cube cube = new Cube(cubeSize,
                    (side, layer) -> {
                        counter.incrementAndGet();
                        numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    },
                    (side, layer) -> {
                        numberOfOK.addAndGet(counter.get() >= 2 ? 1 : 0);
                        counter.decrementAndGet();
                    },
                    () -> counter.incrementAndGet(),
                    () -> counter.decrementAndGet()
            );

            numberOfOK.set(0);

            ArrayList<Thread> threads = new ArrayList<>();
            for (int rotationId = 0; rotationId < 3; rotationId++) {
                int finalRotationId = rotationId;
                threads.add(new Thread(() -> {
                    try {
                        for (int rotation = 0; rotation < 2; rotation++) {
                            int currentSide = 0;//(finalRotationId <= 1) ? 0 : 1;
                            int currentLayer = finalRotationId % cubeSize;

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

            Assertions.assertTrue(numberOfOK.get() > 0);

        } catch (InterruptedException e) {
            e.printStackTrace();
            Assertions.fail("got InterruptedException");
        }
    }

}
