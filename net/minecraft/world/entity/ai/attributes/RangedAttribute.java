package net.minecraft.world.entity.ai.attributes;

import net.minecraft.util.Mth;

public class RangedAttribute extends Attribute {
    private final double minValue;
    public double maxValue;

    public RangedAttribute(String descriptionId, double defaultValue, double min, double max) {
        super(descriptionId, defaultValue);
        this.minValue = min;
        this.maxValue = max;
        if (min > max) {
            throw new IllegalArgumentException("Minimum value cannot be bigger than maximum value!");
        } else if (defaultValue < min) {
            throw new IllegalArgumentException("Default value cannot be lower than minimum value!");
        } else if (defaultValue > max) {
            throw new IllegalArgumentException("Default value cannot be bigger than maximum value!");
        }
    }

    public double getMinValue() {
        return this.minValue;
    }

    public double getMaxValue() {
        return this.maxValue;
    }

    @Override
    public double sanitizeValue(double value) {
        if (!org.purpurmc.purpur.PurpurConfig.clampAttributes) return Double.isNaN(value) ? this.minValue : value; // Purpur - Add attribute clamping and armor limit config
        return Double.isNaN(value) ? this.minValue : Mth.clamp(value, this.minValue, this.maxValue);
    }
}
