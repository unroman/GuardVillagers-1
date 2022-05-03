package dev.mrsterner.guardvillagers.common.entity.ai.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.mrsterner.guardvillagers.common.entity.CitizenEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.ai.brain.task.*;

import java.util.Optional;

public class CitizenBrain {
    public static Brain<?> create(Brain<CitizenEntity> brain) {
        addCoreActivities(brain);
        addIdleActivities(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.resetPossibleActivities();
        return brain;
    }
    
    

    private static void addCoreActivities(Brain<CitizenEntity> brain) {
        brain.setTaskList(
                Activity.CORE,
                0,
                ImmutableList.of(
                        new LookAroundTask(45, 90), 
                        new WanderAroundTask(), 
                        new TemptationCooldownTask(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)
                )
        );
    }

    private static void addIdleActivities(Brain<CitizenEntity> brain) {
        brain.setTaskList(
                Activity.IDLE,
                ImmutableList.of(
                        Pair.of(3, new UpdateAttackTargetTask<>(CitizenBrain::getAttackTarget)),
                        Pair.of(3, new SeekWaterTask(6, 0.15F)),
                        Pair.of(4, new CompositeTask<>(
                                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT),
                                        ImmutableSet.of(),
                                        CompositeTask.Order.ORDERED,
                                        CompositeTask.RunMode.TRY_ALL,
                                        ImmutableList.of(
                                                Pair.of(new AquaticStrollTask(0.5F), 2),
                                                Pair.of(new StrollTask(0.15F, false), 2),
                                                Pair.of(new ConditionalTask<>(Entity::isInsideWaterOrBubbleColumn, new WaitTask(30, 60)), 5),
                                                Pair.of(new ConditionalTask<>(Entity::isOnGround, new WaitTask(200, 400)), 5)
                                        )
                                )
                        )
                )
        );
    }

    public static void updateActivities(CitizenEntity citizenEntity) {
        Brain<CitizenEntity> brain = citizenEntity.getBrain();
        brain.resetPossibleActivities(ImmutableList.of(Activity.FIGHT, Activity.AVOID, Activity.IDLE));
    }

    private static Optional<? extends LivingEntity> getAttackTarget(CitizenEntity citizenEntity) {
        return LookTargetUtil.hasBreedTarget(citizenEntity) ? Optional.empty() : citizenEntity.getBrain().getOptionalMemory(MemoryModuleType.NEAREST_ATTACKABLE);
    }
}
