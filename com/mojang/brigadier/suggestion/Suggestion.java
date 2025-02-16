// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.mojang.brigadier.suggestion;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.StringRange;

import java.util.Objects;

public class Suggestion implements Comparable<Suggestion> {
    private final StringRange range;
    private final String text;
    private final Message tooltip;

    public Suggestion(final StringRange range, final String text) {
        this(range, text, null);
    }

    public Suggestion(final StringRange range, final String text, final Message tooltip) {
        this.range = range;
        this.text = text;
        this.tooltip = tooltip;
    }

    public StringRange getRange() {
        return range;
    }

    public String getText() {
        return text;
    }

    public Message getTooltip() {
        return tooltip;
    }

    public String apply(final String input) {
        if (range.getStart() == 0 && range.getEnd() == input.length()) {
            return text;
        }
        final StringBuilder result = new StringBuilder();
        if (range.getStart() > 0) {
            result.append(input.substring(0, range.getStart()));
        }
        result.append(text);
        if (range.getEnd() < input.length()) {
            result.append(input.substring(range.getEnd()));
        }
        return result.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Suggestion)) {
            return false;
        }
        final Suggestion that = (Suggestion) o;
        return Objects.equals(range, that.range) && Objects.equals(text, that.text) && Objects.equals(tooltip, that.tooltip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, text, tooltip);
    }

    @Override
    public String toString() {
        return "Suggestion{" +
            "range=" + range +
            ", text='" + text + '\'' +
            ", tooltip='" + tooltip + '\'' +
            '}';
    }

    // Paper start - fix unstable Suggestion comparison
    private static int compare0(final Suggestion lhs, final Suggestion rhs, final java.util.Comparator<String> textComparator) {
        if (lhs instanceof final IntegerSuggestion lis && rhs instanceof final IntegerSuggestion ris) {
            return Integer.compare(lis.getValue(), ris.getValue());
        } else if (lhs instanceof IntegerSuggestion) {
            return -1;
        } else if (rhs instanceof IntegerSuggestion) {
            return 1;
        } else {
            return textComparator.compare(lhs.text, rhs.text);
        }
    }
    // Paper end - fix unstable Suggestion comparison

    @Override
    public int compareTo(final Suggestion o) {
        return compare0(this, o, java.util.Comparator.naturalOrder()); // Paper - fix unstable Suggestion comparison
    }

    public int compareToIgnoreCase(final Suggestion b) {
        return compare0(this, b, String.CASE_INSENSITIVE_ORDER); // Paper - fix unstable Suggestion comparison
    }

    public Suggestion expand(final String command, final StringRange range) {
        if (range.equals(this.range)) {
            return this;
        }
        final StringBuilder result = new StringBuilder();
        if (range.getStart() < this.range.getStart()) {
            result.append(command.substring(range.getStart(), this.range.getStart()));
        }
        result.append(text);
        if (range.getEnd() > this.range.getEnd()) {
            result.append(command.substring(this.range.getEnd(), range.getEnd()));
        }
        return new Suggestion(range, result.toString(), tooltip);
    }
}
