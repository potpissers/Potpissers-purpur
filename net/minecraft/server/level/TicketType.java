package net.minecraft.server.level;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

public class TicketType<T> {
    private final String name;
    private final Comparator<T> comparator;
    private final long timeout;
    public static final TicketType<Unit> START = create("start", (unit1, unit2) -> 0);
    public static final TicketType<Unit> DRAGON = create("dragon", (unit1, unit2) -> 0);
    public static final TicketType<ChunkPos> PLAYER = create("player", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<ChunkPos> FORCED = create("forced", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<BlockPos> PORTAL = create("portal", Vec3i::compareTo, 300);
    public static final TicketType<ChunkPos> ENDER_PEARL = create("ender_pearl", Comparator.comparingLong(ChunkPos::toLong), 40);
    public static final TicketType<ChunkPos> UNKNOWN = create("unknown", Comparator.comparingLong(ChunkPos::toLong), 1);

    public static <T> TicketType<T> create(String name, Comparator<T> comparator) {
        return new TicketType<>(name, comparator, 0L);
    }

    public static <T> TicketType<T> create(String name, Comparator<T> comparator, int lifespan) {
        return new TicketType<>(name, comparator, lifespan);
    }

    protected TicketType(String name, Comparator<T> comparator, long timeout) {
        this.name = name;
        this.comparator = comparator;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public Comparator<T> getComparator() {
        return this.comparator;
    }

    public long timeout() {
        return this.timeout;
    }
}
