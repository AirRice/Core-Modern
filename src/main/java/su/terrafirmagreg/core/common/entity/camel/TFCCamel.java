package su.terrafirmagreg.core.common.entity.camel;

import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.entities.EntityHelpers;
import net.dries007.tfc.common.entities.livestock.CommonAnimalData;
import net.dries007.tfc.common.entities.livestock.MammalProperties;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.config.animals.AnimalConfig;
import net.dries007.tfc.config.animals.MammalConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class TFCCamel extends Camel implements MammalProperties {

    static double familiarityCap = 0.35;
    static int adulthoodDays = 80;
    static int uses = 60;
    static boolean eatsRottenFood = false;
    static int childCount = 1;
    static long gestationDays = 19;

    private static final EntityDataAccessor<Boolean> GENDER;
    private static final EntityDataAccessor<Long> BIRTHDAY;
    private static final EntityDataAccessor<Float> FAMILIARITY;
    private static final EntityDataAccessor<Integer> USES;
    private static final EntityDataAccessor<Boolean> FERTILIZED;
    private static final EntityDataAccessor<Long> OLD_DAY;
    private static final EntityDataAccessor<Integer> GENETIC_SIZE;
    private static final EntityDataAccessor<Long> LAST_FED;
    private static final CommonAnimalData ANIMAL_DATA;
    private static final EntityDataAccessor<Long> PREGNANT_TIME;
    private long lastFDecay;
    private long matingTime;
    @Nullable
    private CompoundTag genes;
    private TFCAnimalProperties.Age lastAge;
    private final AnimalConfig config;
    private final MammalConfig mammalConfig;

    public TFCCamel(EntityType<? extends TFCCamel> type, Level level, MammalConfig config) {
        super(type, level);
        this.lastAge = Age.CHILD;
        this.matingTime = Calendars.get(level).getTicks();
        this.lastFDecay = Calendars.get(level).getTotalDays();
        this.config = config.inner();
        this.mammalConfig = config;
    }

    public static TFCCamel makeTFCCamel(EntityType<? extends TFCCamel> type, Level level) {

        return new TFCCamel(type, level, TFCConfig.SERVER.horseConfig);
    }

    @Nullable
    public TFCCamel getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return (TFCCamel) MammalProperties.super.getBreedOffspring(level, otherParent);
    }

    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        spawnData = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        if (reason != MobSpawnType.BREEDING) {
            this.initCommonAnimalData(level, difficulty, reason);
        }

        this.setPregnantTime(-1L);
        return spawnData;
    }

    public MammalConfig getMammalConfig() {
        return this.mammalConfig;
    }

    public long getPregnantTime() {
        return (Long) this.entityData.get(PREGNANT_TIME);
    }

    public void setPregnantTime(long day) {
        this.entityData.set(PREGNANT_TIME, day);
    }

    public void setGenes(@Nullable CompoundTag tag) {
        this.genes = tag;
    }

    @Nullable
    public CompoundTag getGenes() {
        return this.genes;
    }

    public AnimalConfig animalConfig() {
        return this.config;
    }

    public CommonAnimalData animalData() {
        return ANIMAL_DATA;
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.registerCommonData();
        this.entityData.define(PREGNANT_TIME, -1L);
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (BIRTHDAY.equals(data)) {
            this.refreshDimensions();
        }

    }

    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        this.saveCommonAnimalData(nbt);
    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.readCommonAnimalData(nbt);
    }

    //config bypasses
    @Override
    public float getAdultFamiliarityCap() {
        return (float) familiarityCap;
    }

    @Override
    public int getDaysToAdulthood() {
        return adulthoodDays;
    }

    @Override
    public int getUsesToElderly() {
        return uses;
    }

    @Override
    public boolean eatsRottenFood() {
        return eatsRottenFood;
    }

    @Override
    public int getChildCount() {
        return childCount;
    }

    @Override
    public long getGestationDays() {
        return gestationDays;
    }
    //End of config override

    public long getLastFamiliarityDecay() {
        return this.lastFDecay;
    }

    public void setLastFamiliarityDecay(long days) {
        this.lastFDecay = days;
    }

    public void setMated(long ticks) {
        this.matingTime = ticks;
    }

    public long getMated() {
        return this.matingTime;
    }

    public Age getLastAge() {
        return this.lastAge;
    }

    public void setLastAge(Age lastAge) {
        this.lastAge = lastAge;
    }

    @Override
    public TagKey<Item> getFoodTag() {
        return TFCTags.Items.HORSE_FOOD;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().getGameTime() % 20 == 0) {
            tickAnimalData();
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return MammalProperties.super.isFood(stack);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = MammalProperties.super.mobInteract(player, hand);
        if (result == InteractionResult.PASS) {
            ItemStack stack = player.getItemInHand(hand);
            if (!this.isBaby()) {
                if (this.isTamed() && player.isSecondaryUseActive()) {
                    this.openCustomInventoryScreen(player);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
                if (this.isVehicle()) {
                    return InteractionResult.PASS;
                }
            }

            if (!stack.isEmpty()) {
                InteractionResult res = stack.interactLivingEntity(player, this, hand);
                if (res.consumesAction()) {
                    return res;
                }

                boolean canBeSaddled = !this.isBaby() && !this.isSaddled() && stack.is(net.minecraft.world.item.Items.SADDLE);
                if (canBeSaddled) {
                    this.openCustomInventoryScreen(player);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            if (this.isBaby()) {
                return InteractionResult.PASS;
            } else {
                if (this.isTamed() && this.getOwnerUUID() == null) {
                    this.tameWithName(player);
                }
                if (this.isTamed() && this.getPassengers().size() < 2) {
                    this.doPlayerRide(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        } else {
            return result;
        }
    }

    @Override
    public boolean isTamed() {
        System.out.printf("istamed? %.2f%n", this.getFamiliarity());
        return this.getFamiliarity() > 0.15F;
    }

    static {
        GENDER = SynchedEntityData.defineId(TFCCamel.class, EntityDataSerializers.BOOLEAN);
        BIRTHDAY = SynchedEntityData.defineId(TFCCamel.class, EntityHelpers.LONG_SERIALIZER);
        FAMILIARITY = SynchedEntityData.defineId(TFCCamel.class, EntityDataSerializers.FLOAT);
        USES = SynchedEntityData.defineId(TFCCamel.class, EntityDataSerializers.INT);
        FERTILIZED = SynchedEntityData.defineId(TFCCamel.class, EntityDataSerializers.BOOLEAN);
        OLD_DAY = SynchedEntityData.defineId(TFCCamel.class, EntityHelpers.LONG_SERIALIZER);
        GENETIC_SIZE = SynchedEntityData.defineId(TFCCamel.class, EntityDataSerializers.INT);
        LAST_FED = SynchedEntityData.defineId(TFCCamel.class, EntityHelpers.LONG_SERIALIZER);
        ANIMAL_DATA = new CommonAnimalData(GENDER, BIRTHDAY, FAMILIARITY, USES, FERTILIZED, OLD_DAY, GENETIC_SIZE, LAST_FED);
        PREGNANT_TIME = SynchedEntityData.defineId(TFCCamel.class, EntityHelpers.LONG_SERIALIZER);
    }
}
