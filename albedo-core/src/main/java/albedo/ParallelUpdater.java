package albedo;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Helps to process the list of objects concurrently.
 * Pass the list of objects to method {@link #update(List, double)} and it's will be divided
 * into equal parts, which will be processed in parallel.
 *
 * Method {@link #update(List, double)} can be used several times, but after method
 * {@link #shutdown()} was called this instance can not longer be used.
 *
 * @param <T> type of processing objects.
 */
public class ParallelUpdater<T> {
    private final Lock lock = new ReentrantLock(); // used to synchronize access to 'executor' and 'isShutdown'

    private boolean isShutdown = false; // true when shutdown() was called. If true than instance cannot be used anymore
    private final int threadsCount;     // number of threads used by ThreadPoolExecutor
    private final ThreadPoolExecutor executor;  // used to process objects concurrently
    private final BiConsumer<T, Double> updateFunction; // specifies what to do with objects

    /**
     * Creates instance of ParallelUpdater.
     *
     * @param threadsCounts number of threads used to processing objects.
     * @param updateFunction defines what to do with each object. First argument
     *                       of {@code BiConsumer.accept(T, Double)} is object from
     *                       list, passed to {@link #update(List, double)} as first argument.
     *                       Second argument is {@code delta} passed to {@link #update(List, double)}
     *                       as second argument.
     *
     * @throws IllegalArgumentException if {@link #threadsCount} is equal of less than zero
     * or if {@link #updateFunction} is null
     */
    public ParallelUpdater(int threadsCounts, BiConsumer<T, Double> updateFunction) {
        if (threadsCounts < 1)
            throw new IllegalArgumentException("Argument 'threadsCounts' must be greater than zero: " + threadsCounts);

        if (updateFunction == null)
            throw new IllegalArgumentException("Argument 'updateFunction' must be not null");

        this.threadsCount = threadsCounts;
        this.updateFunction = updateFunction;
        this.executor = new ThreadPoolExecutor(threadsCounts, threadsCounts,
                1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    /**
     * Processes each object from {@code objects} list as {@link #updateFunction} defines.
     * Divides {@code objects} list into equal parts and processes they concurrently.
     * However, call of this method block current thread. The method will return control only after
     * all objects have been processed.
     *
     * @param objects list of objects that should be processed. Since this method use List.get(index)
     *                to access items it's recommended to use ArrayList as list implementation.
     * @param delta value that each objects should receive
     *
     * @throws IllegalArgumentException if {@code objects} is null
     * @throws IllegalStateException if instance was shutdown
     */
    public void update(final List<T> objects, final double delta) {
        if (objects == null)
            throw new IllegalArgumentException("Argument 'objects' must be not null");

        lock.lock();   // <--- lock

        if (isShutdown) {
            lock.unlock(); // <--- unlock
            throw new IllegalStateException("This ParallelUpdater was shutdown");
        }

        final var deltaBoxed = Double.valueOf(delta);
        final var latch = new CountDownLatch(threadsCount);
        for (int threadNum = 0; threadNum < threadsCount; threadNum++) {
            final int fromIndex = fromIndex(objects.size(), threadNum);
            final int toIndex = toIndex(objects.size(), threadNum);

            executor.execute(() -> {
                for (int i = fromIndex; i < toIndex; i++) {
                    updateFunction.accept(objects.get(i), deltaBoxed);
                }
                latch.countDown();
            });
        }

        lock.unlock(); // <--- unlock

        try {
            latch.await();
        } catch (InterruptedException ignored) { /* nothing to do */ }
    }

    /**
     * Stops processing of objects and makes this instance unusable.
     *
     * @throws SecurityException if {@code ThreadPoolExecutor.shutdown()} throw an exception.
     */
    public void shutdown() {
        lock.lock();   // <--- lock

        try {
            executor.shutdown();
        } finally {
            isShutdown = true;
            lock.unlock(); // <--- unlock
        }
    }

    /**
     * Calculates index of first item of list that should be processed by
     * thread with ordinal number {@code threadNum}.
     *
     * @param listSize size of list
     * @param threadNum ordinal number of thread. Lies between 0 and {@link #threadsCount}
     * @return index of first item to be be processed this thread
     *
     * @see #toIndex(int, int)
     */
    private int fromIndex(int listSize, int threadNum) {
        if (listSize < this.threadsCount) {
            if (listSize > threadNum) {
                return threadNum;
            } else {
                return 0;
            }
        } else {
            int sectionLen = listSize / this.threadsCount;
            return threadNum * sectionLen;
        }
    }

    /**
     * Calculates index of last item of list that should be processed by
     * thread with ordinal number {@code threadNum}.
     *
     * @param listSize size of list
     * @param threadNum ordinal number of thread. Lies between 0 and {@link #threadsCount}
     * @return index of last item to be be processed this thread
     *
     * @see #fromIndex(int, int)
     */
    private int toIndex(int listSize, int threadNum) {
        if (listSize < this.threadsCount) {
            if (listSize > threadNum) {
                return threadNum + 1;
            } else {
                return 0;
            }
        } else {
            int sectionLen = listSize / this.threadsCount;
            return (threadNum == this.threadsCount - 1) ? listSize : threadNum * sectionLen + sectionLen;
        }
    }
}
