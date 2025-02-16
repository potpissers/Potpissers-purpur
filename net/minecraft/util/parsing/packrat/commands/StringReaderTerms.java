package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.stream.Stream;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;

public interface StringReaderTerms {
    static Term<StringReader> word(String value) {
        return new StringReaderTerms.TerminalWord(value);
    }

    static Term<StringReader> character(char value) {
        return new StringReaderTerms.TerminalCharacter(value);
    }

    public record TerminalCharacter(char value) implements Term<StringReader> {
        @Override
        public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
            parseState.input().skipWhitespace();
            int i = parseState.mark();
            if (parseState.input().canRead() && parseState.input().read() == this.value) {
                return true;
            } else {
                parseState.errorCollector()
                    .store(
                        i,
                        parseState1 -> Stream.of(String.valueOf(this.value)),
                        CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().create(this.value)
                    );
                return false;
            }
        }
    }

    public record TerminalWord(String value) implements Term<StringReader> {
        @Override
        public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
            parseState.input().skipWhitespace();
            int i = parseState.mark();
            String unquotedString = parseState.input().readUnquotedString();
            if (!unquotedString.equals(this.value)) {
                parseState.errorCollector()
                    .store(i, parseState1 -> Stream.of(this.value), CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().create(this.value));
                return false;
            } else {
                return true;
            }
        }
    }
}
