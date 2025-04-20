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
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
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

    /* 
     * Lazer attack
    */

    private static boolean isLaserAttacking = false;
    private static int laserAttackTick = 0;
    private static Vec3 laserTargetPos = null; // Where the beam is aimed
    private static Vec3 laserOriginPos = null; // Where the beam starts from (dragon mouth)
    private static final int LASER_CHARGE_TICKS = 100; // 3 seconds charge up
    private static final int LASER_FIRE_DURATION = 600; // 5 seconds firing
    private static final int LASER_COOLDOWN_TICKS = 600; // 30 seconds cooldown between attempts
    private static int laserCooldown = 200; // Ticks until next laser can be attempted
    private static final float LASER_DAMAGE_PER_TICK = 5.0f; // Damage per tick to players hit
    private static final double LASER_DAMAGE_RADIUS = 5; // Hitbox radius around beam path
    private static final double LASER_MAX_DISTANCE = 120.0; // Max range of the beam
    private static final double LASER_PARTICLE_STEP = 1; // How far beam travels between particle spawns
    private static final double LASER_ATTACK_ALTITUDE = 120.0;
    private static boolean preparingLaserSequence = false;
    private static boolean isFinalPhaseActive = false;

    private static final List<BlockPos> VANILLA_PILLAR_LOCATIONS = List.of(
        new BlockPos( 42, 0,  0),
        new BlockPos( 35, 0, 21),
        new BlockPos( 21, 0, 35),
        new BlockPos(  0, 0, 42),
        new BlockPos(-21, 0, 35),
        new BlockPos(-35, 0, 21),
        new BlockPos(-42, 0,  0),
        new BlockPos(-35, 0,-21),
        new BlockPos(-21, 0,-35),
        new BlockPos(  0, 0,-42)
    );

    private static final float    CRYSTAL_RESPAWN_TIME    = 8000;
    //private static final int      LIGHTNING_DESTROY_RANGE = 10 * 10;
    private static final float    ADD_TIMER               = 2000;
    private static       BlockPos crystalRespawnPos       = null;
    private static       int      crystalRespawnTimer     = 0;
    private static int globalLevitationEventTimer = 0;
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

    // public static  AttributeModifier AA_GRAVITY_MOD = new AttributeModifier("fall", 5.0, AttributeModifier.Operation.ADDITION);
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
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 1));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 300, 200));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 4));
        enderCrystalEntity.level().addFreshEntity(areaeffectcloudentity);

        addCrystalRespawnPos(enderCrystalEntity.blockPosition()); // Attempt to queue for respawn (will be blocked in final phase)

        if (!(damageSource.getEntity() instanceof Player))
        {
            return;
        }
        Player playerSource = (Player) damageSource.getEntity();
        // --- Trigger on ANY player crystal break ---
        DragonfightMod.LOGGER.info("Player crystal kill detected by {}!", playerSource);
        BlockPos destroyedCrystalPos = enderCrystalEntity.blockPosition();
        // Ground level: Fixed Y=64 (fornow) + configured offset
        BlockPos groundSpawnPos = new BlockPos(destroyedCrystalPos.getX()+spawn_offset, 64, destroyedCrystalPos.getZ()+spawn_offset);
        BlockPos pillarTopPos = new BlockPos(destroyedCrystalPos.getX()+1, destroyedCrystalPos.getY()-1, destroyedCrystalPos.getZ()+1);
        Vec3 groundVec = createVec3(groundSpawnPos);
        Vec3 pillarTopVec = createVec3(pillarTopPos);

        if (damageSource.getEntity() instanceof Player || damageSource.getEntity() instanceof LightningBolt)
        {
            // --- Spawn Mobs ---
            if (!spawnOnCrystalDeath.isEmpty()) {
                DragonfightMod.LOGGER.info("Processing crystal death spawns. Ground Target Base: {}, Pillar Target Base: {}", groundSpawnPos, pillarTopPos);

                for (ConfigurationCache.ConfiguredSpawnData configuredSpawnData : spawnOnCrystalDeath) {
                    EntitySpawnData spawnData = configuredSpawnData.entityData();
                    String locationPref = configuredSpawnData.locationPreference();
                    ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type);
                    DragonfightMod.LOGGER.info("Attempting to spawn {} with location pref '{}'", typeId, locationPref);

                    Vec3 baseSpawnVec;
                    if (locationPref.equals("pillar_top")) {
                        baseSpawnVec = pillarTopVec;
                        DragonfightMod.LOGGER.debug("Base location Pillar Top for {}.", typeId);
                    } else { // Default to ground_level
                        baseSpawnVec = groundVec;
                        DragonfightMod.LOGGER.debug("Base location Ground Level near {} for {}.", groundSpawnPos, typeId);
                    }

                    // --- Apply Spread & Spawn ---
                    double spreadRadius = 5.0; // Increased spread radius
                    Vec3 finalSpawnVec = baseSpawnVec;
                    boolean positionFound = false;
                    Level entityLevel = enderCrystalEntity.level(); // Use level from crystal
                    for (int attempt = 0; attempt < 10; attempt++) {
                        double offsetX = (entityLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0; // Corrected random range
                        double offsetZ = (entityLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0;
                        Vec3 potentialVec = baseSpawnVec.add(offsetX, 0.2, offsetZ); // Use 0.2 Y offset
                        BlockPos potentialBlockPos = BlockPos.containing(potentialVec);

                        if (entityLevel.getBlockState(potentialBlockPos).isAir() && entityLevel.getBlockState(potentialBlockPos.above()).isAir() && entityLevel.getBlockState(potentialBlockPos.below()).isFaceSturdy(entityLevel, potentialBlockPos.below(), Direction.UP)) {
                            finalSpawnVec = potentialVec;
                            positionFound = true;
                            break;
                        }
                    }
                    if (!positionFound) {
                        finalSpawnVec = baseSpawnVec.add(0, -1, 0); // Fallback to base + 0.2Y
                        DragonfightMod.LOGGER.debug("Could not find clear offset spawn for {}, using base.", typeId);
                    } else {
                        DragonfightMod.LOGGER.debug("Using offset spawn for {} at {}", typeId, finalSpawnVec);
                    }

                    Entity spawnedEntity = spawnEntity((ServerLevel) entityLevel, spawnData, finalSpawnVec);

                    // --- Force Player Target ---
                    if (spawnedEntity instanceof Mob mob) {
                        mob.setTarget(playerSource); // Target the player who broke the crystal
                        DragonfightMod.LOGGER.debug("Set target for {} to crystal breaker {}", typeId, playerSource.getName().getString());
                    }
                } // End for loop
            } // End if !isEmpty
        }
        // --- Dragon HP Reduction & Player Notification ---
        if (dragonEntity != null && dragonEntity.isAlive()) {
            float oldHealth = dragonEntity.getHealth();
            float maxHealth = dragonEntity.getMaxHealth();
            float damageAmount = maxHealth * 0.08f; // 8% of Max HP
            dragonEntity.setHealth(Math.max(1.0f, oldHealth - damageAmount)); // Reduce health, minimum 1
            DragonfightMod.LOGGER.info("Reducing dragon health by 8% ({}) due to crystal break. New health: {}", damageAmount, dragonEntity.getHealth());

            Component message = Component.literal("Crystal destroyed! Dragon health reduced by 8%!").withStyle(ChatFormatting.YELLOW);
            notifyAllPlayersInFight(enderCrystalEntity.level(), message);
        }
        // --- End HP Reduction ---
    } // End onCrystalDeath

    private static void addCrystalRespawnPos(final BlockPos position) {
        if (isFinalPhaseActive) return; // Do not add new positions in final phase
        if (dragonEntity == null || Math.sqrt(dragonEntity.blockPosition().distSqr(position)) > 1000) {
            return;
        }
        CrystalLevelData.getForLevel((ServerLevel) dragonEntity.level()).addPosition(position);
    }

    private static Set<BlockPos> getCrystalRespawnPositions(final ServerLevel level) {
        final Set<BlockPos> existing = new HashSet<>();
        for (final BlockPos pos : CrystalLevelData.getForLevel(level).getCrystalPendingRespawns()) {
            if (dragonEntity != null && Math.sqrt(dragonEntity.blockPosition().distSqr(pos)) > 1000) {
                continue;
            }
            existing.add(pos);
        }
        return existing;
    }

    public static void onWorldTick(final Level world)
    {
        final EndDragonFight manager = ((ServerLevel) world).getDragonFight();
        if (manager == null || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty() || dragonEntity == null)
        {
            reset();
            return;
        }

        if (!isFightRunning && dragonEntity.isAlive() && !((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty()) {
            // Fight starting procedures
            List<Monster> monsterEntities = world.getEntitiesOfClass(Monster.class, dragonEntity.getBoundingBox().inflate(150));
            for (final Monster entity : monsterEntities) {
                if (!(entity instanceof Npc) && !entity.isPersistenceRequired()) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            setDragonHealth(); // Set initial health correctly
            isFightRunning = true;
            isFinalPhaseActive = false; // Ensure final phase is off at start
            DragonfightMod.LOGGER.info("Dragon fight starting/resuming.");
        } else if (isFightRunning && (!dragonEntity.isAlive() || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty())) {
            // Fight ending procedures
            reset();
            return; // Stop processing if fight ended
        }

        if (!isFightRunning) {
            return; // Should not happen if above logic is correct, but safety check
        }
        if (laserCooldown > 0) {
            laserCooldown--;
        }

        // --- Final Phase Check ---
        if (!isFinalPhaseActive && dragonEntity.isAlive() && (dragonEntity.getHealth() / dragonEntity.getMaxHealth()) < 0.20f) {
            isFinalPhaseActive = true;
            DragonfightMod.LOGGER.info("Dragon below 10% HP - Entering FINAL PHASE!");
            notifyAllPlayersInFight(world, Component.literal("The Dragon enters its final stand! All crystals return!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            respawnAllCrystalsOnce(world);
            advancingExplosionCurrent = 20; advancingExplosionStop = 300; // near constant explosions
            laserCooldown = 0; // Allow laser immediately
        }
        // --- End Final Phase Check ---

        // --- Crystal Respawn Timer ---
        if (crystalRespawnPos != null && !isFinalPhaseActive) { // Check final phase here too
            if (--crystalRespawnTimer <= 0) {
                notifyPlayer(world, "Respawning crystal at" + crystalRespawnPos);
                respawnCrystalAt(crystalRespawnPos, world); // This will call checkCrystalsToRespawn internally
            } else if (crystalRespawnTimer == 200) {
                spawnLightningAtCircle(crystalRespawnPos, 10, world);
                spawnLightningAtCircle(crystalRespawnPos, 8, world);
                spawnLightningAtCircle(crystalRespawnPos, 4, world);
                spawnLightningAtCircle(crystalRespawnPos, 12, world);
                spawnLightningAtCircle(crystalRespawnPos, 16, world);
            }
        }

        // --- Health Print Timer ---
        if (DragonfightMod.config.getCommonConfig().printDragonPhases && ++print_hp_timer >= 20) { // Increment first, then check
            DragonfightMod.LOGGER.info("Dragon current health = {} / {}", dragonEntity.getHealth(), dragonEntity.getMaxHealth());
            print_hp_timer = 0;
        }

        // --- Handle Forced Laser Sequence ---
        if (isLaserAttacking) {
            ServerLevel serverLevel = (ServerLevel) world;
            laserAttackTick++;

            // --- Stage 1 & 2 Common: Force Position & Phase ---
            if (laserAttackTick < LASER_FIRE_DURATION) {
                 double targetY = LASER_ATTACK_ALTITUDE;
                 double currentY = dragonEntity.getY();
                 double climbRate = 4;
                 double newY = currentY;

                 if (laserAttackTick < 0) { // Climbing during charge
                     newY = Math.min(targetY, currentY + climbRate);
                 } else { // Maintain altitude during fire
                     if (Math.abs(currentY - targetY) > 0.5) {
                          newY = currentY + Math.signum(targetY - currentY) * climbRate * 0.5;
                     }
                 }
                 double targetX = spawnPos.getX() + (world.random.nextDouble() - 0.5) * 5;
                 double targetZ = spawnPos.getZ() + (world.random.nextDouble() - 0.5) * 5;

                 dragonEntity.teleportTo(targetX, newY, targetZ);
                 dragonEntity.setDeltaMovement(Vec3.ZERO);
                 dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);

                 if (laserAttackTick < 0) {
                      laserOriginPos = dragonEntity.getEyePosition().add(dragonEntity.getViewVector(1.0f).scale(3.0));
                 }
            }

            // --- Stage 1: Charging Phase Visuals & Setup ---
            if (laserAttackTick < 0) {
                 // Lightning Signal
                 if (world.getGameTime() % 5 == 0) { /* ... spawn visual lightning ... */ }
                 // Log completion
                 if (laserAttackTick == -1) {
                      if (laserOriginPos == null) { laserOriginPos = dragonEntity.getEyePosition().add(dragonEntity.getViewVector(1.0f).scale(3.0)); }
                      DragonfightMod.LOGGER.info("Laser charging complete, starting fire sequence. Origin: {}", laserOriginPos);
                 }
            }
            // --- Stage 2: Firing Phase Particles & Damage ---
            else if (laserAttackTick >= 0 && laserAttackTick < LASER_FIRE_DURATION) {
                 // Lightning Signal
                 if (world.getGameTime() % 5 == 0) { /* ... spawn visual lightning ... */ }
                 // Laser Beam
                 if (laserOriginPos != null && laserTargetPos != null) {
                     Vec3 direction = laserTargetPos.subtract(laserOriginPos).normalize();
                     for (double step = 0; step < LASER_MAX_DISTANCE; step += LASER_PARTICLE_STEP) {
                         Vec3 currentPoint = laserOriginPos.add(direction.scale(step));
                         serverLevel.sendParticles(ParticleTypes.FIREWORK, currentPoint.x, currentPoint.y, currentPoint.z, 1, 0, 0, 0, 0);
                         serverLevel.sendParticles(ParticleTypes.END_ROD, currentPoint.x, currentPoint.y, currentPoint.z, 1, (world.random.nextDouble()-0.5)*0.1, (world.random.nextDouble()-0.5)*0.1, (world.random.nextDouble()-0.5)*0.1, 0.05);
                         AABB damageArea = new AABB(currentPoint.x - LASER_DAMAGE_RADIUS, currentPoint.y - LASER_DAMAGE_RADIUS, currentPoint.z - LASER_DAMAGE_RADIUS, currentPoint.x + LASER_DAMAGE_RADIUS, currentPoint.y + LASER_DAMAGE_RADIUS, currentPoint.z + LASER_DAMAGE_RADIUS);
                         List<Player> playersHit = world.getEntitiesOfClass(Player.class, damageArea, player -> player.isAlive() && !player.isCreative() && !player.isSpectator());
                         for (Player target : playersHit) { target.hurt(world.damageSources().indirectMagic(dragonEntity, dragonEntity), LASER_DAMAGE_PER_TICK); }
                     }
                 } else { DragonfightMod.LOGGER.warn("Laser firing skipped: Origin or Target is null!"); }
            }
            // --- Stage 3: Finish Attack ---
            else if (laserAttackTick >= LASER_FIRE_DURATION) {
                isLaserAttacking = false;
                laserCooldown = isFinalPhaseActive ? LASER_COOLDOWN_TICKS / 3 : LASER_COOLDOWN_TICKS; // Faster cooldown in final phase
                DragonfightMod.LOGGER.info("Forced laser sequence finished.");
                dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF); // Force takeoff
            }
        }
        // --- End Forced Laser Sequence ---

        // --- Handle Laser Cooldown ---
        if (!isLaserAttacking && laserCooldown > 0) {
            laserCooldown--;
        }

        // --- Attempt to Trigger Laser (Randomly, if not forced) ---
        // This check might need refinement if the forced sequence trigger is preferred
        else if (laserCooldown <= 0 && !isLaserAttacking && dragonEntity != null && isFightRunning) {
            // Maybe check phase here if getType() works? Or just random chance?
            if (world.random.nextInt(600) == 0) { // Reduced chance for purely random trigger
                 isLaserAttacking = true;
                 laserAttackTick = -LASER_CHARGE_TICKS;
                 laserTargetPos = new Vec3(spawnPos.getX(), 60, spawnPos.getZ());
                 laserOriginPos = null;
                 DragonfightMod.LOGGER.info("Laser attack charging! (Random Trigger)");
                 notifyAllPlayersInFight(world, Component.literal("The dragon gathers immense energy!").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
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

        // --- Global Levitation Event ---
        int currentLevitationInterval = isFinalPhaseActive ? DragonfightMod.config.getCommonConfig().antiFlightIntervalTicks / 2 : DragonfightMod.config.getCommonConfig().antiFlightIntervalTicks;
        if (DragonfightMod.config.getCommonConfig().antiflightAbility) {
            globalLevitationEventTimer++;
            if (globalLevitationEventTimer >= currentLevitationInterval) {
                globalLevitationEventTimer = 0;
                DragonfightMod.LOGGER.info("Triggering global Levitation event.");
                final EndDragonFight fightmanager = ((ServerLevel) world).getDragonFight();
                if (fightmanager != null) {
                    for (final Player player : ((IDragonfightAccessor) fightmanager).getDragonEvent().getPlayers()) {
                        if (!player.isSpectator() && !player.isCreative()) { // Also check creative
                            int durationTicks = DragonfightMod.config.getCommonConfig().antiFlightEffectDurationTicks;
                            int amplifier = 220; // Keep your high amplifier
                            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, durationTicks, amplifier, false, false));
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

        if (dragonEntity != null && advancingExplosionCurrent == 0 && advancingLightningCurrent == 0 && !isLaserAttacking) {
        advancingExplosionCurrent = 10;
        advancingExplosionStop = isFinalPhaseActive ? 400 : 80; // Longer duration in final phase
   }

        // --- Advancing Explosion Execution ---
        int currentExplosionInterval = isFinalPhaseActive ? 100 : 200; // Twice as frequent in final phase
        if (advancingExplosionCurrent > 0 && world.getGameTime() % currentExplosionInterval == 0) {
            advancingExplosionCurrent += 3;
            explodeInCircleAround(spawnPos, advancingExplosionCurrent, world);
            if (advancingExplosionCurrent > advancingExplosionStop) {
                advancingExplosionCurrent = 0;
                advancingExplosionStop = 0;
            }
        }

        // --- Melee Add Spawning ---
        double currentAddTimerThreshold = ADD_TIMER / (getDifficulty() * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier);
        if (isFinalPhaseActive) currentAddTimerThreshold /= 2.0f; // Twice as frequent in final phase
        if (spawnAdds && !isLaserAttacking && spawnCounter++ > currentAddTimerThreshold) { // Don't spawn during laser
            notifyPlayer(world, "Spawning melee add");
            spawnMeleeAdds(world);
            spawnCounter = 0;
        }

        // --- Continuous Lightning Spam (Final Phase) ---
        if (isFinalPhaseActive && world.getGameTime() % 20 == 0) { // Every second
             spawnLightningAtCircle(spawnPos, world.random.nextInt(40) + 10, world); // Random radius circle
             // Also strike players
        }
        if(isFinalPhaseActive && world.getGameTime() % 200 == 0){
            final EndDragonFight fightManager = ((ServerLevel) world).getDragonFight();

            if (fightManager != null) {
                for (final Player player : ((IDragonfightAccessor) fightManager).getDragonEvent().getPlayers()) {
                     if (!player.isSpectator() && player.isAlive()) {
                          LightningBolt playerLightning = EntityType.LIGHTNING_BOLT.create(world);
                          if (playerLightning != null) {
                               playerLightning.moveTo(player.getX(), player.getY(), player.getZ());
                               playerLightning.setVisualOnly(false);
                               world.addFreshEntity(playerLightning);
                          }
                     }
                }
            }
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
        if (!isFightRunning)
        {
            return;
        }
    }

    /**
     * Re-adds the health modifiers
     */

     private static void setDragonHealth()
     {
         int hpOverride = DragonfightMod.config.getCommonConfig().dragonHpOverride;
         if (dragonEntity == null) return; // Safety check
         double currentBase = dragonEntity.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
         AttributeModifier existingMod = dragonEntity.getAttribute(Attributes.MAX_HEALTH).getModifier(MAX_HP_MOD.getId());
         boolean hasScaledMod = existingMod != null;
     
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
    private static void reset() {
        crystalRespawnPos = null;
        spawnAdds = false;
        spawnCounter = 0;
        isLaserAttacking = false;
        laserAttackTick = 0;
        laserTargetPos = null;
        laserOriginPos = null;
        laserCooldown = 0;
        isFinalPhaseActive = false; // Reset final phase flag
        advancingLightningCurrent = 0; // Reset lightning/explosion timers
        advancingLightningStop = 0;
        advancingExplosionCurrent = 0;
        advancingExplosionStop = 0;
        timeSinceLastLanding = 0;
        print_hp_timer = 0;
        globalLevitationEventTimer = 0;

        if (DragonfightMod.server == null) { return; }
        // Clear melee adds list safely
        if (meleeAdds != null) {
             meleeAdds.removeIf(entity -> {
                 if (entity != null && entity.isAlive()) {
                      entity.remove(Entity.RemovalReason.DISCARDED);
                 }
                 return true; // Remove all entries
             });
        } else {
             meleeAdds = new ArrayList<>(); // Ensure list exists
        }

        isFightRunning = false;
    }

    /**
     * Spawn aggroed enderman as melee adds
     *
     * @param world
     */
    private static void spawnMeleeAdds(final Level world) {
        // Keep improved version using maxMeleeAddsLimit and isFaceSturdy
        meleeAdds.removeIf(Entity::isRemoved);
        int limit = DragonfightMod.config.getCommonConfig().maxMeleeAddsLimit;
        if ((limit >= 0 && meleeAdds.size() >= limit) || spawnOnDragonSitting.isEmpty()) {
            return;
        }
        BlockPos searchedPos = BlockSearch.findAround(world, spawnPos, 40, 40, 1,
          (level, checkPos) -> level.getBlockState(checkPos).isAir() &&
                               level.getBlockState(checkPos.above()).isAir() &&
                               level.getBlockState(checkPos.below()).isFaceSturdy(level, checkPos.below(), Direction.UP)
           );
        if (searchedPos == null) { searchedPos = spawnPos; }

        final Entity spawnedEntity = spawnEntity((ServerLevel) world, spawnOnDragonSitting.get(DragonfightMod.rand.nextInt(spawnOnDragonSitting.size())), createVec3(searchedPos));
        if (spawnedEntity instanceof LivingEntity livingEntity) { // Check if it's LivingEntity before adding
             meleeAdds.add(livingEntity);
             // Targeting logic
             if (livingEntity instanceof Mob mob) {
                  Player targetPlayer = world.getNearestPlayer(mob, 150); // Use simple nearest player targeting
                  if (targetPlayer != null && !targetPlayer.isCreative() && !targetPlayer.isSpectator()) {
                       mob.setTarget(targetPlayer);
                  }
             }
        }
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
            if (pos.getX() * pos.getX() + pos.getZ() * pos.getZ() < 10*10) { // Simple squared distance check (within 10 blocks horizontally)
                DragonfightMod.LOGGER.info("Skipping crystal respawn at {} due to proximity to exit portal area.", pos);
                // Remove invalid position and try next one
                CrystalLevelData.getForLevel((ServerLevel) world).removePosition(pos);
                crystalRespawnPos = null; // Clear current target
                checkCrystalsToRespawn(world); // Trigger check for a different position
                return;
            }
            // Check if block below is bedrock (ensure it's on a proper pillar)
            if (!world.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                DragonfightMod.LOGGER.info("Skipping crystal respawn at {} as block below is not bedrock.", pos);
                // Remove invalid position and try next one
                CrystalLevelData.getForLevel((ServerLevel) world).removePosition(pos);
                crystalRespawnPos = null; // Clear current target
                checkCrystalsToRespawn(world); // Trigger check for a different position
                return;
            }
            if (!world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(2)).isEmpty()) {
            // Crystal already exists, maybe it spawned naturally? Remove from our list.
            DragonfightMod.LOGGER.info("Crystal already exists at {}, removing from respawn queue.", pos);
            CrystalLevelData.getForLevel((ServerLevel) world).removePosition(pos);
            crystalRespawnPos = null;
            checkCrystalsToRespawn(world);
            return; // Stop this attempt
            }
            // Respawn crystal
            DragonfightMod.LOGGER.info("Respawning crystal at {}", pos);
            final EndCrystal crystal = (EndCrystal) spawnEntity((ServerLevel) world, new ConfigurationCache.EntitySpawnData(EntityType.END_CRYSTAL, null), createVec3(pos));

            if (crystal != null) {
                int activeCrystals = countActiveCrystals(world);
                Component message = Component.literal("A crystal has respawned! [" + activeCrystals + " Active]").withStyle(ChatFormatting.LIGHT_PURPLE);
                notifyAllPlayersInFight(world, message);
            }

            if (!spawnOnCrystalRespawn.isEmpty())
            {
                BlockPos pillarTopPos = new BlockPos (pos.getX()+2, pos.getY()-1, pos.getZ()-3); //spawn them with offset
                Vec3 pillarTopVec = createVec3(pillarTopPos);
                // Calculate potential ground location relative to this pillar
                BlockPos groundSpawnPos = new BlockPos(pillarTopPos.getX()+spawn_offset, 64, pillarTopPos.getZ()-spawn_offset); // Y64 as ground level for now
                Vec3 groundVec = createVec3(groundSpawnPos);
                groundSpawnPos = findGroundPosNear(world, pillarTopPos);
                DragonfightMod.LOGGER.info("Crystal respawning. Processing configured spawns...");

                for (ConfigurationCache.ConfiguredSpawnData configuredSpawnData : spawnOnCrystalRespawn) {
                    EntitySpawnData spawnData = configuredSpawnData.entityData();
                    String locationPref = configuredSpawnData.locationPreference();
                    ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type);
   
                    // Determine BASE spawn location based on config preference
                    Vec3 baseSpawnVec;
                    if (locationPref.equals("pillar_top")) {
                         baseSpawnVec = pillarTopVec;
                         DragonfightMod.LOGGER.debug("Base location Pillar Top for {}.", typeId);
                    } else { // Default to ground_level
                         baseSpawnVec = groundVec;
                         DragonfightMod.LOGGER.debug("Base location Ground Level near {} for {}.", groundSpawnPos, typeId);
                    }
   
                    double spreadRadius = 10.0; // Increased spread radius
                    Vec3 finalSpawnVec = baseSpawnVec; // Start with base
                    boolean positionFound = false;
                    for (int attempt = 0; attempt < 10; attempt++) { // Try up to 5 times to find a clear offset spot
                        double offsetX = (world.random.nextDouble() - 0.5) * spreadRadius * 2.0; // Use 'world' variable here
                        double offsetZ = (world.random.nextDouble() - 0.5) * spreadRadius * 2.0;
                        Vec3 potentialVec = baseSpawnVec.add(offsetX, 0.1, offsetZ);
                        BlockPos potentialBlockPos = BlockPos.containing(potentialVec);
    
                        // Check if the potential spot and the space above are air
                        if (world.getBlockState(potentialBlockPos).isAir() && world.getBlockState(potentialBlockPos.above()).isAir()) {
                            finalSpawnVec = potentialVec; // Found a good spot
                            positionFound = true;
                            break; // Stop trying
                        }
                    }
                    if (!positionFound) {
                         // If still no spot found after attempts, fallback to base -1Y
                         finalSpawnVec = baseSpawnVec.add(spawn_offset, -1, spawn_offset);
                         DragonfightMod.LOGGER.debug("Could not find clear offset spawn for {}, using base.", typeId);
                    } else {
                         DragonfightMod.LOGGER.debug("Using offset spawn for {} at {}", typeId, finalSpawnVec);
                    }

                    // Spawn the entity using the original data and target location
                    Entity spawnedEntity = spawnEntity((ServerLevel) world, spawnData, finalSpawnVec);
   
                    if (spawnedEntity instanceof Mob mob) {
                        Player targetPlayer = null;
                        double minPlayerDistSq = Double.MAX_VALUE;
                        // Get players specifically from the boss fight context
                        final EndDragonFight fightManager = ((ServerLevel) world).getDragonFight(); // Use 'world' variable here
                        if (fightManager != null) {
                            // Change List<Player> to Collection<ServerPlayer> or List<ServerPlayer>
                            // Let's use Collection as it's more general if the exact return type isn't List
                            Collection<ServerPlayer> candidates = ((IDragonfightAccessor) fightManager).getDragonEvent().getPlayers();
                            // Iterate using ServerPlayer
                            for (ServerPlayer p : candidates) {
                                // Target only living players in survival/adventure within range
                                // No need to check !p.isCreative() as ServerPlayer doesn't have that directly, use abilities
                                // Use p.gameMode.isSurvival() or p.gameMode.isAdventure()
                                if (p != null && p.isAlive() && !p.isSpectator() && (p.gameMode.isSurvival())) {
                                    double distSq = mob.distanceToSqr(p);
                                    if (distSq < minPlayerDistSq && distSq < 150 * 150) { // Check range
                                        minPlayerDistSq = distSq;
                                        targetPlayer = p; // Can assign ServerPlayer to Player variable
                                    }
                                }
                            }
                        }

                        // Set target if a suitable one was found
                        if (targetPlayer != null) {
                            mob.setTarget(targetPlayer);
                            DragonfightMod.LOGGER.debug("Set target for {} to survival/adventure player {}", typeId, targetPlayer.getName().getString());
                        } else {
                            DragonfightMod.LOGGER.debug("Could not find suitable (survival/adventure) player target for {}", typeId);
                        }
                    }
                    // --- END FORCE PLAYER TARGET ---
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
        if (dragonEntity != dragon){
            dragonEntity = dragon;
            return;
        }
        if (dragon == null || !(dragon.level() instanceof ServerLevel) || !dragonEntity.isAlive()){
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
            spawnAdds = true; // Always start melee adds on takeoff
            if (!isFinalPhaseActive) 
            {
                checkCrystalsToRespawn(dragon.level());
            }
                if (preparingLaserSequence && !isLaserAttacking && laserCooldown <= 0 && dragonEntity != null && isFightRunning) {
                    // If we were preparing the laser, this takeoff is the start of the climb
                    DragonfightMod.LOGGER.info("Dragon taking off to initiate laser sequence climb.");
                    EnderDragonPhase<?> currentPhaseType = dragonEntity.getPhaseManager().getCurrentPhase().getPhase();
                    // The actual climb/charge/fire logic is handled in onWorldTick based on the 'preparingLaserSequence' flag
                    if (currentPhaseType == EnderDragonPhase.SITTING_SCANNING || currentPhaseType == EnderDragonPhase.SITTING_ATTACKING || currentPhaseType == EnderDragonPhase.TAKEOFF) {
                        isLaserAttacking = true; // Start the actual sequence
                        preparingLaserSequence = false; // Consumed the preparation flag
                        laserAttackTick = -LASER_CHARGE_TICKS; // Start charge timer
                        laserTargetPos = new Vec3(spawnPos.getX(), 60, spawnPos.getZ());
                        laserOriginPos = null; // Will be set during climb
          
                        DragonfightMod.LOGGER.info("Starting forced laser sequence climb!");
                        notifyAllPlayersInFight(dragon.level(), Component.literal("The dragon takes flight for a devastating attack!").withStyle(ChatFormatting.LIGHT_PURPLE));
                        // Ensure it's trying to fly up
                        dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
                    }
                } else {
                        // Normal takeoff (laser not prepared), handle low health effects
                        if ((dragon.getHealth() / dragon.getMaxHealth()) < 0.70d && dragon.getDragonFight() != null)
                        {   // trigger when 50% hp
                            
                            dragon.level().playLocalSound(dragon.getX(),
                            dragon.getY(),
                            dragon.getZ(),
                            SoundEvents.ENDER_DRAGON_GROWL,
                            dragon.getSoundSource(),
                            2.5F,
                            0.8F + DragonfightMod.rand.nextFloat() * 0.3F,
                            false);
                            for (final Player playerEntity : ((IDragonfightAccessor) manager).getDragonEvent().getPlayers()) {
                                playerEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 5));
                                playerEntity.sendSystemMessage(Component.literal("The Dragon's power withers your soul").withStyle(ChatFormatting.BLACK));
                            }
                        }
                
                    }
                    // --- Handle ACTIVE Laser Sequence (Charge/Fire/Finish) ---
            if (isLaserAttacking) {
                ServerLevel serverLevel = (ServerLevel) dragon.level();
                laserAttackTick++;

                // --- Force Position & Phase (During Charge & Fire) ---
                if (laserAttackTick < LASER_FIRE_DURATION && dragonEntity != null) {
                    double targetY = LASER_ATTACK_ALTITUDE;
                    double currentY = dragonEntity.getY();
                    double climbRate = 2;
                    double newY = currentY;

                    if (laserAttackTick < 0) { // Climbing during charge
                        newY = Math.min(targetY, currentY + climbRate);
                    } else { // Maintain altitude during fire
                        if (Math.abs(currentY - targetY) > 0.5) {
                            newY = currentY + Math.signum(targetY - currentY) * climbRate * 0.5;
                        }
                    }
                    double targetX = spawnPos.getX() + (dragon.level().random.nextDouble() - 0.5) * 5;
                    double targetZ = spawnPos.getZ() + (dragon.level().random.nextDouble() - 0.5) * 5;

                    dragonEntity.teleportTo(targetX, newY, targetZ);
                    dragonEntity.setDeltaMovement(Vec3.ZERO);
                    // Continuously set HOLDING_PATTERN to try and keep it hovering
                    if (dragonEntity.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) {
                        dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                    }

                    // Update laser origin continuously during climb/hover
                    laserOriginPos = dragonEntity.getEyePosition().add(dragonEntity.getViewVector(1.0f).scale(3.0));
                }

                // --- Stage 1: Charging Phase Visuals ---
                if (laserAttackTick < 0) {
                    // Lightning Signal
                    if (dragon.level().getGameTime() % 5 == 0) {
                        BlockPos lightningCenter = spawnPos.below(spawnPos.getY() - dragon.level().getMinBuildHeight());
                        if (lightningCenter.getY() < dragon.level().getMinBuildHeight()) { lightningCenter = lightningCenter.atY(dragon.level().getMinBuildHeight()); }
                        LightningBolt signalLightning = EntityType.LIGHTNING_BOLT.create(dragon.level());
                        if (signalLightning != null) {
                            signalLightning.moveTo(lightningCenter.getX() + dragon.level().random.nextInt(10)-5, lightningCenter.getY(), lightningCenter.getZ() + dragon.level().random.nextInt(10)-5);
                            signalLightning.setVisualOnly(true);
                            dragon.level().addFreshEntity(signalLightning);
                        }
                    }
                    // Log completion
                    if (laserAttackTick == -1) {
                        DragonfightMod.LOGGER.info("Laser charging complete, starting fire sequence. Origin: {}", laserOriginPos);
                    }
                }
                // --- Stage 2: Firing Phase Particles & Damage ---
                else if (laserAttackTick >= 0 && laserAttackTick < LASER_FIRE_DURATION) {
                    // Lightning Signal
                    if (dragon.level().getGameTime() % 5 == 0) {
                        BlockPos lightningCenter = spawnPos.below(spawnPos.getY() - dragon.level().getMinBuildHeight());
                        if (lightningCenter.getY() < dragon.level().getMinBuildHeight()) { lightningCenter = lightningCenter.atY(dragon.level().getMinBuildHeight()); }
                        LightningBolt signalLightning = EntityType.LIGHTNING_BOLT.create(dragon.level());
                        if (signalLightning != null) {
                            signalLightning.moveTo(lightningCenter.getX() + dragon.level().random.nextInt(10)-5, lightningCenter.getY(), lightningCenter.getZ() + dragon.level().random.nextInt(10)-5);
                            signalLightning.setVisualOnly(true);
                            dragon.level().addFreshEntity(signalLightning);
                        }
                    }
                    // Laser Beam
                    if (laserOriginPos != null && laserTargetPos != null) {
                        DragonfightMod.LOGGER.info("Laser Beam Starting...");
                        Vec3 direction = laserTargetPos.subtract(laserOriginPos).normalize();
                        for (double step = 0; step < LASER_MAX_DISTANCE; step += LASER_PARTICLE_STEP) {
                            Vec3 currentPoint = laserOriginPos.add(direction.scale(step));
                            serverLevel.sendParticles(ParticleTypes.FIREWORK, currentPoint.x, currentPoint.y, currentPoint.z, 1, 0, 0, 0, 0);
                            serverLevel.sendParticles(ParticleTypes.END_ROD, currentPoint.x, currentPoint.y, currentPoint.z, 1, (dragon.level().random.nextDouble()-0.5)*0.1, (dragon.level().random.nextDouble()-0.5)*0.1, (dragon.level().random.nextDouble()-0.5)*0.1, 0.05);
                            AABB damageArea = new AABB(currentPoint.x - LASER_DAMAGE_RADIUS, currentPoint.y - LASER_DAMAGE_RADIUS, currentPoint.z - LASER_DAMAGE_RADIUS,
                                currentPoint.x + LASER_DAMAGE_RADIUS, currentPoint.y + LASER_DAMAGE_RADIUS, currentPoint.z + LASER_DAMAGE_RADIUS);
                            List<Player> playersHit = dragon.level().getEntitiesOfClass(Player.class, damageArea,
                                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                            );
                            for (Player target : playersHit) {
                                target.hurt(dragon.level().damageSources().indirectMagic(dragonEntity, dragonEntity), LASER_DAMAGE_PER_TICK);
                            }
                        }
                    } else { DragonfightMod.LOGGER.warn("Laser firing skipped: Origin or Target is null!"); }
                }
                // --- Stage 3: Finish Attack ---
                else if (laserAttackTick >= LASER_FIRE_DURATION) {
                    isLaserAttacking = false;
                    preparingLaserSequence = false; // Ensure flag is reset
                    laserCooldown = isFinalPhaseActive ? LASER_COOLDOWN_TICKS / 3 : LASER_COOLDOWN_TICKS;
                    DragonfightMod.LOGGER.info("Forced laser sequence finished.");
                    if (dragonEntity != null) { // Ensure dragon still exists
                        dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF); // Force Takeoff
                    }
                }
            }
            // --- End Forced Laser Sequence ---
        }
        if ((newPhase == EnderDragonPhase.LANDING_APPROACH) || (newPhase == EnderDragonPhase.DYING)) {
            timeSinceLastLanding = 0;
            spawnAdds = false; // Stop melee adds timer
            if (isLaserAttacking) { // Interrupt laser if landing/dying
                isLaserAttacking = false;
                DragonfightMod.LOGGER.info("Laser sequence interrupted by landing/death.");
            }
        }
        if (oldphase == EnderDragonPhase.LANDING && newPhase == EnderDragonPhase.SITTING_SCANNING) {
            timeSinceLastLanding = 0;
            // --- PREPARE FORCED LASER SEQUENCE ---
            if (!isLaserAttacking && laserCooldown <= 0 && !preparingLaserSequence) {
                preparingLaserSequence = true; // Set flag to prepare
                DragonfightMod.LOGGER.info("Dragon landed - Preparing forced laser sequence.");
                notifyAllPlayersInFight(dragon.level(), Component.literal("The dragon has perched").withStyle(ChatFormatting.RED));
            } else {
                 // Normal landing lightning
                final double healthpercent = (dragon.getHealth() / dragon.getMaxHealth());
                if (healthpercent < 0.8d) { 
                    advancingLightningCurrent = 10; 
                    advancingLightningStop = 100; 
                }
                 else { spawnLightningAtCircle(spawnPos, DragonfightMod.rand.nextInt(16) + 8, dragon.level()); }
            }
        }
        if (newPhase == EnderDragonPhase.TAKEOFF || newPhase == EnderDragonPhase.LANDING_APPROACH) {
            if (preparingLaserSequence && !isLaserAttacking) { // Reset if it didn't transition to attack
                 preparingLaserSequence = false;
            }
        }
    }


    private static void checkCrystalsToRespawn(final Level world) {
        if (isFinalPhaseActive || crystalRespawnPos != null) { // Check final phase and if already targeting one
            return;
        }
        final List<BlockPos> positions = new ArrayList<>(getCrystalRespawnPositions((ServerLevel) world));
        Collections.shuffle(positions);
        for (final BlockPos pos : positions) {
            if (world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(5)).isEmpty()) {
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
    private static void explodeInCircleAround(final BlockPos midPoint, final int radius, final Level world) {
        // Get positions for a filled circle, not just the edge
        Set<BlockPos> circlePositions = getFilledCirclePositions(midPoint, radius);
        int explosionCount = 10 + (radius / 3);
        List<BlockPos> explosionPositions = getRandomPositionsFromSet(circlePositions, explosionCount);
        
        for (final BlockPos explosionPos : explosionPositions) {
            // Find the surface level by checking down until we find a non-air block
            BlockPos surfacePos = findSurfacePosition(world, explosionPos);
            
            if (surfacePos != null) {
                notifyPlayer(world, "Spawning explosion at: " + surfacePos);
                
                world.explode(dragonEntity,
                    surfacePos.getX() + 0.5,
                    surfacePos.getY() + 1,  // Explode just above the surface
                    surfacePos.getZ() + 0.5,
                    5.0f + getDifficulty() / 2.0f,
                    false,
                    Level.ExplosionInteraction.MOB);
            }
        }
    }

    private static Set<BlockPos> getFilledCirclePositions(BlockPos center, int radius) {
        Set<BlockPos> positions = new HashSet<>();
        int radiusSquared = radius * radius;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Check if this position is within the circle (x²+z² ≤ r²)
                if (x * x + z * z <= radiusSquared) {
                    positions.add(new BlockPos(center.getX() + x, center.getY(), center.getZ() + z));
                }
            }
        }
        
        return positions;
    }
    
    private static List<BlockPos> getRandomPositionsFromSet(Set<BlockPos> positions, int count) {
        List<BlockPos> posList = new ArrayList<>(positions);
        Collections.shuffle(posList, DragonfightMod.rand);  // Use the mod's random instance
        
        // Return either the requested count or all positions if there are fewer than requested
        return posList.subList(0, Math.min(count, posList.size()));
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

    private static void notifyAllPlayersInFight(Level world, Component message) {
        if (!(world instanceof ServerLevel serverLevel)) return;
        final EndDragonFight fightManager = serverLevel.getDragonFight();
        if (fightManager != null) {
            for (final Player player : ((IDragonfightAccessor) fightManager).getDragonEvent().getPlayers()) {
                if (player != null && player.isAlive()) { // Check if player is valid
                    player.sendSystemMessage(message); // Send as non-chat overlay message
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

    private static void respawnAllCrystalsOnce(Level world) {
        DragonfightMod.LOGGER.info("Attempting to respawn all missing crystals for final phase...");
        // Use hardcoded vanilla pillar base XZ locations
        List<BlockPos> pillarLocations = VANILLA_PILLAR_LOCATIONS;
        int spawned = 0;
        for (BlockPos pillarBase : pillarLocations) {
             // Find the Y level of the bedrock top
             int bedrockY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, pillarBase.getX(), pillarBase.getZ());
             // Calculate the position *above* the bedrock where the crystal should spawn
             BlockPos crystalSpawnPos = new BlockPos(pillarBase.getX(), bedrockY + 1, pillarBase.getZ());
             // Get the position of the bedrock block itself for validation
             BlockPos bedrockPos = crystalSpawnPos.below();

             // Validate position before spawning
             // 1. Check distance from center (using XZ from pillarBase)
             if (pillarBase.getX() * pillarBase.getX() + pillarBase.getZ() * pillarBase.getZ() < 10*10) continue;
             // 2. Check if the block AT the calculated heightmap Y IS bedrock
             if (!world.getBlockState(bedrockPos).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                  DragonfightMod.LOGGER.warn("Skipping final phase crystal at base {} as block at calculated Y={} is not bedrock.", pillarBase.atY(0), bedrockY);
                  continue;
             }
             // 3. Check if a crystal already exists at the spawn position
             if (world.getEntitiesOfClass(EndCrystal.class, new AABB(crystalSpawnPos).inflate(1)).isEmpty()) { // Check AABB around the spawn pos
                 DragonfightMod.LOGGER.debug("Final phase: Respawning missing crystal at {}", crystalSpawnPos);
                 // Spawn the entity at the calculated crystal spawn position (Y+1)
                 spawnEntity((ServerLevel) world, new ConfigurationCache.EntitySpawnData(EntityType.END_CRYSTAL, null), createVec3(crystalSpawnPos));
                 spawned++;
             }
        }
         DragonfightMod.LOGGER.info("Final phase: Respawned {} crystals.", spawned);
    }

    private static int countActiveCrystals(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) return 0;
        // EndDragonFight fight = serverLevel.getDragonFight(); // Not needed just to count

        List<BlockPos> pillarLocations = VANILLA_PILLAR_LOCATIONS; // Use hardcoded XZ
        int count = 0;
        for (BlockPos pillarBase : pillarLocations) {
            // Find the Y level of the bedrock top
            int bedrockY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, pillarBase.getX(), pillarBase.getZ());
            // Calculate the position *above* bedrock where the crystal SHOULD be
            BlockPos crystalCheckPos = new BlockPos(pillarBase.getX(), bedrockY + 1, pillarBase.getZ());

            // Check if a crystal exists near that calculated spawn position
             if (!world.getEntitiesOfClass(EndCrystal.class, new AABB(crystalCheckPos).inflate(1)).isEmpty()) { // Check AABB around spawn pos
                 count++;
             }
        }
        return count;
    }

    private static BlockPos findGroundPosNear(Level world, BlockPos pillarPos) {
        for (int offset = 0; offset < 5; offset++) {
             BlockPos potentialGround = new BlockPos(pillarPos.getX() + world.random.nextInt(offset * 2 + 1) - offset,
                                                    pillarPos.getY(),
                                                    pillarPos.getZ() + world.random.nextInt(offset * 2 + 1) - offset);
             BlockPos surfacePos = world.getHeightmapPos(WORLD_SURFACE, potentialGround);
             if (!world.getBlockState(surfacePos.below()).isAir() && world.getBlockState(surfacePos).isAir() && world.getBlockState(surfacePos.above()).isAir()) {
                 if (surfacePos.getY() > world.getMinBuildHeight() + 5 && surfacePos.getY() > 50) {
                      return surfacePos;
                 }
             }
        }
        return world.getHeightmapPos(WORLD_SURFACE, new BlockPos(world.random.nextInt(10)-5, 65, world.random.nextInt(10)-5));
    }

    private static BlockPos findSurfacePosition(Level world, BlockPos pos) {
        int startY = 70;
        

        if (pos.getY() < startY) {
            startY = pos.getY();
        }
        
        BlockPos checkPos = new BlockPos(pos.getX(), startY, pos.getZ());
        
        boolean inAir = world.getBlockState(checkPos).isAir();
        
        if (!inAir) {
            while (!world.getBlockState(checkPos).isAir() && checkPos.getY() < world.getMaxBuildHeight() - 2) {
                checkPos = checkPos.above();
            }
        }
        while (world.getBlockState(checkPos).isAir() && checkPos.getY() > 40) {
            checkPos = checkPos.below();
        }
        if (!world.getBlockState(checkPos).isAir()) {
            return checkPos;
        }
        
        // Fallback to the original position if we didn't find a surface
        return pos;
    }

}