package net.minecraft.world.entity.schedule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import java.util.Collection;
import java.util.List;

public class Timeline {
    private final List<Keyframe> keyframes = Lists.newArrayList();
    private int previousIndex;

    public ImmutableList<Keyframe> getKeyframes() {
        return ImmutableList.copyOf(this.keyframes);
    }

    public Timeline addKeyframe(int duration, float active) {
        this.keyframes.add(new Keyframe(duration, active));
        this.sortAndDeduplicateKeyframes();
        return this;
    }

    public Timeline addKeyframes(Collection<Keyframe> frames) {
        this.keyframes.addAll(frames);
        this.sortAndDeduplicateKeyframes();
        return this;
    }

    private void sortAndDeduplicateKeyframes() {
        Int2ObjectSortedMap<Keyframe> map = new Int2ObjectAVLTreeMap<>();
        this.keyframes.forEach(keyframe -> map.put(keyframe.getTimeStamp(), keyframe));
        this.keyframes.clear();
        this.keyframes.addAll(map.values());
        this.previousIndex = 0;
    }

    public float getValueAt(int dayTime) {
        if (this.keyframes.size() <= 0) {
            return 0.0F;
        } else {
            Keyframe keyframe = this.keyframes.get(this.previousIndex);
            Keyframe keyframe1 = this.keyframes.get(this.keyframes.size() - 1);
            boolean flag = dayTime < keyframe.getTimeStamp();
            int i = flag ? 0 : this.previousIndex;
            float f = flag ? keyframe1.getValue() : keyframe.getValue();

            for (int i1 = i; i1 < this.keyframes.size(); i1++) {
                Keyframe keyframe2 = this.keyframes.get(i1);
                if (keyframe2.getTimeStamp() > dayTime) {
                    break;
                }

                this.previousIndex = i1;
                f = keyframe2.getValue();
            }

            return f;
        }
    }
}
