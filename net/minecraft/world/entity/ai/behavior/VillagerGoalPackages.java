package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class VillagerGoalPackages {
    private static final float STROLL_SPEED_MODIFIER = 0.4F;
    public static final int INTERACT_DIST_SQR = 5;
    public static final int INTERACT_WALKUP_DIST = 2;
    public static final float INTERACT_SPEED_MODIFIER = 0.5F;

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getCorePackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
            Pair.of(0, new Swim<>(0.8F)),
            Pair.of(0, InteractWithDoor.create()),
            Pair.of(0, new LookAtTargetSink(45, 90)),
            Pair.of(0, new VillagerPanicTrigger()),
            Pair.of(0, WakeUp.create()),
            Pair.of(0, ReactToBell.create()),
            Pair.of(0, SetRaidStatus.create()),
            Pair.of(0, ValidateNearbyPoi.create(profession.heldJobSite(), MemoryModuleType.JOB_SITE)),
            Pair.of(0, ValidateNearbyPoi.create(profession.acquirableJobSite(), MemoryModuleType.POTENTIAL_JOB_SITE)),
            Pair.of(1, new MoveToTargetSink()),
            Pair.of(2, PoiCompetitorScan.create()),
            Pair.of(3, new LookAndFollowTradingPlayerSink(speedModifier)),
            Pair.of(5, GoToWantedItem.create(villager -> !villager.isSleeping(), speedModifier, false, 4)), // Paper - Fix MC-157464
            Pair.of(
                6,
                AcquirePoi.create(
                    profession.acquirableJobSite(),
                    MemoryModuleType.JOB_SITE,
                    MemoryModuleType.POTENTIAL_JOB_SITE,
                    true,
                    Optional.empty(),
                    (level, pos) -> true
                )
            ),
            Pair.of(7, new GoToPotentialJobSite(speedModifier)),
            Pair.of(8, YieldJobSite.create(speedModifier)),
            Pair.of(
                10,
                AcquirePoi.create(
                    poiType -> poiType.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte)14), VillagerGoalPackages::validateBedPoi
                )
            ),
            Pair.of(10, AcquirePoi.create(poiType -> poiType.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte)14))),
            Pair.of(10, AssignProfessionFromJobSite.create()),
            Pair.of(10, ResetProfession.create())
        );
    }

    private static boolean validateBedPoi(ServerLevel level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && !blockState.getValue(BedBlock.OCCUPIED);
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getWorkPackage(VillagerProfession profession, float speedModifier) {
        // Purpur start - Option for Villager Clerics to farm Nether Wart
        return getWorkPackage(profession, speedModifier, false);
    }
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getWorkPackage(VillagerProfession profession, float speedModifier, boolean clericsFarmWarts) {
        // Purpur end - Option for Villager Clerics to farm Nether Wart
        WorkAtPoi workAtPoi;
        if (profession == VillagerProfession.FARMER || (clericsFarmWarts && profession == VillagerProfession.CLERIC)) { // Purpur - Option for Villager Clerics to farm Nether Wart
            workAtPoi = new WorkAtComposter();
        } else {
            workAtPoi = new WorkAtPoi();
        }

        return ImmutableList.of(
            getMinimalLookBehavior(),
            Pair.of(
                5,
                new RunOne<>(
                    ImmutableList.of(
                        Pair.of(workAtPoi, 7),
                        Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 4), 2),
                        Pair.of(StrollToPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 1, 10), 5),
                        Pair.of(StrollToPoiList.create(MemoryModuleType.SECONDARY_JOB_SITE, speedModifier, 1, 6, MemoryModuleType.JOB_SITE), 5),
                        Pair.of(new HarvestFarmland(), profession == VillagerProfession.FARMER ? 2 : 5),
                        Pair.of(new UseBonemeal(), profession == VillagerProfession.FARMER ? 4 : 7)
                    )
                )
            ),
            Pair.of(10, new ShowTradesToPlayer(400, 1600)),
            Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
            Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speedModifier, 9, 100, 1200)),
            Pair.of(3, new GiveGiftToHero(100)),
            Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getPlayPackage(float speedModifier) {
        return ImmutableList.of(
            Pair.of(0, new MoveToTargetSink(80, 120)),
            getFullLookBehavior(),
            Pair.of(5, PlayTagWithOtherKids.create()),
            Pair.of(
                5,
                new RunOne<>(
                    ImmutableMap.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryStatus.VALUE_ABSENT),
                    ImmutableList.of(
                        Pair.of(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier), 1),
                        Pair.of(SetWalkTargetFromLookTarget.create(speedModifier, 2), 1),
                        Pair.of(new JumpOnBed(speedModifier), 2),
                        Pair.of(new DoNothing(20, 40), 2)
                    )
                )
            ),
            Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getRestPackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
            Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, speedModifier, 1, 150, 1200)),
            Pair.of(3, ValidateNearbyPoi.create(poiType -> poiType.is(PoiTypes.HOME), MemoryModuleType.HOME)),
            Pair.of(3, new SleepInBed()),
            Pair.of(
                5,
                new RunOne<>(
                    ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                    ImmutableList.of(
                        Pair.of(SetClosestHomeAsWalkTarget.create(speedModifier), 1),
                        Pair.of(InsideBrownianWalk.create(speedModifier), 4),
                        Pair.of(GoToClosestVillage.create(speedModifier, 4), 2),
                        Pair.of(new DoNothing(20, 40), 2)
                    )
                )
            ),
            getMinimalLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getMeetPackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
            Pair.of(
                2,
                TriggerGate.triggerOneShuffled(
                    ImmutableList.of(Pair.of(StrollAroundPoi.create(MemoryModuleType.MEETING_POINT, 0.4F, 40), 2), Pair.of(SocializeAtBell.create(), 2))
                )
            ),
            Pair.of(10, new ShowTradesToPlayer(400, 1600)),
            Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
            Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, speedModifier, 6, 100, 200)),
            Pair.of(3, new GiveGiftToHero(100)),
            Pair.of(3, ValidateNearbyPoi.create(poiType -> poiType.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT)),
            Pair.of(
                3,
                new GateBehavior<>(
                    ImmutableMap.of(),
                    ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                    GateBehavior.OrderPolicy.ORDERED,
                    GateBehavior.RunningPolicy.RUN_ONE,
                    ImmutableList.of(Pair.of(new TradeWithVillager(), 1))
                )
            ),
            getFullLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getIdlePackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
            Pair.of(
                2,
                new RunOne<>(
                    ImmutableList.of(
                        Pair.of(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(
                            InteractWith.of(EntityType.VILLAGER, 8, AgeableMob::canBreed, AgeableMob::canBreed, MemoryModuleType.BREED_TARGET, speedModifier, 2),
                            1
                        ),
                        Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier), 1),
                        Pair.of(SetWalkTargetFromLookTarget.create(speedModifier, 2), 1),
                        Pair.of(new JumpOnBed(speedModifier), 1),
                        Pair.of(new DoNothing(30, 60), 1)
                    )
                )
            ),
            Pair.of(3, new GiveGiftToHero(100)),
            Pair.of(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
            Pair.of(3, new ShowTradesToPlayer(400, 1600)),
            Pair.of(
                3,
                new GateBehavior<>(
                    ImmutableMap.of(),
                    ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                    GateBehavior.OrderPolicy.ORDERED,
                    GateBehavior.RunningPolicy.RUN_ONE,
                    ImmutableList.of(Pair.of(new TradeWithVillager(), 1))
                )
            ),
            Pair.of(
                3,
                new GateBehavior<>(
                    ImmutableMap.of(),
                    ImmutableSet.of(MemoryModuleType.BREED_TARGET),
                    GateBehavior.OrderPolicy.ORDERED,
                    GateBehavior.RunningPolicy.RUN_ONE,
                    ImmutableList.of(Pair.of(new VillagerMakeLove(), 1))
                )
            ),
            getFullLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getPanicPackage(VillagerProfession profession, float speedModifier) {
        float f = speedModifier * 1.5F;
        return ImmutableList.of(
            Pair.of(0, VillagerCalmDown.create()),
            Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
            Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
            Pair.of(3, VillageBoundRandomStroll.create(f, 2, 2)),
            getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getPreRaidPackage(
        VillagerProfession profession, float speedModifier
    ) {
        return ImmutableList.of(
            Pair.of(0, RingBell.create()),
            Pair.of(
                0,
                TriggerGate.triggerOneShuffled(
                    ImmutableList.of(
                        Pair.of(SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, speedModifier * 1.5F, 2, 150, 200), 6),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier * 1.5F), 2)
                    )
                )
            ),
            getMinimalLookBehavior(),
            Pair.of(99, ResetRaidStatus.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getRaidPackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
            Pair.of(
                0,
                BehaviorBuilder.sequence(
                    BehaviorBuilder.triggerIf(VillagerGoalPackages::raidExistsAndNotVictory),
                    TriggerGate.triggerOneShuffled(
                        ImmutableList.of(
                            Pair.of(MoveToSkySeeingSpot.create(speedModifier), 5), Pair.of(VillageBoundRandomStroll.create(speedModifier * 1.1F), 2)
                        )
                    )
                )
            ),
            Pair.of(0, new CelebrateVillagersSurvivedRaid(600, 600)),
            Pair.of(
                2,
                BehaviorBuilder.sequence(
                    BehaviorBuilder.triggerIf(VillagerGoalPackages::raidExistsAndActive), LocateHidingPlace.create(24, speedModifier * 1.4F, 1)
                )
            ),
            getMinimalLookBehavior(),
            Pair.of(99, ResetRaidStatus.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getHidePackage(VillagerProfession profession, float speedModifier) {
        int i = 2;
        return ImmutableList.of(
            Pair.of(0, SetHiddenState.create(15, 3)), Pair.of(1, LocateHidingPlace.create(32, speedModifier * 1.25F, 2)), getMinimalLookBehavior()
        );
    }

    private static Pair<Integer, BehaviorControl<LivingEntity>> getFullLookBehavior() {
        return Pair.of(
            5,
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(SetEntityLookTarget.create(EntityType.CAT, 8.0F), 8),
                    Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(MobCategory.CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.WATER_CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.AXOLOTLS, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.WATER_AMBIENT, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.MONSTER, 8.0F), 1),
                    Pair.of(new DoNothing(30, 60), 2)
                )
            )
        );
    }

    private static Pair<Integer, BehaviorControl<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(
            5,
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                    Pair.of(new DoNothing(30, 60), 8)
                )
            )
        );
    }

    private static boolean raidExistsAndActive(ServerLevel level, LivingEntity entity) {
        Raid raidAt = level.getRaidAt(entity.blockPosition());
        return raidAt != null && raidAt.isActive() && !raidAt.isVictory() && !raidAt.isLoss();
    }

    private static boolean raidExistsAndNotVictory(ServerLevel level, LivingEntity entity) {
        Raid raidAt = level.getRaidAt(entity.blockPosition());
        return raidAt != null && raidAt.isVictory();
    }
}
