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
    private static final int LASER_CHARGE_TICKS = 60; // 3 seconds charge up
    private static final int LASER_FIRE_DURATION = 100; // 5 seconds firing
    private static final int LASER_COOLDOWN_TICKS = 600; // 30 seconds cooldown between attempts
    private static int laserCooldown = 0; // Ticks until next laser can be attempted
    private static final float LASER_DAMAGE_PER_TICK = 2.0f; // Damage per tick to players hit
    private static final double LASER_DAMAGE_RADIUS = 1.5; // Hitbox radius around beam path
    private static final double LASER_MAX_DISTANCE = 120.0; // Max range of the beam
    private static final double LASER_PARTICLE_STEP = 0.5; // How far beam travels between particle spawns
    private static final double LASER_ATTACK_ALTITUDE = 120.0;

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

                for (ConfigurationCache.ConfiguredSpawnData configuredSpawnData : spawnOnCrystalDeath) {
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
    
                    
                    double spreadRadius = 5.0; // Increased spread radius
                    Vec3 finalSpawnVec = baseSpawnVec; // Start with base
                    boolean positionFound = false;
                    for (int attempt = 0; attempt < 5; attempt++) { // Try up to 5 times to find a clear offset spot
                        // Use enderCrystalEntity.level() here:
                        double offsetX = (enderCrystalEntity.level().random.nextDouble() - 0.5) * spreadRadius * 2.0;
                        double offsetZ = (enderCrystalEntity.level().random.nextDouble() - 0.5) * spreadRadius * 2.0;
                        Vec3 potentialVec = baseSpawnVec.add(offsetX, 0.1, offsetZ);
                        BlockPos potentialBlockPos = BlockPos.containing(potentialVec);

                        // Check if the potential spot and the space above are air
                        // Use enderCrystalEntity.level() here:
                        if (enderCrystalEntity.level().getBlockState(potentialBlockPos).isAir() && enderCrystalEntity.level().getBlockState(potentialBlockPos.above()).isAir()) {
                            finalSpawnVec = potentialVec; // Found a good spot
                            positionFound = true;
                            break; // Stop trying
                        }
                    }
                    if (!positionFound) {
                         // If still no spot found after attempts, fallback to base + 0.1Y
                         finalSpawnVec = baseSpawnVec.add(0, 0.1, 0);
                         DragonfightMod.LOGGER.debug("Could not find clear offset spawn for {}, using base.", typeId);
                    } else {
                         DragonfightMod.LOGGER.debug("Using offset spawn for {} at {}", typeId, finalSpawnVec);
                    }
    
                }
                
            }
        }
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
                if (laserCooldown > 0) {
                    laserCooldown--;
                }
            }
            isFightRunning = false;
        }

        if (!isFightRunning)
        {
            return;
        }

        if (isLaserAttacking) {
            ServerLevel serverLevel = (ServerLevel) world;
            laserAttackTick++;

            // --- Stage 1: Force Dragon Upwards (during "charge" ticks) ---
            if (laserAttackTick < 0) { // Still in charge/fly-up phase
                // Teleport dragon upwards towards target altitude
                double targetY = LASER_ATTACK_ALTITUDE;
                double currentY = dragonEntity.getY();
                double climbRate = 1.0; // How fast it climbs (blocks per tick)
                double newY = Math.min(targetY, currentY + climbRate); // Move up, but don't overshoot

                // Keep X/Z roughly centered or let it drift slightly
                double targetX = spawnPos.getX() + (world.random.nextDouble() - 0.5) * 10; // Slight drift
                double targetZ = spawnPos.getZ() + (world.random.nextDouble() - 0.5) * 10;

                dragonEntity.teleportTo(targetX, newY, targetZ);
                // Try to keep it hovering
                dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);

                // --- Continuous Lightning Signal ---
                // Spawn lightning frequently near the center portal while charging/climbing
                if (world.getGameTime() % 5 == 0) { // Every 1/4 second
                    BlockPos lightningCenter = spawnPos.below(spawnPos.getY() - 64); // Target bedrock level
                    LightningBolt signalLightning = EntityType.LIGHTNING_BOLT.create(world);
                    if (signalLightning != null) {
                        signalLightning.moveTo(lightningCenter.getX() + world.random.nextInt(5)-2,
                                               lightningCenter.getY(),
                                               lightningCenter.getZ() + world.random.nextInt(5)-2);
                        signalLightning.setVisualOnly(true); // Visual only for signal
                        world.addFreshEntity(signalLightning);
                    }
                }

                // Set laser origin once dragon is near target altitude (end of charge)
                if (laserAttackTick == -1) { // Last tick of charge
                     laserOriginPos = dragonEntity.getEyePosition().add(dragonEntity.getViewVector(1.0f).scale(3.0));
                     DragonfightMod.LOGGER.info("Laser charging complete, starting fire sequence.");
                }

            }
            // --- Stage 2: Firing Phase (laserAttackTick >= 0) ---
            else if (laserAttackTick >= 0 && laserAttackTick < LASER_FIRE_DURATION) {
                dragonEntity.teleportTo(dragonEntity.getX(), LASER_ATTACK_ALTITUDE, dragonEntity.getZ());
                dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);

                // --- Continuous Lightning Signal (During Firing) ---
                 if (world.getGameTime() % 5 == 0) {
                     BlockPos lightningCenter = spawnPos.below(spawnPos.getY() - 64);
                     LightningBolt signalLightning = EntityType.LIGHTNING_BOLT.create(world);
                     if (signalLightning != null) {
                         signalLightning.moveTo(lightningCenter.getX() + world.random.nextInt(5)-2,
                                                lightningCenter.getY(),
                                                lightningCenter.getZ() + world.random.nextInt(5)-2);
                         signalLightning.setVisualOnly(true);
                         world.addFreshEntity(signalLightning);
                     }
                 }

                // --- Laser Beam Particles and Damage ---
                if (laserOriginPos != null && laserTargetPos != null) {
                    Vec3 direction = laserTargetPos.subtract(laserOriginPos).normalize();
                    for (double step = 0; step < LASER_MAX_DISTANCE; step += LASER_PARTICLE_STEP) {
                        Vec3 currentPoint = laserOriginPos.add(direction.scale(step));

                        // Spawn Particles (Using currentPoint coordinates)
                        serverLevel.sendParticles(ParticleTypes.FIREWORK, currentPoint.x, currentPoint.y, currentPoint.z, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.END_ROD, currentPoint.x, currentPoint.y, currentPoint.z, 1, (world.random.nextDouble()-0.5)*0.1, (world.random.nextDouble()-0.5)*0.1, (world.random.nextDouble()-0.5)*0.1, 0.05);

                        // Damage Players (Define AABB correctly)
                        AABB damageArea = new AABB(currentPoint.x - LASER_DAMAGE_RADIUS, currentPoint.y - LASER_DAMAGE_RADIUS, currentPoint.z - LASER_DAMAGE_RADIUS,
                                                   currentPoint.x + LASER_DAMAGE_RADIUS, currentPoint.y + LASER_DAMAGE_RADIUS, currentPoint.z + LASER_DAMAGE_RADIUS);
                        // Get only PLAYERS within the damage area (Correct filter)
                        List<Player> playersHit = world.getEntitiesOfClass(Player.class, damageArea,
                            // Filter: Only hit players who are alive and not in creative/spectator mode
                            player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        );
                        // Loop through the filtered players
                        for (Player target : playersHit) {
                             target.hurt(world.damageSources().indirectMagic(dragonEntity, dragonEntity), LASER_DAMAGE_PER_TICK);
                        }
                    }
                }
            }
            // --- Stage 3: Finish Attack ---
            else if (laserAttackTick >= LASER_FIRE_DURATION) {
                isLaserAttacking = false;
                laserCooldown = LASER_COOLDOWN_TICKS; // Start cooldown
                DragonfightMod.LOGGER.info("Forced laser sequence finished.");
                // Allow dragon to resume normal behavior (e.g., take off properly)
                // It should already be in HOLDING_PATTERN, setting TAKEOFF might be good
                dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
            }
        }
        // --- End Forced Laser Sequence ---

        // --- Handle Laser Cooldown (Keep this separate) ---
        if (!isLaserAttacking && laserCooldown > 0) { // Only tick down if not attacking
            laserCooldown--;
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

        isLaserAttacking = false;
        laserAttackTick = 0;
        laserTargetPos = null;
        laserOriginPos = null;
        laserCooldown = 0;

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
   
                    // Determine BASE spawn location based on config preference
                    Vec3 baseSpawnVec;
                    if (locationPref.equals("pillar_top")) {
                         baseSpawnVec = pillarTopVec;
                         DragonfightMod.LOGGER.debug("Base location Pillar Top for {}.", typeId);
                    } else { // Default to ground_level
                         baseSpawnVec = groundVec;
                         DragonfightMod.LOGGER.debug("Base location Ground Level near {} for {}.", groundSpawnPos, typeId);
                    }
   
                    double spreadRadius = 5.0; // Increased spread radius
                    Vec3 finalSpawnVec = baseSpawnVec; // Start with base
                    boolean positionFound = false;
                    for (int attempt = 0; attempt < 5; attempt++) { // Try up to 5 times to find a clear offset spot
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
                         // If still no spot found after attempts, fallback to base + 0.1Y
                         finalSpawnVec = baseSpawnVec.add(0, 0.1, 0);
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
            if ((dragon.getHealth() / dragon.getMaxHealth()) < 0.50d && dragon.getDragonFight() != null)
            {   // trigger when 50% hp
                
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
            if (isLaserAttacking) {
                isLaserAttacking = false;
                DragonfightMod.LOGGER.info("Forced laser sequence interrupted by landing/death.");
            }
        }
        if (oldphase == EnderDragonPhase.LANDING && newPhase == EnderDragonPhase.SITTING_SCANNING)
        {
            timeSinceLastLanding = 0;
            if (!isLaserAttacking && laserCooldown <= 0) { // Only start if not already attacking or cooling down
                isLaserAttacking = true; // Use this flag to signify the whole sequence
                laserAttackTick = -LASER_CHARGE_TICKS; // Start with charging phase
                // Target position for laser beam remains the center base
                laserTargetPos = new Vec3(spawnPos.getX(), 60, spawnPos.getZ());
                laserOriginPos = null; // Origin will be set once dragon reaches altitude

                DragonfightMod.LOGGER.info("Dragon landed - Initiating forced laser sequence.");
                notifyPlayer(dragon.level(), "The dragon prepares a powerful attack!");

                // Force the dragon to take off immediately
                dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
            } else{
                final double healthpercent = (dragon.getHealth() / dragon.getMaxHealth());
                if (healthpercent < 0.8d)
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
                Level.ExplosionInteraction.MOB);
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