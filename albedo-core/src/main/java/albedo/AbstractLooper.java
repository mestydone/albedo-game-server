package albedo;

/**
 * Base implementation of game loop. Allows get updates with fixed frequency.
 */
public abstract class AbstractLooper {
    private final Object lockObject = new Object(); // used to synchronize access to 'asyncThread' and 'state'

    private Thread asyncThread = null; // null if startAsync() will not be called
    private volatile boolean isAsync = false; // true if startAsync() was called
    private volatile States state = States.READY; // current state of this looper
    private final double expectedTickTime; // how many time in milliseconds ome tick should take (16.6... for 60 Hz)

    /**
     * Creates instance of {@link AbstractLooper}.
     *
     * @param frequency target frequency of updating. Must be greater than zero.
     * @throws IllegalArgumentException if {@code frequency} equal or less than zero.
     */
    public AbstractLooper(int frequency) {
        if (frequency <= 0)
            throw new IllegalArgumentException("Argument 'frequency' must be greater than zero: " + frequency);

        this.expectedTickTime = 1000.0 / frequency;
    }

    /**
     * Called before looper was started. If {@link #startAsync()} was called, this method
     * will run on {@link #asyncThread}.
     */
    public abstract void onStart();

    /**
     * Called on each iteration of looper.
     * @param delta ratio of real tick time to expected tick time. Usually a value close to 1.0.
     */
    public abstract void onUpdate(double delta);

    /**
     * Called after {@link #stop()} method was called or if {@link #onUpdate(double)} throw
     * an exception (except {@code InterruptedException}).
     */
    public abstract void onStop();


    /**
     * Starts this looper. Calls {@link #onStart()} and than run update loop.
     * This method blocks the thread.
     *
     * @throws InterruptedException if thread was interrupted while loop run.
     * @throws IllegalStateException if this looper already run or stopped, i.e. {@link #state}
     * not {@code States.READY}.
     */
    public final void start() throws InterruptedException{
        synchronized (lockObject) {
            state.errorIfNotReady();
            state = States.RUNNING;
        }

        onStart();

        try {
            loop();
        } catch (Exception e) {
            onStop();
            throw e;
        }
    }

    /**
     * Starts this looper in background thread. Methods {@link #onStart()} and {@link #onStop()}
     * will run also in background. Uses empty {@code Thread.UncaughtExceptionHandler}
     * as exception handler.
     */
    public final void startAsync() {
        startAsync((t, e) -> { /* nothing to do */ });
    }

    /**
     * Starts this looper in background thread. Methods {@link #onStart()} and {@link #onStop()}
     * will run also in background.
     *
     * @param handler exception handler for exception thrown in {@link #onUpdate(double)}.
     * @throws IllegalArgumentException if {@code handler} is null.
     */
    public final void startAsync(Thread.UncaughtExceptionHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException("Argument 'handler' must be not null");

        synchronized (lockObject) {
            state.errorIfNotReady();
            state = States.RUNNING;
            isAsync = true;

            asyncThread = new Thread(() -> {
                try {
                    AbstractLooper.this.onStart();
                    AbstractLooper.this.loop();
                } catch (InterruptedException ignored) {
                    // nothing to do if interrupted
                } finally {
                    AbstractLooper.this.stop();
                    AbstractLooper.this.onStop();
                }
            });

            asyncThread.setUncaughtExceptionHandler(handler);
            asyncThread.start();
        }
    }

    /**
     * Stops this looper and set {@link #state} to {@code States.STOPPED}.
     * Calls {@link #onStop()} on current thread or in background if looper was started
     * with {@link #startAsync()}.
     */
    public final void stop() {
        synchronized (lockObject) {
            if (state == States.STOPPED) return;

            state = States.STOPPED;
            if (asyncThread != null && !asyncThread.isInterrupted()) {
                asyncThread.interrupt();
            }
        }
    }

    /**
     * Starts update loop. It's doing iteration each {@link #expectedTickTime} milliseconds.
     * On each iteration calls {@link #onUpdate(double)}.
     *
     * @throws InterruptedException if thread was interrupted while loop run.
     */
    private void loop() throws InterruptedException {
        double tickTime = expectedTickTime;
        while (state == States.RUNNING && !Thread.interrupted()) {
            double tickStartTime = System.nanoTime() / 1e6;
            onUpdate(tickTime / expectedTickTime);

            double updateTime = (System.nanoTime() / 1e6) - tickStartTime;
            sleep(expectedTickTime - updateTime);

            tickTime = (System.nanoTime() / 1e6) - tickStartTime;
        }

        if (!isAsync) onStop(); // if async than onStop() will called in startAsync()
    }


    /**
     * Blocks thread for the specified time. Uses both sleep and busy waiting (empty while loop)
     * to achieve better accuracy.
     *
     * @param millis time to sleep.
     * @throws InterruptedException if thread was interrupted during sleep.
     */
    @SuppressWarnings("StatementWithEmptyBody") // empty while body
    private void sleep(double millis) throws InterruptedException {
        if (millis <= 0) return;

        final long lag = (long) 2e6;
        long continueTime = (long) (System.nanoTime() + millis * 1e6);
        long nanoTime;

        while ((nanoTime = System.nanoTime()) < continueTime) {
            if (nanoTime + lag < continueTime) {
                Thread.sleep(1);
            } else {
                while (System.nanoTime() < continueTime); // busy wait
                break;
            }
        }
    }

    /**
     * Describes possible states of looper.
     */
    private enum States {
        READY, RUNNING, STOPPED;

        /**
         * @throws IllegalStateException if state is not {@link #READY}.
         */
        void errorIfNotReady() {
            if (this == STOPPED) {
                throw new IllegalStateException("This looper was stopped");
            } else if (this == RUNNING) {
                throw new IllegalStateException("This looper is already running");
            }
        }
    }
}