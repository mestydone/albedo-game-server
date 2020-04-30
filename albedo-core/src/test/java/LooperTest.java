import helpers.impls.TestLoopImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LooperTest {

    @Test
    @DisplayName("Constructor is working correctly")
    public void testConstructor() {
        assertDoesNotThrow(() -> new TestLoopImpl(1));

        try {
            new TestLoopImpl(0);
            fail("Constructor should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'frequency' must be greater than zero: 0", e.getMessage());
        }
    }

    @Test
    @DisplayName("Throws an exception if stopped")
    @Timeout(1)
    public void testErrorIfStopped() throws InterruptedException {
        try {
            var looper = new TestLoopImpl(1);
            looper.stop();
            looper.start();
            fail("start() should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This looper was stopped", e.getMessage());
        }

        try {
            var looper = new TestLoopImpl(1);
            looper.stop();
            looper.startAsync();
            fail("startAsync() should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This looper was stopped", e.getMessage());
        }

        try {
            var looper = new TestLoopImpl(1);
            looper.stop();
            looper.startAsync((t, e) -> {
            });
            fail("startAsync(handler) should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This looper was stopped", e.getMessage());
        }
    }

    @Test
    @DisplayName("Throws an exception if already running")
    @Timeout(1)
    public void testErrorIfRunning() throws InterruptedException {

        // startAsync() -> start()
        var looper1 = new TestLoopImpl(1);
        try {
            looper1.startAsync();
            looper1.start();
            fail("start() should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This looper is already running", e.getMessage());
        } finally {
            looper1.stop();
        }

        // start() -> startAsync()
        var looper2 = new TestLoopImpl(1);
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            looper2.doOnStart(l -> latch.countDown());

            new Thread(() -> {
                try {
                    looper2.start();
                } catch (InterruptedException ignored) { }
            }).start();

            latch.await();

            looper2.startAsync();
            fail("startAsync() should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("This looper is already running", e.getMessage());
        } finally {
            looper2.stop();
        }
    }


    @Test
    @DisplayName("Throws an exception if startAsync() receives null as an argument")
    public void testRejectNullInStartAsync() {
        try {
            TestLoopImpl.one().startAsync(null);
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'handler' must be not null", e.getMessage());
        }
    }

    @ParameterizedTest(name = "throw an exception = {argumentsWithNames}")
    @DisplayName("onStart, onUpdate, onStop work correctly in sync mode")
    @ValueSource(booleans = { false, true })
    @Timeout(1)
    public void testEventsSync(boolean throwException) {
        AtomicInteger onStart = new AtomicInteger(),
                      onUpdate = new AtomicInteger(),
                      onStop = new AtomicInteger();

        try {
            new TestLoopImpl(100)
                    .doOnStart(l -> onStart.incrementAndGet())
                    .doOnUpdate((l, d) -> {
                        onUpdate.incrementAndGet();
                        if (throwException)
                            throw new NullPointerException("Test!");
                        else
                            l.stop();
                    })
                    .doOnStop(l -> onStop.incrementAndGet())
                    .start();
        } catch (Exception e) {
            if (!e.getMessage().equals("Test!")) fail(e);
        }

        assertEquals(1, onStart.get(), "onStart should be called once");
        assertEquals(1, onUpdate.get(), "onUpdate should be called once");
        assertEquals(1, onStop.get(), "onStop should be called once");
    }

    @ParameterizedTest(name = "throw an exception = {argumentsWithNames}")
    @DisplayName("onStart, onUpdate, onStop work correctly in async mode")
    @ValueSource(booleans = { false, true })
    @Timeout(1)
    public void testEventsAsync(boolean throwException) throws InterruptedException {
        AtomicInteger onStart = new AtomicInteger(),
                      onUpdate = new AtomicInteger(),
                      onStop = new AtomicInteger();

        CountDownLatch latch = new CountDownLatch(1);

        new TestLoopImpl(100)
                .doOnStart(l -> onStart.incrementAndGet())
                .doOnUpdate((l, d) -> {
                    onUpdate.incrementAndGet();
                    if (throwException)
                        throw new NullPointerException("Test!");
                    else
                        l.stop();
                })
                .doOnStop(l -> {
                    onStop.incrementAndGet();
                    latch.countDown();
                })
                .startAsync();

        latch.await();

        assertEquals(1, onStart.get(), "onStart should be called once");
        assertEquals(1, onUpdate.get(), "onUpdate should be called once");
        assertEquals(1, onStop.get(), "onStop should be called once");
    }

    @Test
    @DisplayName("Should catch exception in async mode")
    @Timeout(1)
    public void testCatchExceptionAsync() throws InterruptedException {
        final String errorMessage = "Some terrible error";
        Runnable throwError = () -> {
            throw new NullPointerException(errorMessage);
        };

        var loopers = new TestLoopImpl[]{
                TestLoopImpl.one().doOnStart(l -> throwError.run()),
                TestLoopImpl.one().doOnUpdate((l, d) -> throwError.run()),
                TestLoopImpl.one().doOnUpdate((l, d) -> l.stop()).doOnStop(l -> throwError.run())
        };

        var hints = new String[]{
                "Exception in onStart()",
                "Exception in onUpdate()",
                "Exception in onStop()",
        };

        for (int i = 0; i < 3; i++) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> ref = new AtomicReference<>();

            loopers[i].startAsync((t, e) -> {
                ref.set(e);
                latch.countDown();
            });

            latch.await();

            assertEquals(errorMessage, ref.get().getMessage(), hints[i]);
        }
    }
}