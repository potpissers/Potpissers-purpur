package net.minecraft.util.parsing.packrat;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.mutable.MutableBoolean;

public interface Term<S> {
    boolean parse(ParseState<S> parseState, Scope scope, Control control);

    static <S> Term<S> named(Atom<?> name) {
        return new Term.Reference<>(name);
    }

    static <S, T> Term<S> marker(Atom<T> name, T value) {
        return new Term.Marker<>(name, value);
    }

    @SafeVarargs
    static <S> Term<S> sequence(Term<S>... elements) {
        return new Term.Sequence<>(List.of(elements));
    }

    @SafeVarargs
    static <S> Term<S> alternative(Term<S>... elements) {
        return new Term.Alternative<>(List.of(elements));
    }

    static <S> Term<S> optional(Term<S> term) {
        return new Term.Maybe<>(term);
    }

    static <S> Term<S> cut() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
                control.cut();
                return true;
            }

            @Override
            public String toString() {
                return "↑";
            }
        };
    }

    static <S> Term<S> empty() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
                return true;
            }

            @Override
            public String toString() {
                return "ε";
            }
        };
    }

    public record Alternative<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
            MutableBoolean mutableBoolean = new MutableBoolean();
            Control control1 = mutableBoolean::setTrue;
            int i = parseState.mark();

            for (Term<S> term : this.elements) {
                if (mutableBoolean.isTrue()) {
                    break;
                }

                Scope scope1 = new Scope();
                if (term.parse(parseState, scope1, control1)) {
                    scope.putAll(scope1);
                    return true;
                }

                parseState.restore(i);
            }

            return false;
        }
    }

    public record Marker<S, T>(Atom<T> name, T value) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
            scope.put(this.name, this.value);
            return true;
        }
    }

    public record Maybe<S>(Term<S> term) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
            int i = parseState.mark();
            if (!this.term.parse(parseState, scope, control)) {
                parseState.restore(i);
            }

            return true;
        }
    }

    public record Reference<S, T>(Atom<T> name) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
            Optional<T> optional = parseState.parse(this.name);
            if (optional.isEmpty()) {
                return false;
            } else {
                scope.put(this.name, optional.get());
                return true;
            }
        }
    }

    public record Sequence<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
            int i = parseState.mark();

            for (Term<S> term : this.elements) {
                if (!term.parse(parseState, scope, control)) {
                    parseState.restore(i);
                    return false;
                }
            }

            return true;
        }
    }
}
