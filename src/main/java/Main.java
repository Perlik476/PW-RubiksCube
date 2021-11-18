import concurrentcube.Cube;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        Cube cube = new Cube(4, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        //cube.test();

        ArrayList<Thread> threads = new ArrayList<>();
        threads.add(new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    cube.rotate(1, 1);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t1"));
        threads.add(new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    cube.rotate(1, 0);
                    cube.rotate(2, 0);
                    cube.rotate(1, 0);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t2"));
        threads.add(new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    cube.rotate(1, 2);
                    cube.rotate(2, 2);
                    cube.rotate(1, 2);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t3"));
        for (int i = 4; i < 10; i++) {
            int finalI = i;
            threads.add(new Thread(() -> {
                try {
                    for (int j = 0; j < 30; j++) {
                        cube.rotate((finalI + j) % 6, (finalI + j) % 4);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "t" + i));
        }
        threads.add(new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    System.out.println(cube.show());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t0"));

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(cube);



    }


}
