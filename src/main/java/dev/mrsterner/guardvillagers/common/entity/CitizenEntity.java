package dev.mrsterner.guardvillagers.common.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import dev.mrsterner.guardvillagers.common.entity.ai.brain.CitizenBrain;
import dev.mrsterner.guardvillagers.mixin.MobEntityAccessor;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Npc;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.VillagerTaskListProvider;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.DebugInfoSender;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class CitizenEntity extends PassiveEntity implements Npc, InventoryChangedListener {
    public SimpleInventory citizenInventory = new SimpleInventory(3 * 9);
    public SimpleInventory citizenEquipment = new SimpleInventory(6);
    private static final TrackedData<Optional<BlockPos>> BED_POS = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS);
    private static final TrackedData<Boolean> IS_SLEEPING = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_CHILD = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TEXTURE = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> IS_FEMALE = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> VILLAGE_ID = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ENTITY_ID = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LEVEL = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> MODEL = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> PROFESSION = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.STRING);

    private int MODEL_IDENTIFIER;
    private int TEXTURE_IDENTIFIER;
    private boolean female;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super CitizenEntity>>> SENSORS;
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_MODULES;

    private boolean child = false;
    private boolean markEquipmentForUpdate = true;
    private boolean markTextureForUpdate = true;

    public CitizenEntity(EntityType<? extends PassiveEntity> entityType, World world) {
        super(entityType, world);
        this.citizenInventory.addListener(this);
        this.citizenEquipment.addListener(this);
        ((MobNavigation)this.getNavigation()).setCanPathThroughDoors(true);
        this.getNavigation().setCanSwim(true);
    }


    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (this.world instanceof ServerWorld) {
            this.reinitializeBrain((ServerWorld)this.world);
        }
        if (nbt.contains("BedPosX")) {
            int x = nbt.getInt("BedPosX");
            int y = nbt.getInt("BedPosY");
            int z = nbt.getInt("BedPosZ");
            this.dataTracker.set(BED_POS, Optional.ofNullable(new BlockPos(x, y, z)));
        }
        NbtList listnbt = nbt.getList("Inventory", 10);
        for (int i = 0; i < listnbt.size(); ++i) {
            NbtCompound compoundnbt = listnbt.getCompound(i);
            int j = compoundnbt.getByte("Slot") & 255;
            this.citizenInventory.setStack(j, ItemStack.fromNbt(compoundnbt));
        }

        if (nbt.contains("ArmorItems", 9)) {
            NbtList armorItems = nbt.getList("ArmorItems", 10);
            for (int i = 0; i < ((MobEntityAccessor)this).armorItems().size(); ++i) {
                int index = GuardEntity.slotToInventoryIndex(MobEntity.getPreferredEquipmentSlot(ItemStack.fromNbt(armorItems.getCompound(i))));
                this.citizenEquipment.setStack(index, ItemStack.fromNbt(armorItems.getCompound(i)));
            }
        }
        if (nbt.contains("HandItems", 9)) {
            NbtList handItems = nbt.getList("HandItems", 10);
            for (int i = 0; i < ((MobEntityAccessor)this).handItems().size(); ++i) {
                int handSlot = i == 0 ? 5 : 4;
                this.citizenEquipment.setStack(handSlot, ItemStack.fromNbt(handItems.getCompound(i)));
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        NbtList listnbt = new NbtList();
        for (int i = 0; i < this.citizenInventory.size(); ++i) {
            ItemStack itemstack = this.citizenInventory.getStack(i);
            NbtCompound compoundnbt = new NbtCompound();
            compoundnbt.putByte("Slot", (byte) i);
            itemstack.writeNbt(compoundnbt);
            listnbt.add(compoundnbt);
        }
        for (int i = 0; i < this.citizenEquipment.size(); ++i) {
            ItemStack itemstack = this.citizenEquipment.getStack(i);
            NbtCompound compoundnbt = new NbtCompound();
            compoundnbt.putByte("Slot", (byte) i);
            itemstack.writeNbt(compoundnbt);
            listnbt.add(compoundnbt);
        }
        nbt.put("Inventory", listnbt);
        if (this.getDataTracker().get(BED_POS).isPresent()) {
            nbt.putInt("BedPosX", this.getDataTracker().get(BED_POS).get().getX());
            nbt.putInt("BedPosY", this.getDataTracker().get(BED_POS).get().getY());
            nbt.putInt("BedPosZ", this.getDataTracker().get(BED_POS).get().getZ());
        }
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        this.setPersistent();
        this.initEquipment(world.getRandom(), difficulty);
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BED_POS, Optional.empty());

        this.dataTracker.startTracking(IS_SLEEPING, false);
        this.dataTracker.startTracking(IS_CHILD, false);

        this.dataTracker.startTracking(TEXTURE, 0);
        this.dataTracker.startTracking(IS_FEMALE, 0);
        this.dataTracker.startTracking(VILLAGE_ID, 0);
        this.dataTracker.startTracking(ENTITY_ID, 0);
        this.dataTracker.startTracking(LEVEL, 0);

        this.dataTracker.startTracking(MODEL, "");
        this.dataTracker.startTracking(PROFESSION, "");
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20);
    }


    @Override
    protected void mobTick() {
        this.world.getProfiler().push("citizenBrain");
        this.getBrain().tick((ServerWorld)this.world, this);
        this.world.getProfiler().pop();
        this.world.getProfiler().push("citizenActivityUpdate");
        CitizenBrain.updateActivities(this);
        this.world.getProfiler().pop();
    }

    @Override
    protected Brain.Profile<CitizenEntity> createBrainProfile() {
        return Brain.createProfile(MEMORY_MODULES, SENSORS);
    }

    @Override
    protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
        return CitizenBrain.create(this.createBrainProfile().deserialize(dynamic));
    }

    @Override
    public Brain<CitizenEntity> getBrain() {
        return (Brain<CitizenEntity>) super.getBrain();
    }

    @Override
    protected void sendAiDebugData() {
        super.sendAiDebugData();
        DebugInfoSender.sendBrainDebugData(this);
    }

    @Override
    protected void onGrowUp() {
        super.onGrowUp();
        if (this.world instanceof ServerWorld) {
            this.reinitializeBrain((ServerWorld)this.world);
        }

    }

    public void reinitializeBrain(ServerWorld world) {
        Brain<CitizenEntity> brain = this.getBrain();
        brain.stopAllTasks(world, this);
        this.brain = brain.copy();
        this.initBrain(this.getBrain());
    }

    private void initBrain(Brain<CitizenEntity> brain) {
        if (this.isBaby()) {
            /*TODO
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.setTaskList(Activity.PLAY, VillagerTaskListProvider.createPlayTasks(0.5F));

             */
        } else {
            /*
            brain.setSchedule(Schedule.VILLAGER_DEFAULT);
            brain.setTaskList(
                    Activity.WORK,
                    VillagerTaskListProvider.createWorkTasks(villagerProfession, 0.5F),
                    ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryModuleState.VALUE_PRESENT))
            );

             */
        }

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.doExclusively(Activity.IDLE);
        brain.refreshActivities(this.world.getTimeOfDay(), this.world.getTime());
    }


    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public void sleep(BlockPos pos) {
        super.sleep(pos);
        this.brain.remember(MemoryModuleType.LAST_SLEPT, this.world.getTime());
        this.brain.forget(MemoryModuleType.WALK_TARGET);
        this.brain.forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void wakeUp() {
        super.wakeUp();
        this.brain.remember(MemoryModuleType.LAST_WOKEN, this.world.getTime());
    }

    @Override
    public boolean isBaby() {
        return child;
    }

    @Override
    public void onInventoryChanged(Inventory sender) {
    }

    public boolean isFemale() {
        return female;
    }

    public void setFemale(boolean female) {
        this.female = female;
    }

    public boolean isMarkTextureForUpdate() {
        return markTextureForUpdate;
    }

    public void setMarkTextureForUpdate(boolean markTextureForUpdate) {
        this.markTextureForUpdate = markTextureForUpdate;
    }


    public boolean isMarkEquipmentForUpdate() {
        return markEquipmentForUpdate;
    }

    public void setMarkEquipmentForUpdate(boolean markEquipmentForUpdate) {
        this.markEquipmentForUpdate = markEquipmentForUpdate;
    }

    public int getModel() {
        return MODEL_IDENTIFIER;
    }

    public void setModel(int model) {
        this.MODEL_IDENTIFIER = model;
        this.dataTracker.set(TEXTURE, model);
    }

    public int getTexture() {
        return TEXTURE_IDENTIFIER;
    }

    public void setTexture(int texture) {
        this.TEXTURE_IDENTIFIER = texture;
        this.dataTracker.set(TEXTURE, texture);
    }

    static {
        SENSORS = ImmutableList.of(
                SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.HURT_BY);
    }

    static {
        MEMORY_MODULES = ImmutableList.of(
                MemoryModuleType.MOBS,
                MemoryModuleType.VISIBLE_MOBS,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.PATH,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.ATTACK_COOLING_DOWN,
                MemoryModuleType.HURT_BY_ENTITY,
                MemoryModuleType.NEAREST_ATTACKABLE
        );
    }


}
