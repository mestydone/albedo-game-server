package helpers.impls;

import albedo.AbstractLooper;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TestLoopImpl extends AbstractLooper {
    private Consumer<AbstractLooper> onStart, onStop;
    private BiConsumer<AbstractLooper, Double> onUpdate;

    public static TestLoopImpl one() {
        return new TestLoopImpl(1);
    }

    public TestLoopImpl(int frequency) {
        super(frequency);
    }

    public TestLoopImpl doOnStart(Consumer<AbstractLooper> onStart) {
        this.onStart = onStart;
        return this;
    }

    public TestLoopImpl doOnUpdate(BiConsumer<AbstractLooper, Double> onUpdate) {
        this.onUpdate = onUpdate;
        return this;
    }

    public TestLoopImpl doOnStop(Consumer<AbstractLooper> onStop) {
        this.onStop = onStop;
        return this;
    }

    @Override
    public void onStart() {
        if (onStart != null) onStart.accept(this);
    }

    @Override
    public void onUpdate(double delta) {
        if (onUpdate != null) onUpdate.accept(this, delta);
    }

    @Override
    public void onStop() {
        if (onStop != null) onStop.accept(this);
    }
}
