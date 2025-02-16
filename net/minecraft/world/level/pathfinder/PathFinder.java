package net.minecraft.world.level.pathfinder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;

public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private int maxVisitedNodes;
    public final NodeEvaluator nodeEvaluator;
    private static final boolean DEBUG = false;
    private final BinaryHeap openSet = new BinaryHeap();

    public PathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    @Nullable
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targetPositions, float maxRange, int accuracy, float searchDepthMultiplier) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(region, mob);
        Node start = this.nodeEvaluator.getStart();
        if (start == null) {
            return null;
        } else {
            Map<Target, BlockPos> map = targetPositions.stream()
                .collect(Collectors.toMap(pos -> this.nodeEvaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()), Function.identity()));
            Path path = this.findPath(start, map, maxRange, accuracy, searchDepthMultiplier);
            this.nodeEvaluator.done();
            return path;
        }
    }

    @Nullable
    private Path findPath(Node node, Map<Target, BlockPos> targetPositions, float maxRange, int accuracy, float searchDepthMultiplier) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("find_path");
        profilerFiller.markForCharting(MetricCategory.PATH_FINDING);
        Set<Target> set = targetPositions.keySet();
        node.g = 0.0F;
        node.h = this.getBestH(node, set);
        node.f = node.h;
        this.openSet.clear();
        this.openSet.insert(node);
        Set<Node> set1 = ImmutableSet.of();
        int i = 0;
        Set<Target> set2 = Sets.newHashSetWithExpectedSize(set.size());
        int i1 = (int)(this.maxVisitedNodes * searchDepthMultiplier);

        while (!this.openSet.isEmpty()) {
            if (++i >= i1) {
                break;
            }

            Node node1 = this.openSet.pop();
            node1.closed = true;

            for (Target target : set) {
                if (node1.distanceManhattan(target) <= accuracy) {
                    target.setReached();
                    set2.add(target);
                }
            }

            if (!set2.isEmpty()) {
                break;
            }

            if (!(node1.distanceTo(node) >= maxRange)) {
                int neighbors = this.nodeEvaluator.getNeighbors(this.neighbors, node1);

                for (int i2 = 0; i2 < neighbors; i2++) {
                    Node node2 = this.neighbors[i2];
                    float f = this.distance(node1, node2);
                    node2.walkedDistance = node1.walkedDistance + f;
                    float f1 = node1.g + f + node2.costMalus;
                    if (node2.walkedDistance < maxRange && (!node2.inOpenSet() || f1 < node2.g)) {
                        node2.cameFrom = node1;
                        node2.g = f1;
                        node2.h = this.getBestH(node2, set) * 1.5F;
                        if (node2.inOpenSet()) {
                            this.openSet.changeCost(node2, node2.g + node2.h);
                        } else {
                            node2.f = node2.g + node2.h;
                            this.openSet.insert(node2);
                        }
                    }
                }
            }
        }

        Optional<Path> optional = !set2.isEmpty()
            ? set2.stream()
                .map(pathfinder -> this.reconstructPath(pathfinder.getBestNode(), targetPositions.get(pathfinder), true))
                .min(Comparator.comparingInt(Path::getNodeCount))
            : set.stream()
                .map(pathfinder -> this.reconstructPath(pathfinder.getBestNode(), targetPositions.get(pathfinder), false))
                .min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
        profilerFiller.pop();
        return optional.isEmpty() ? null : optional.get();
    }

    protected float distance(Node first, Node second) {
        return first.distanceTo(second);
    }

    private float getBestH(Node node, Set<Target> targets) {
        float f = Float.MAX_VALUE;

        for (Target target : targets) {
            float f1 = node.distanceTo(target);
            target.updateBest(f1, node);
            f = Math.min(f1, f);
        }

        return f;
    }

    private Path reconstructPath(Node point, BlockPos targetPos, boolean reachesTarget) {
        List<Node> list = Lists.newArrayList();
        Node node = point;
        list.add(0, point);

        while (node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }

        return new Path(list, targetPos, reachesTarget);
    }
}
