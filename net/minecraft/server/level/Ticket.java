package net.minecraft.server.level;

import java.util.Objects;

public final class Ticket<T> implements Comparable<Ticket<?>>, ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket<T> { // Paper - rewrite chunk system
    private final TicketType<T> type;
    private final int ticketLevel;
    public final T key;
    // Paper start - rewrite chunk system
    private long removeDelay;

    @Override
    public final long moonrise$getRemoveDelay() {
        return this.removeDelay;
    }

    @Override
    public final void moonrise$setRemoveDelay(final long removeDelay) {
        this.removeDelay = removeDelay;
    }
    // Paper end - rewrite chunk system

    public Ticket(TicketType<T> type, int ticketLevel, T key) { // Paper - public
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
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] to die in " + this.removeDelay; // Paper - rewrite chunk system
    }

    public TicketType<T> getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    protected void setCreatedTick(long timestamp) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean timedOut(long currentTime) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }
}
