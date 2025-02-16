package net.minecraft.server.level;

import java.util.Objects;

public final class Ticket<T> implements Comparable<Ticket<?>> {
    private final TicketType<T> type;
    private final int ticketLevel;
    private final T key;
    private long createdTick;

    protected Ticket(TicketType<T> type, int ticketLevel, T key) {
        this.type = type;
        this.ticketLevel = ticketLevel;
        this.key = key;
    }

    @Override
    public int compareTo(Ticket<?> other) {
        int i = Integer.compare(this.ticketLevel, other.ticketLevel);
        if (i != 0) {
            return i;
        } else {
            int i1 = Integer.compare(System.identityHashCode(this.type), System.identityHashCode(other.type));
            return i1 != 0 ? i1 : this.type.getComparator().compare(this.key, (T)other.key);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof Ticket<?> ticket
                && this.ticketLevel == ticket.ticketLevel
                && Objects.equals(this.type, ticket.type)
                && Objects.equals(this.key, ticket.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.ticketLevel, this.key);
    }

    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] at " + this.createdTick;
    }

    public TicketType<T> getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    protected void setCreatedTick(long timestamp) {
        this.createdTick = timestamp;
    }

    protected boolean timedOut(long currentTime) {
        long timeout = this.type.timeout();
        return timeout != 0L && currentTime - this.createdTick > timeout;
    }
}
