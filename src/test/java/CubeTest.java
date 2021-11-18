
import concurrentcube.Cube;
import concurrentcube.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Random;

public class CubeTest {

    private Cube cube;

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
    void shouldCancelOut() {
        try {
            for (int cubeSize = 1; cubeSize <= 10; cubeSize++) {
                Cube cube = new Cube(cubeSize,
                        (x, y) -> {},
                        (x, y) -> {},
                        () -> {},
                        () -> {}
                );

                for (int testNumber = 0; testNumber <= 1; testNumber++) {
                    if (testNumber == 1) {
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

}
