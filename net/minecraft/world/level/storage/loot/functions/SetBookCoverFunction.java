package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetBookCoverFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetBookCoverFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    Filterable.codec(Codec.string(0, 32)).optionalFieldOf("title").forGetter(setBookCoverFunction -> setBookCoverFunction.title),
                    Codec.STRING.optionalFieldOf("author").forGetter(setBookCoverFunction -> setBookCoverFunction.author),
                    ExtraCodecs.intRange(0, 3).optionalFieldOf("generation").forGetter(setBookCoverFunction -> setBookCoverFunction.generation)
                )
            )
            .apply(instance, SetBookCoverFunction::new)
    );
    private final Optional<String> author;
    private final Optional<Filterable<String>> title;
    private final Optional<Integer> generation;

    public SetBookCoverFunction(List<LootItemCondition> predicates, Optional<Filterable<String>> title, Optional<String> author, Optional<Integer> generation) {
        super(predicates);
        this.author = author;
        this.title = title;
        this.generation = generation;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return stack;
    }

    private WrittenBookContent apply(WrittenBookContent writtenBookContent) {
        return new WrittenBookContent(
            this.title.orElseGet(writtenBookContent::title),
            this.author.orElseGet(writtenBookContent::author),
            this.generation.orElseGet(writtenBookContent::generation),
            writtenBookContent.pages(),
            writtenBookContent.resolved()
        );
    }

    @Override
    public LootItemFunctionType<SetBookCoverFunction> getType() {
        return LootItemFunctions.SET_BOOK_COVER;
    }
}
