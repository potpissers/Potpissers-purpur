package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class MerchantOffer {
    public static final Codec<MerchantOffer> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ItemCost.CODEC.fieldOf("buy").forGetter(merchantOffer -> merchantOffer.baseCostA),
                ItemCost.CODEC.lenientOptionalFieldOf("buyB").forGetter(merchantOffer -> merchantOffer.costB),
                ItemStack.CODEC.fieldOf("sell").forGetter(merchantOffer -> merchantOffer.result),
                Codec.INT.lenientOptionalFieldOf("uses", Integer.valueOf(0)).forGetter(merchantOffer -> merchantOffer.uses),
                Codec.INT.lenientOptionalFieldOf("maxUses", Integer.valueOf(4)).forGetter(merchantOffer -> merchantOffer.maxUses),
                Codec.BOOL.lenientOptionalFieldOf("rewardExp", Boolean.valueOf(true)).forGetter(merchantOffer -> merchantOffer.rewardExp),
                Codec.INT.lenientOptionalFieldOf("specialPrice", Integer.valueOf(0)).forGetter(merchantOffer -> merchantOffer.specialPriceDiff),
                Codec.INT.lenientOptionalFieldOf("demand", Integer.valueOf(0)).forGetter(merchantOffer -> merchantOffer.demand),
                Codec.FLOAT.lenientOptionalFieldOf("priceMultiplier", Float.valueOf(0.0F)).forGetter(merchantOffer -> merchantOffer.priceMultiplier),
                Codec.INT.lenientOptionalFieldOf("xp", Integer.valueOf(1)).forGetter(merchantOffer -> merchantOffer.xp)
            )
            .apply(instance, MerchantOffer::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffer> STREAM_CODEC = StreamCodec.of(
        MerchantOffer::writeToStream, MerchantOffer::createFromStream
    );
    private final ItemCost baseCostA;
    private final Optional<ItemCost> costB;
    private final ItemStack result;
    private int uses;
    private final int maxUses;
    private final boolean rewardExp;
    private int specialPriceDiff;
    private int demand;
    private final float priceMultiplier;
    private final int xp;

    private MerchantOffer(
        ItemCost baseCostA,
        Optional<ItemCost> costB,
        ItemStack result,
        int _uses,
        int maxUses,
        boolean rewardExp,
        int specialPriceDiff,
        int demand,
        float priceMultiplier,
        int xp
    ) {
        this.baseCostA = baseCostA;
        this.costB = costB;
        this.result = result;
        this.uses = _uses;
        this.maxUses = maxUses;
        this.rewardExp = rewardExp;
        this.specialPriceDiff = specialPriceDiff;
        this.demand = demand;
        this.priceMultiplier = priceMultiplier;
        this.xp = xp;
    }

    public MerchantOffer(ItemCost baseCostA, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, Optional.empty(), result, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, 0, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int _uses, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, _uses, maxUses, xp, priceMultiplier, 0);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int _uses, int maxUses, int xp, float priceMultiplier, int demand) {
        this(baseCostA, costB, result, _uses, maxUses, true, 0, demand, priceMultiplier, xp);
    }

    private MerchantOffer(MerchantOffer other) {
        this(
            other.baseCostA,
            other.costB,
            other.result.copy(),
            other.uses,
            other.maxUses,
            other.rewardExp,
            other.specialPriceDiff,
            other.demand,
            other.priceMultiplier,
            other.xp
        );
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA.itemStack();
    }

    public ItemStack getCostA() {
        return this.baseCostA.itemStack().copyWithCount(this.getModifiedCostCount(this.baseCostA));
    }

    private int getModifiedCostCount(ItemCost itemCost) {
        int count = itemCost.count();
        int max = Math.max(0, Mth.floor(count * this.demand * this.priceMultiplier));
        return Mth.clamp(count + max + this.specialPriceDiff, 1, itemCost.itemStack().getMaxStackSize());
    }

    public ItemStack getCostB() {
        return this.costB.map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
    }

    public ItemCost getItemCostA() {
        return this.baseCostA;
    }

    public Optional<ItemCost> getItemCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = this.demand + this.uses - (this.maxUses - this.uses);
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        this.uses++;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int add) {
        this.specialPriceDiff += add;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int price) {
        this.specialPriceDiff = price;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public boolean satisfiedBy(ItemStack playerOfferA, ItemStack playerOfferB) {
        if (!this.baseCostA.test(playerOfferA) || playerOfferA.getCount() < this.getModifiedCostCount(this.baseCostA)) {
            return false;
        } else {
            return !this.costB.isPresent()
                ? playerOfferB.isEmpty()
                : this.costB.get().test(playerOfferB) && playerOfferB.getCount() >= this.costB.get().count();
        }
    }

    public boolean take(ItemStack playerOfferA, ItemStack playerOfferB) {
        if (!this.satisfiedBy(playerOfferA, playerOfferB)) {
            return false;
        } else {
            playerOfferA.shrink(this.getCostA().getCount());
            if (!this.getCostB().isEmpty()) {
                playerOfferB.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }

    private static void writeToStream(RegistryFriendlyByteBuf buffer, MerchantOffer offer) {
        ItemCost.STREAM_CODEC.encode(buffer, offer.getItemCostA());
        ItemStack.STREAM_CODEC.encode(buffer, offer.getResult());
        ItemCost.OPTIONAL_STREAM_CODEC.encode(buffer, offer.getItemCostB());
        buffer.writeBoolean(offer.isOutOfStock());
        buffer.writeInt(offer.getUses());
        buffer.writeInt(offer.getMaxUses());
        buffer.writeInt(offer.getXp());
        buffer.writeInt(offer.getSpecialPriceDiff());
        buffer.writeFloat(offer.getPriceMultiplier());
        buffer.writeInt(offer.getDemand());
    }

    public static MerchantOffer createFromStream(RegistryFriendlyByteBuf buffer) {
        ItemCost itemCost = ItemCost.STREAM_CODEC.decode(buffer);
        ItemStack itemStack = ItemStack.STREAM_CODEC.decode(buffer);
        Optional<ItemCost> optional = ItemCost.OPTIONAL_STREAM_CODEC.decode(buffer);
        boolean _boolean = buffer.readBoolean();
        int _int = buffer.readInt();
        int _int1 = buffer.readInt();
        int _int2 = buffer.readInt();
        int _int3 = buffer.readInt();
        float _float = buffer.readFloat();
        int _int4 = buffer.readInt();
        MerchantOffer merchantOffer = new MerchantOffer(itemCost, optional, itemStack, _int, _int1, _int2, _float, _int4);
        if (_boolean) {
            merchantOffer.setToOutOfStock();
        }

        merchantOffer.setSpecialPriceDiff(_int3);
        return merchantOffer;
    }
}
