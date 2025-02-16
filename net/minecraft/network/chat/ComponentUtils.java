package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;

public class ComponentUtils {
    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    public static MutableComponent mergeStyles(MutableComponent component, Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style style1 = component.getStyle();
            if (style1.isEmpty()) {
                return component.setStyle(style);
            } else {
                return style1.equals(style) ? component : component.setStyle(style1.applyTo(style));
            }
        }
    }

    public static Optional<MutableComponent> updateForEntity(
        @Nullable CommandSourceStack commandSourceStack, Optional<Component> optionalComponent, @Nullable Entity entity, int recursionDepth
    ) throws CommandSyntaxException {
        return optionalComponent.isPresent()
            ? Optional.of(updateForEntity(commandSourceStack, optionalComponent.get(), entity, recursionDepth))
            : Optional.empty();
    }

    public static MutableComponent updateForEntity(
        @Nullable CommandSourceStack commandSourceStack, Component component, @Nullable Entity entity, int recursionDepth
    ) throws CommandSyntaxException {
        if (recursionDepth > 100) {
            return component.copy();
        } else {
            MutableComponent mutableComponent = component.getContents().resolve(commandSourceStack, entity, recursionDepth + 1);

            for (Component component1 : component.getSiblings()) {
                mutableComponent.append(updateForEntity(commandSourceStack, component1, entity, recursionDepth + 1));
            }

            return mutableComponent.withStyle(resolveStyle(commandSourceStack, component.getStyle(), entity, recursionDepth));
        }
    }

    private static Style resolveStyle(@Nullable CommandSourceStack commandSourceStack, Style style, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent != null) {
            Component component = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
            if (component != null) {
                HoverEvent hoverEvent1 = new HoverEvent(HoverEvent.Action.SHOW_TEXT, updateForEntity(commandSourceStack, component, entity, recursionDepth + 1));
                return style.withHoverEvent(hoverEvent1);
            }
        }

        return style;
    }

    public static Component formatList(Collection<String> elements) {
        return formatAndSortList(elements, content -> Component.literal(content).withStyle(ChatFormatting.GREEN));
    }

    public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> elements, Function<T, Component> componentExtractor) {
        if (elements.isEmpty()) {
            return CommonComponents.EMPTY;
        } else if (elements.size() == 1) {
            return componentExtractor.apply(elements.iterator().next());
        } else {
            List<T> list = Lists.newArrayList(elements);
            list.sort(Comparable::compareTo);
            return formatList(list, componentExtractor);
        }
    }

    public static <T> Component formatList(Collection<? extends T> elements, Function<T, Component> componentExtractor) {
        return formatList(elements, DEFAULT_SEPARATOR, componentExtractor);
    }

    public static <T> MutableComponent formatList(
        Collection<? extends T> elements, Optional<? extends Component> optionalSeparator, Function<T, Component> componentExtractor
    ) {
        return formatList(elements, DataFixUtils.orElse(optionalSeparator, DEFAULT_SEPARATOR), componentExtractor);
    }

    public static Component formatList(Collection<? extends Component> elements, Component separator) {
        return formatList(elements, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(Collection<? extends T> elements, Component separator, Function<T, Component> componentExtractor) {
        if (elements.isEmpty()) {
            return Component.empty();
        } else if (elements.size() == 1) {
            return componentExtractor.apply((T)elements.iterator().next()).copy();
        } else {
            MutableComponent mutableComponent = Component.empty();
            boolean flag = true;

            for (T object : elements) {
                if (!flag) {
                    mutableComponent.append(separator);
                }

                mutableComponent.append(componentExtractor.apply(object));
                flag = false;
            }

            return mutableComponent;
        }
    }

    public static MutableComponent wrapInSquareBrackets(Component toWrap) {
        return Component.translatable("chat.square_brackets", toWrap);
    }

    public static Component fromMessage(Message message) {
        return (Component)(message instanceof Component ? (Component)message : Component.literal(message.getString()));
    }

    public static boolean isTranslationResolvable(@Nullable Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents translatableContents) {
            String key = translatableContents.getKey();
            String fallback = translatableContents.getFallback();
            return fallback != null || Language.getInstance().has(key);
        } else {
            return true;
        }
    }

    public static MutableComponent copyOnClickText(String text) {
        return wrapInSquareBrackets(
            Component.literal(text)
                .withStyle(
                    style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                        .withInsertion(text)
                )
        );
    }
}
