package net.minecraft.commands.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;

public record StringTemplate(List<String> segments, List<String> variables) {
    public static StringTemplate fromString(String name, int lineNumber) {
        Builder<String> builder = ImmutableList.builder();
        Builder<String> builder1 = ImmutableList.builder();
        int len = name.length();
        int i = 0;
        int index = name.indexOf(36);

        while (index != -1) {
            if (index != len - 1 && name.charAt(index + 1) == '(') {
                builder.add(name.substring(i, index));
                int index1 = name.indexOf(41, index + 1);
                if (index1 == -1) {
                    throw new IllegalArgumentException("Unterminated macro variable in macro '" + name + "' on line " + lineNumber);
                }

                String sub = name.substring(index + 2, index1);
                if (!isValidVariableName(sub)) {
                    throw new IllegalArgumentException("Invalid macro variable name '" + sub + "' on line " + lineNumber);
                }

                builder1.add(sub);
                i = index1 + 1;
                index = name.indexOf(36, i);
            } else {
                index = name.indexOf(36, index + 1);
            }
        }

        if (i == 0) {
            throw new IllegalArgumentException("Macro without variables on line " + lineNumber);
        } else {
            if (i != len) {
                builder.add(name.substring(i));
            }

            return new StringTemplate(builder.build(), builder1.build());
        }
    }

    private static boolean isValidVariableName(String variableName) {
        for (int i = 0; i < variableName.length(); i++) {
            char c = variableName.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }

        return true;
    }

    public String substitute(List<String> arguments) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < this.variables.size(); i++) {
            stringBuilder.append(this.segments.get(i)).append(arguments.get(i));
            CommandFunction.checkCommandLineLength(stringBuilder);
        }

        if (this.segments.size() > this.variables.size()) {
            stringBuilder.append(this.segments.get(this.segments.size() - 1));
        }

        CommandFunction.checkCommandLineLength(stringBuilder);
        return stringBuilder.toString();
    }
}
