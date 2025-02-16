package net.minecraft.world.ticks;

public enum TickPriority {
    EXTREMELY_HIGH(-3),
    VERY_HIGH(-2),
    HIGH(-1),
    NORMAL(0),
    LOW(1),
    VERY_LOW(2),
    EXTREMELY_LOW(3);

    private final int value;

    private TickPriority(final int value) {
        this.value = value;
    }

    public static TickPriority byValue(int priority) {
        for (TickPriority tickPriority : values()) {
            if (tickPriority.value == priority) {
                return tickPriority;
            }
        }

        return priority < EXTREMELY_HIGH.value ? EXTREMELY_HIGH : EXTREMELY_LOW;
    }

    public int getValue() {
        return this.value;
    }
}
