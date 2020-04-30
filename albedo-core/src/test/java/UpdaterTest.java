import albedo.ParallelUpdater;
import helpers.impls.UpdatableObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class UpdaterTest {

    private List<UpdatableObject> generateUpdatable(int count, Consumer<Double> onUpdate) {
        return Stream.generate(() -> new UpdatableObject(onUpdate))
                .limit(count)
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("Constructor work correctly")
    public void testConstructor() {
        assertDoesNotThrow(() -> new ParallelUpdater<>(1, (o, d) -> { }));

        try {
            new ParallelUpdater<>(0, (o, d) -> { });
            fail("Constructor should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'threadsCounts' must be greater than zero: 0", e.getMessage());
        }

        try {
            new ParallelUpdater<>(1, null);
            fail("Constructor should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'updateFunction' must be not null", e.getMessage());
        }
    }

    @ParameterizedTest(name = "#{index} objects count = {argumentsWithNames}")
    @DisplayName("All objects are updated (4 threads)")
    @ValueSource(ints = {0, 1, 3, 4, 20})
    @Timeout(1)
    public void testUpdate(int count) {
        var objects = generateUpdatable(count, d -> { });

        new ParallelUpdater<>(4, UpdatableObject::update)
                .update(objects, 1);

        objects.forEach(o -> assertEquals(1, o.updateCount(),
                "All object should be updated once (object count = " + count + ")"));
    }

    @Test
    @DisplayName("update() throw IllegalArgumentException")
    public void testUpdateRejectNull() {
        try {
            new ParallelUpdater<>(1, UpdatableObject::update).update(null, 0);
            fail("update() should reject null as argument");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'objects' must be not null", e.getMessage());
        }
    }

    @Test
    @DisplayName("update() throw IllegalArgumentException")
    public void testUpdateThrowError() {
        try {
            var updater = new ParallelUpdater<>(1, UpdatableObject::update);
            updater.shutdown();
            updater.update(generateUpdatable(1, d -> { }), 0);
            fail("update() should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This ParallelUpdater was shutdown", e.getMessage());
        }
    }

    @Test
    @DisplayName("shutdown() should interrupt execution")
    public void testShutdown() throws InterruptedException {
        var updater = new ParallelUpdater<>(1, UpdatableObject::update);

        var latch = new CountDownLatch(1);
        var objects = generateUpdatable(2, d -> {
            latch.countDown();
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) { }
        });

        new Thread(() -> updater.update(objects, 0)).start();
        latch.await();
        updater.shutdown();

        assertEquals(1, objects.get(0).updateCount(), "First object should be updated");
        assertEquals(0, objects.get(1).updateCount(), "Second object shouldn't be updated");
    }

    @ParameterizedTest(name = "delta = {argumentsWithNames}")
    @DisplayName("Should pass to onUpdate() correct delta")
    @ValueSource(doubles = { 0.0, 16.66 })
    public void testDelta(double delta) {
        var updater = new ParallelUpdater<>(1, UpdatableObject::update);
        var objects = generateUpdatable(3, d -> { });
        updater.update(objects, delta);

        objects.forEach(o -> assertEquals(delta, o.lastDelta()));
    }
}