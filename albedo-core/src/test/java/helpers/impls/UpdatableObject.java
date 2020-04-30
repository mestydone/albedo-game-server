package helpers.impls;

import java.util.function.Consumer;

/**
 * Helps to test {@link albedo.ParallelUpdater}
 */
public class UpdatableObject {

    private Consumer<Double> onUpdate;
    private int updateCount = 0;
    private double lastDelta = Double.POSITIVE_INFINITY;

    public UpdatableObject(Consumer<Double> onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void update(double delta) {
        updateCount++;
        lastDelta = delta;
        onUpdate.accept(delta);
    }

    public int updateCount() {
        return updateCount;
    }

    public double lastDelta() {
        return lastDelta;
    }
}
