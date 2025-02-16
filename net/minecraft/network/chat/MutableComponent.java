package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

public class MutableComponent implements Component {
    private final ComponentContents contents;
    private final List<Component> siblings;
    private Style style;
    private FormattedCharSequence visualOrderText = FormattedCharSequence.EMPTY;
    @Nullable
    private Language decomposedWith;

    MutableComponent(ComponentContents contents, List<Component> siblings, Style style) {
        this.contents = contents;
        this.siblings = siblings;
        this.style = style;
    }

    public static MutableComponent create(ComponentContents contents) {
        return new MutableComponent(contents, Lists.newArrayList(), Style.EMPTY);
    }

    @Override
    public ComponentContents getContents() {
        return this.contents;
    }

    @Override
    public List<Component> getSiblings() {
        return this.siblings;
    }

    public MutableComponent setStyle(Style style) {
        this.style = style;
        return this;
    }

    @Override
    public Style getStyle() {
        return this.style;
    }

    public MutableComponent append(String string) {
        return string.isEmpty() ? this : this.append(Component.literal(string));
    }

    public MutableComponent append(Component sibling) {
        this.siblings.add(sibling);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> modifyFunc) {
        this.setStyle(modifyFunc.apply(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(Style style) {
        this.setStyle(style.applyTo(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formats) {
        this.setStyle(this.getStyle().applyFormats(formats));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting format) {
        this.setStyle(this.getStyle().applyFormat(format));
        return this;
    }

    public MutableComponent withColor(int color) {
        this.setStyle(this.getStyle().withColor(color));
        return this;
    }

    @Override
    public FormattedCharSequence getVisualOrderText() {
        Language instance = Language.getInstance();
        if (this.decomposedWith != instance) {
            this.visualOrderText = instance.getVisualOrder(this);
            this.decomposedWith = instance;
        }

        return this.visualOrderText;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof MutableComponent mutableComponent
                && this.contents.equals(mutableComponent.contents)
                && this.style.equals(mutableComponent.style)
                && this.siblings.equals(mutableComponent.siblings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.contents, this.style, this.siblings);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(this.contents.toString());
        boolean flag = !this.style.isEmpty();
        boolean flag1 = !this.siblings.isEmpty();
        if (flag || flag1) {
            stringBuilder.append('[');
            if (flag) {
                stringBuilder.append("style=");
                stringBuilder.append(this.style);
            }

            if (flag && flag1) {
                stringBuilder.append(", ");
            }

            if (flag1) {
                stringBuilder.append("siblings=");
                stringBuilder.append(this.siblings);
            }

            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }
}
