package main;

public class LamportClock {
    private int clock;

    public LamportClock() {
        this.clock = 0;
    }

    // Increment the clock on internal events
    public void increment() {
        clock++;
    }

    // Update the clock on receiving a message
    public void update(int receivedClock) {
        clock = Math.max(clock, receivedClock) + 1;
    }

    // Get the current clock value
    public int getClock() {
        return clock;
    }
}
