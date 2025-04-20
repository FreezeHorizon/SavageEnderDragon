package com.dragonfight.fight;

import com.cupboard.util.BlockSearch;
import com.dragonfight.DragonfightMod;
import com.dragonfight.config.ConfigurationCache;
import com.dragonfight.config.ConfigurationCache.EntitySpawnData;
import com.google.common.collect.ImmutableList;


import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;

/**
 * Custom manager for handling additional dragon difficulty
 */
public class DragonFightManagerCustom
{
    public static ImmutableList<ConfigurationCache.ConfiguredSpawnData> spawnOnCrystalDeath   = ImmutableList.of();
    public static ImmutableList<ConfigurationCache.ConfiguredSpawnData> spawnOnCrystalRespawn = ImmutableList.of();
    public static ImmutableList<ConfigurationCache.EntitySpawnData> spawnOnDragonSitting  = ImmutableList.of();

    private static final float    CRYSTAL_RESPAWN_TIME    = 8000;
    private static final int      LIGHTNING_DESTROY_RANGE = 10 * 10;
    private static final float    ADD_TIMER               = 2000;
    private static       BlockPos crystalRespawnPos       = null;
    private static       int      crystalRespawnTimer     = 0;
    private static int globalLevitationEventTimer = 0;
    private static final int LEVITATION_EVENT_INTERVAL = 1200;
    private static int timeSinceLastLanding = 0;
    private static final int spawn_offset = 7;
    private static int print_hp_timer = 0;
    /**
     * ^^ Add counters
     */
    private static       boolean            spawnAdds    = false;
    private final static BlockPos           spawnPos     = new BlockPos(0, 68, 0);
    private static       int                spawnCounter = 0;
    private static       List<LivingEntity> meleeAdds    = new ArrayList<>();

    private static int advancingLightningCurrent = 0;
    private static int advancingLightningStop    = 0;

    private static int advancingExplosionCurrent = 0;
    private static int advancingExplosionStop    = 0;

    private static EnderDragon dragonEntity = null;

    public static boolean isFightRunning = true;

    public static  AttributeModifier AA_GRAVITY_MOD = new AttributeModifier("fall", 5.0, AttributeModifier.Operation.ADDITION);
    private static AttributeModifier MAX_HP_MOD     = new AttributeModifier("dragonhp", 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static void onCrystalDeath(final EndCrystal enderCrystalEntity, final DamageSource damageSource)
    {
        AreaEffectCloud areaeffectcloudentity =
          new AreaEffectCloud(enderCrystalEntity.level(), enderCrystalEntity.getX(), enderCrystalEntity.getY(), enderCrystalEntity.getZ());

        if (dragonEntity != null)
        {
            areaeffectcloudentity.setOwner(dragonEntity);
        }

        notifyPlayer(enderCrystalEntity.level(), "Crystal died from:" + damageSource);
        // Spawn ground area effect making the player walk away
        areaeffectcloudentity.setParticle(ParticleTypes.DRAGON_BREATH);
        areaeffectcloudentity.setRadius(3.0F);
        areaeffectcloudentity.setDuration((int) ((CRYSTAL_RESPAWN_TIME / getDifficulty()) * DragonfightMod.config.getCommonConfig().crystalRespawnTimeModifier));
        areaeffectcloudentity.setRadiusPerTick((5.0F - areaeffectcloudentity.getRadius()) / (float) areaeffectcloudentity.getDuration());
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.HARM, 100, 5));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 1));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 300, 200));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 10));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 4));
        enderCrystalEntity.level().addFreshEntity(areaeffectcloudentity);

        addCrystalRespawnPos(enderCrystalEntity.blockPosition());

        if (!(damageSource.getEntity() instanceof Player))
        {
            return;
        }
        if (damageSource.getEntity().blockPosition().distSqr(enderCrystalEntity.blockPosition()) > LIGHTNING_DESTROY_RANGE)
            {
            // On ranged crystal kill
            if (!spawnOnCrystalDeath.isEmpty())
            {
                BlockPos destroyedCrystalPos = enderCrystalEntity.blockPosition();
                // Calculate potential spawn locations ONCE before the loop
                
                BlockPos groundSpawnPos = new BlockPos(destroyedCrystalPos.getX()+spawn_offset, 64, destroyedCrystalPos.getZ()+spawn_offset); // Y64 as ground level for now
                BlockPos pillarTopPos = new BlockPos(destroyedCrystalPos.getX()+1, destroyedCrystalPos.getY()-1, destroyedCrystalPos.getZ()+1);
                Vec3 groundVec = createVec3(groundSpawnPos);
                Vec3 pillarTopVec = createVec3(pillarTopPos);

                DragonfightMod.LOGGER.info("{} Crystal destroyed at range. Processing configured spawns...", destroyedCrystalPos);

                // Iterate through ALL configured entries for this event
                for (ConfigurationCache.ConfiguredSpawnData configuredSpawnData : spawnOnCrystalDeath) {
                    EntitySpawnData spawnData = configuredSpawnData.entityData(); // Use data directly from config parsing
                    String locationPref = configuredSpawnData.locationPreference();
                    ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type);

                    // Determine spawn location based on config preference
                    Vec3 targetSpawnVec;
                    if (locationPref.equals("pillar_top")) {
                        targetSpawnVec = pillarTopVec;
                        DragonfightMod.LOGGER.info("Spawning {} at pillar top.", typeId);
                    } else { // Default to ground_level
                        targetSpawnVec = groundVec;
                        DragonfightMod.LOGGER.info("Spawning {} at ground level near {}.", typeId, groundSpawnPos);
                    }

                    // Spawn the entity using the final data and target location
                    // NO special case override needed here anymore
                    spawnEntity((ServerLevel) enderCrystalEntity.level(), spawnData, targetSpawnVec);
                    DragonfightMod.LOGGER.info("Spawning {} at {}.",spawnData, targetSpawnVec);
                    }
                }
                
            }
            // removed melee kill reducing dragon hp
        }

    private static void addCrystalRespawnPos(final BlockPos position)
    {
        if (dragonEntity == null || Math.sqrt(dragonEntity.blockPosition().distSqr(position)) > 1000)
        {
            return;
        }

        CrystalLevelData.getForLevel((ServerLevel) dragonEntity.level()).addPosition(position);
    }

    private static Set<BlockPos> getCrystalRespawnPositions(final ServerLevel level)
    {
        final Set<BlockPos> existing = new HashSet<>();

        for (final BlockPos pos : CrystalLevelData.getForLevel(level).getCrystalPendingRespawns())
        {
            if (dragonEntity != null && Math.sqrt(dragonEntity.blockPosition().distSqr(pos)) > 1000)
            {
                continue;
            }

            existing.add(pos);
        }

        return existing;
    }

    public static Map<UUID, Integer> flyingPlayers = new HashMap<>();

    public static void onWorldTick(final Level world)
    {
        final EndDragonFight manager = ((ServerLevel) world).getDragonFight();
        if (manager == null || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty() || dragonEntity == null)
        {
            reset();
            return;
        }

        if (crystalRespawnPos != null)
        {
            if (--crystalRespawnTimer > 0)
            {
                if (crystalRespawnTimer == 200)
                {
                    // Spawns pre-respawn lightning
                    spawnLightningAtCircle(crystalRespawnPos, 8, world);
                }
            }
            else
            {
                notifyPlayer(world, "Respawning crystal at" + crystalRespawnPos);
                respawnCrystalAt(crystalRespawnPos, world);
            }
        }

        if (isFightRunning && dragonEntity.isAlive()) {
            setDragonHealth(); // Re-apply override/scaling logic frequently
            if (DragonfightMod.config.getCommonConfig().printDragonPhases && print_hp_timer == 20)
            {
                
                DragonfightMod.LOGGER.info("dragon current health =" + dragonEntity.getHealth());
                print_hp_timer = 0;

            }
            print_hp_timer++;
        }

        if (dragonEntity.getHealth() < dragonEntity.getMaxHealth() && dragonEntity.isAlive())
        {
            if (!isFightRunning && !((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty())
            {
                // Cleans entities on fight start
                List<Monster> monsterEntities = world.getEntitiesOfClass(Monster.class, dragonEntity.getBoundingBox().inflate(150));
                for (final Monster entity : monsterEntities)
                {
                    if (!(entity instanceof Npc) && !entity.isPersistenceRequired())
                    {
                        entity.remove(Entity.RemovalReason.DISCARDED);
                    }
                }

                setDragonHealth();

                isFightRunning = true;
            }
        }
        else
        {
            if (isFightRunning)
            {
                reset();
            }
            isFightRunning = false;
        }

        if (!isFightRunning)
        {
            return;
        }

        // Fix dragon flying forever on death
        if (dragonEntity.getPhaseManager().getCurrentPhase() instanceof DragonDeathPhase && dragonEntity.getPhaseManager().getCurrentPhase().getFlyTargetLocation() != null)
        {
            if (dragonEntity.getPhaseManager().getCurrentPhase().getFlyTargetLocation().distanceToSqr(dragonEntity.blockPosition().getX(),
              dragonEntity.blockPosition().getY(), dragonEntity.getZ()) < 10)
            {
                dragonEntity.setHealth(0);
            }
        }

        timeSinceLastLanding++;
        // Fix landing
        if (timeSinceLastLanding > 120 * 20 && dragonEntity != null)
        {
            timeSinceLastLanding = 0;
            dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
            notifyPlayer(world, "Forcing landing phase");
        }


        if (dragonEntity.getHealth() > dragonEntity.getMaxHealth() * 0.9)
        {
            return;
        }

        if (DragonfightMod.config.getCommonConfig().antiflightAbility)
        {
            globalLevitationEventTimer++;
            if (globalLevitationEventTimer >= LEVITATION_EVENT_INTERVAL)
            {
            globalLevitationEventTimer = 0; // Reset timer
            DragonfightMod.LOGGER.info("Triggering global Levitation 230 event.");
            final EndDragonFight fightmanager = ((ServerLevel) world).getDragonFight();
            if (fightmanager != null) {
                for (final Player player : ((IDragonfightAccessor) fightmanager).getDragonEvent().getPlayers())
                {
                    if (!player.isSpectator())
                        {
                            int durationTicks = 400;
                            int amplifier = 220;     // Negative Levitation effect, in 1.20.2 change this to gravity attribute

                            // Apply the effect
                            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, durationTicks, amplifier, false, false)); // Ambient=false visible = false
                            // Optional: Message to player
                            player.sendSystemMessage(Component.literal("The dragon's power pulls you down").withStyle(ChatFormatting.DARK_PURPLE));
                        }
                    }
                }
            }
        }

        if (advancingLightningCurrent > 0 && world.getGameTime() % 100 == 0)
        {
            advancingLightningCurrent += 3;
            spawnLightningAtCircle(spawnPos, advancingLightningCurrent, world);

            if (advancingLightningCurrent > advancingLightningStop)
            {
                advancingLightningCurrent = 0;
                advancingLightningStop = 0;
            }
        }

        if (dragonEntity != null && advancingExplosionCurrent == 0 && advancingLightningCurrent == 0)
        {
            advancingExplosionCurrent = 8;
            advancingExplosionStop = 80;
        }

        if (advancingExplosionCurrent > 0 && world.getGameTime() % 200 == 0)
        {
            advancingExplosionCurrent += 3;
            explodeInCircleAround(spawnPos, advancingExplosionCurrent, world);

            if (advancingExplosionCurrent > advancingExplosionStop)
            {
                advancingExplosionCurrent = 0;
                advancingExplosionStop = 0;
            }
        }

        if (spawnAdds && spawnCounter++ > (ADD_TIMER / (getDifficulty() * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier)))
        {
            notifyPlayer(world, "Spawning melee add");
            spawnMeleeAdds(world);
            spawnCounter = 0;
        }
    }

    /**
     * Re-adds the health modifiers
     */

     private static void setDragonHealth()
     {
         int hpOverride = DragonfightMod.config.getCommonConfig().dragonHpOverride;
         double currentBase = dragonEntity.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
         boolean hasScaledMod = dragonEntity.getAttribute(Attributes.MAX_HEALTH).getModifier(MAX_HP_MOD.getId()) != null;
     
         // Remove existing modifier ONLY if we intend to change the state
         boolean needsModifierRemoval = (hpOverride > 0 && hasScaledMod) || (hpOverride <= 0 && currentBase != DragonfightMod.config.getCommonConfig().dragonBaseHp);
         if (needsModifierRemoval && hasScaledMod) {
              dragonEntity.getAttribute(Attributes.MAX_HEALTH).removeModifier(MAX_HP_MOD.getId());
              hasScaledMod = false; // Update state
         }
         if (hpOverride > 0) {
             // --- Apply HP Override ---
             // Only set if different from current base
             if (currentBase != hpOverride) {
                  DragonfightMod.LOGGER.info("Applying Dragon HP Override: {}", hpOverride);
                  dragonEntity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hpOverride);
                  // Heal only if needed
                  if(dragonEntity.getHealth() < hpOverride) dragonEntity.setHealth(hpOverride);
             }
         } else {
             // --- Apply Scaled HP ---
             // Set base value only if different
             if (currentBase != DragonfightMod.config.getCommonConfig().dragonBaseHp) {
                 dragonEntity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DragonfightMod.config.getCommonConfig().dragonBaseHp);
             }
     
             // Calculate the target modifier value
             double targetModValue = (Math.max(1, getDifficulty() / 5.0) * DragonfightMod.config.getCommonConfig().dragonHealthModifier) - 1.0;
     
             // Apply modifier only if it's not already present with the correct value
             AttributeModifier existingMod = dragonEntity.getAttribute(Attributes.MAX_HEALTH).getModifier(MAX_HP_MOD.getId());
             if (existingMod == null || existingMod.getAmount() != targetModValue) {
                 if(existingMod != null) { // Remove if value is wrong
                      dragonEntity.getAttribute(Attributes.MAX_HEALTH).removeModifier(MAX_HP_MOD.getId());
                 }
                 MAX_HP_MOD = new AttributeModifier(UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef"),
                   "dragonhp_scaled", targetModValue, AttributeModifier.Operation.MULTIPLY_TOTAL);
                 dragonEntity.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(MAX_HP_MOD);
                 DragonfightMod.LOGGER.info("Applying/Updating Scaled Dragon HP. Base: {}, Scaled Max: {}", dragonEntity.getAttribute(Attributes.MAX_HEALTH).getBaseValue(), dragonEntity.getMaxHealth());
                 // Heal to full scaled HP only when modifier is first applied/changed
                 dragonEntity.setHealth(dragonEntity.getMaxHealth());
             }
         }
     }
    /**
     * Reset saved counters
     */
    private static void reset()
    {
        crystalRespawnPos = null;
        spawnAdds = false;
        spawnCounter = 0;

        if (DragonfightMod.server == null)
        {
            return;
        }

        for (final LivingEntity living : meleeAdds)
        {
            living.remove(Entity.RemovalReason.DISCARDED);
        }

        isFightRunning = false;
        flyingPlayers.clear();
        meleeAdds.clear();
    }

    /**
     * Spawn aggroed enderman as melee adds
     *
     * @param world
     */
    private static void spawnMeleeAdds(final Level world)
    {
        meleeAdds.removeIf(Entity::isRemoved);

        if (meleeAdds.size() >= (getDifficulty() * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier) || spawnOnDragonSitting.isEmpty())
        {
            return;
        }

        BlockPos searchedPos = BlockSearch.findAround(world,
          spawnPos,
          40,
          40,
          1,
          (level, checkPos) -> level.getBlockState(checkPos).isAir() &&
                               level.getBlockState(checkPos.above()).isAir() &&
                               level.getBlockState(checkPos.below()).isFaceSturdy(level, checkPos.below(), Direction.UP)
           );
        if (searchedPos == null)
        {
            searchedPos = spawnPos;
        }

        final LivingEntity entity =
          (LivingEntity) spawnEntity((ServerLevel) world, spawnOnDragonSitting.get(DragonfightMod.rand.nextInt(spawnOnDragonSitting.size())), createVec3(searchedPos));

        final List<Player> closesPlayers = world.getNearbyPlayers(TargetingConditions.DEFAULT, entity, entity.getBoundingBox().inflate(30));
        if (!closesPlayers.isEmpty())
        {
            final Player closestPlayer = closesPlayers.get(DragonfightMod.rand.nextInt(closesPlayers.size()));
            if (entity instanceof Mob)
            {
                ((Mob) entity).setTarget(closestPlayer);
            }
        }
        else
        {
            final List<Player> farPlayers = world.getNearbyPlayers(TargetingConditions.DEFAULT, entity, entity.getBoundingBox().inflate(60, 120, 60));
            if (!farPlayers.isEmpty())
            {
                final Player closestPlayer = farPlayers.get(DragonfightMod.rand.nextInt(farPlayers.size()));
                if (entity instanceof Mob)
                {
                    ((Mob) entity).setTarget(closestPlayer);
                }
            }
        }

        meleeAdds.add(entity);
    }

    /**
     * Respawns a crystal at the given pos
     *
     * @param pos   pos to respawn at
     * @param world world to respawn in
     */
    private static void respawnCrystalAt(final BlockPos pos, final Level world)
    {
        if (world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(2)).isEmpty())
        {
            // Respawn crystal
            final EndCrystal crystal = (EndCrystal) spawnEntity((ServerLevel) world, new ConfigurationCache.EntitySpawnData(EntityType.END_CRYSTAL, null), createVec3(pos));
            
            if (!spawnOnCrystalRespawn.isEmpty())
            {
                BlockPos pillarTopPos = new BlockPos (pos.getX()+2, pos.getY()-1, pos.getZ()-3);
                Vec3 pillarTopVec = createVec3(pillarTopPos);
                // Calculate potential ground location relative to this pillar
                BlockPos groundSpawnPos = new BlockPos(pillarTopPos.getX()+spawn_offset, 64, pillarTopPos.getZ()-spawn_offset); // Y64 as ground level for now
                Vec3 groundVec = createVec3(groundSpawnPos);

                DragonfightMod.LOGGER.info("Crystal respawning. Processing configured spawns...");

                for (ConfigurationCache.ConfiguredSpawnData configuredSpawnData : spawnOnCrystalRespawn) {
                     EntitySpawnData spawnData = configuredSpawnData.entityData();
                     String locationPref = configuredSpawnData.locationPreference();
                     ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type);

                     // Determine spawn location based on config preference
                     Vec3 targetSpawnVec;
                     if (locationPref.equals("pillar_top")) {
                          targetSpawnVec = pillarTopVec;
                          DragonfightMod.LOGGER.info("Spawning {} at pillar top.", typeId);
                     } else { // Default to ground_level
                          targetSpawnVec = groundVec;
                          DragonfightMod.LOGGER.info("Spawning {} at ground level near {}.", typeId, groundSpawnPos);
                     }

                     // Spawn the entity using the original data and target location
                    spawnEntity((ServerLevel) world, spawnData, targetSpawnVec);
                    DragonfightMod.LOGGER.info("[{}]Spawning {} at {}.",(ServerLevel) world,spawnData, targetSpawnVec);

                }
            }

            float f = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
            float f1 = (DragonfightMod.rand.nextFloat() - 0.5F) * 4.0F;
            float f2 = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, crystal.getX() + (double) f, crystal.getY() + 2.0D + (double) f1, crystal.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        CrystalLevelData.getForLevel((ServerLevel) world).removePosition(crystalRespawnPos);
        crystalRespawnPos = null;
        checkCrystalsToRespawn(world);
    }

    /**
     * Called when the dragon heals
     *
     * @param dragonEntity
     */
    public static void onDragonHeal(final EnderDragon dragonEntity)
    {
        dragonEntity.setHealth(Math.min(dragonEntity.getMaxHealth(), dragonEntity.getHealth() + (getDifficulty() / 7f)));
    }

    /**
     * Called when attacking a player
     *
     * @param damage
     * @return
     */
    public static float onAttackPlayer(final float damage)
    {
        return (float) ((damage + getDifficulty() / 2f) * DragonfightMod.config.getCommonConfig().dragonDamageModifier);
    }

    public static void onPhaseChange(
      final EnderDragonPhase<?> newPhase,
      final EnderDragonPhase<? extends DragonPhaseInstance> oldphase,
      final EnderDragon dragon)
    {
        // Avoid doing anything when we're reading a new entity, as nbt read does save the phases
        if (dragonEntity != dragon)
        {
            dragonEntity = dragon;
            return;
        }

        if (dragon == null || !(dragon.level() instanceof ServerLevel) || !dragonEntity.isAlive())
        {
            return;
        }

        final EndDragonFight manager = ((ServerLevel) dragon.level()).getDragonFight();
        if (manager == null || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty())
        {
            return;
        }

        notifyPlayer(dragon.level(), "Next phase:" + newPhase.toString());

        if (newPhase == EnderDragonPhase.TAKEOFF)
        {
            // Start spawning endermen
            spawnAdds = true;

            checkCrystalsToRespawn(dragon.level());
            if ((dragon.getHealth() / dragon.getMaxHealth()) < 0.25d && dragon.getDragonFight() != null)
            {
                
                dragon.level().playLocalSound(dragon.getX(),
                  dragon.getY(),
                  dragon.getZ(),
                  SoundEvents.ENDER_DRAGON_GROWL,
                  dragon.getSoundSource(),
                  2.5F,
                  0.8F + DragonfightMod.rand.nextFloat() * 0.3F,
                  false);
                for (final Player playerEntity : ((IDragonfightAccessor) dragon.getDragonFight()).getDragonEvent().getPlayers())
                {
                    playerEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 4));
                    playerEntity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 220));
                    playerEntity.sendSystemMessage(Component.literal("The dragon's power drains your spirit and our soul").withStyle(ChatFormatting.RED));
                }
                
            }
        }
        if ((newPhase == EnderDragonPhase.LANDING_APPROACH) || (newPhase == EnderDragonPhase.DYING))
        {
            // Stop spawning
            timeSinceLastLanding = 0;
            spawnAdds = false;
        }
        if (oldphase == EnderDragonPhase.LANDING && newPhase == EnderDragonPhase.SITTING_SCANNING)
        {
            timeSinceLastLanding = 0;

            final double healthpercent = (dragon.getHealth() / dragon.getMaxHealth());
            if (healthpercent < 0.5d)
            {
                advancingLightningCurrent = 6;
                advancingLightningStop = 50;
            }
            else
            {
                spawnLightningAtCircle(spawnPos, DragonfightMod.rand.nextInt(16) + 8, dragon.level());
            }
        }
    }

    private static void checkCrystalsToRespawn(final Level world)
    {
        if (crystalRespawnPos != null)
        {
            return;
        }

        final List<BlockPos> positions = new ArrayList<>(getCrystalRespawnPositions((ServerLevel) world));
        Collections.shuffle(positions);
        for (final BlockPos pos : positions)
        {
            if (world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(5)).isEmpty())
            {
                crystalRespawnPos = pos;
                crystalRespawnTimer = (int) Math.max(400, (CRYSTAL_RESPAWN_TIME / getDifficulty()) * DragonfightMod.config.getCommonConfig().crystalRespawnTimeModifier);
                notifyPlayer(world, "Adding respawn at :" + crystalRespawnPos + " in:" + crystalRespawnTimer);
                break;
            }
        }
    }

    /**
     * Spawns a circular lightning hit
     *
     * @param midPoint
     * @param radius
     * @param world
     */
    private static void spawnLightningAtCircle(final BlockPos midPoint, final int radius, final Level world)
    {
        if (DragonfightMod.config.getCommonConfig().disableLightning)
        {
            return;
        }

        Set<BlockPos> lightningPositions = getCircularPositionsAround(midPoint, radius, 15 - (radius / 10));
        for (final BlockPos lightningPos : lightningPositions)
        {
            notifyPlayer(world,
              "spawning plightning at!" + new BlockPos(lightningPos.getX(),
                world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY(),
                lightningPos.getZ()));

            final int yLevel = world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY();

            // Dont hit too varied height differences
            if (Math.abs(midPoint.getY() - yLevel) > 20)
            {
                continue;
            }

            LightningBolt lightningboltentity = EntityType.LIGHTNING_BOLT.create(world);
            lightningboltentity.moveTo(lightningPos.getX(), yLevel, lightningPos.getZ());
            lightningboltentity.setVisualOnly(false);
            world.addFreshEntity(lightningboltentity);
        }
        // targets player
        final EndDragonFight fightManager = ((ServerLevel) world).getDragonFight();
        if (fightManager != null) {
             DragonfightMod.LOGGER.info("Lightning striking players.");
             for (final Player player : ((IDragonfightAccessor) fightManager).getDragonEvent().getPlayers()) {
                  if (!player.isSpectator()) {
                       // Spawn lightning directly at player's location
                       LightningBolt playerLightning = EntityType.LIGHTNING_BOLT.create(world);
                       if (playerLightning != null) { // Check if creation succeeded
                            playerLightning.moveTo(player.getX(), player.getY(), player.getZ());
                            playerLightning.setVisualOnly(false);
                            world.addFreshEntity(playerLightning);
                       }
                  }
             }
        }
    }

    /**
     * Spawns a circular lightning hit
     *
     * @param midPoint
     * @param radius
     * @param world
     */
    private static void explodeInCircleAround(final BlockPos midPoint, final int radius, final Level world)
    {
        Set<BlockPos> explodePos = getCircularPositionsAround(midPoint, radius, 15);
        for (final BlockPos lightningPos : explodePos)
        {
            // i want to change this to hit everything within the circle randomly
            final int yLevel = 64;
            notifyPlayer(world,
              "spawning explosion at!" + new BlockPos(lightningPos.getX(),
                yLevel,
                lightningPos.getZ()));
            //final int yLevel = world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY();
            // explosion sometimes is goes off on air, ill just force it to the common Y position
            // Dont hit too varied height differences
            world.explode(dragonEntity,
                lightningPos.getX(),
                yLevel,
                lightningPos.getZ(),
                1 + getDifficulty() / 4f,
                false,
                Level.ExplosionInteraction.NONE);
        }
    }

    private static Set<BlockPos> getCircularPositionsAround(final BlockPos start, final int radius, int precision)
    {
        Set<BlockPos> positions = new HashSet<>();

        precision = (int) (precision / DragonfightMod.config.getCommonConfig().lightningExplosionDensity);
        final int randomOffset = DragonfightMod.rand.nextInt(40);
        for (int i = randomOffset; i < 360 + randomOffset; i += precision)
        {
            int x = (int) Math.round(radius * Math.cos(Math.toRadians(i)));
            int z = (int) Math.round(radius * Math.sin(Math.toRadians(i)));

            positions.add(start.offset(x, 0, z));
        }

        return positions;
    }

    /**
     * Notify OP's of the fights state for debugging
     *
     * @param world
     * @param message
     */
    public static void notifyPlayer(final Level world, final String message)
    {
        if (DragonfightMod.config.getCommonConfig().printDragonPhases)
        {
            for (final Player player : ((ServerLevel) world).players())
            {
                if (world.getServer() != null && ((ServerLevel) world).getServer().getProfilePermissions(player.getGameProfile()) > 0)
                {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        }
    }

    /**
     * Get the total difficulty number
     *
     * @return
     */
    private static int getDifficulty()
    {
        int difficulty = DragonfightMod.config.getCommonConfig().dragonDifficulty;

        if (dragonEntity != null)
        {
            difficulty += dragonEntity.level().getDifficulty().getId();
            if (dragonEntity.getDragonFight() != null)
            {
                difficulty += ((IDragonfightAccessor) dragonEntity.getDragonFight()).getDragonEvent().getPlayers().size();
            }
        }

        return Math.max(difficulty, 1);
    }

    private static Vec3 createVec3(final BlockPos pos)
    {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Entity spawnEntity(final ServerLevel world, ConfigurationCache.EntitySpawnData spawnData, Vec3 pos)
    {
        CompoundTag compoundtag = new CompoundTag();

        if (spawnData.nbt != null)
        {
            compoundtag = spawnData.nbt.copy();
        }

        compoundtag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type).toString());
        Entity entity = EntityType.loadEntityRecursive(compoundtag, world, (p_138828_) -> {

            final double offset = pos.x % 1d != 0d || pos.z % 1d != 0d ? 0 : 0.5;

            p_138828_.moveTo(pos.x + offset, pos.y, pos.z + offset, p_138828_.getYRot(), p_138828_.getXRot());
            return p_138828_;
        });

        if (entity == null)
        {
            return null;
        }

        entity.setUUID(UUID.randomUUID());

        if (entity instanceof Mob)
        {
            
            ((Mob) entity).finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData) null, (CompoundTag) null);
            DragonfightMod.LOGGER.info("spawning new entity:" + ((Mob) entity).finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData) null, (CompoundTag) null));
        }

        world.addFreshEntity(entity);
        DragonfightMod.LOGGER.info("newSpawned entity block position"+ entity.blockPosition());
        return entity;
    }

}