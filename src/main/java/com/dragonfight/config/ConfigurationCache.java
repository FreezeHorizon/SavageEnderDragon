package com.dragonfight.config;


import com.google.gson.JsonObject; 
import com.dragonfight.DragonfightMod;
import com.dragonfight.fight.DragonFightManagerCustom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.List;

public class ConfigurationCache
{
    public static class EntitySpawnData {
        public final EntityType<?> type;
        public final CompoundTag nbt;

        public EntitySpawnData(final EntityType<?> type, final CompoundTag nbt) {
            this.type = type;
            this.nbt = (nbt != null) ? nbt : new CompoundTag();
        }
    }
    
    public static void onConfigChanged() {
        DragonFightManagerCustom.spawnOnCrystalDeath = parseCrystalSpawnEntries(DragonfightMod.config.getCommonConfig().spawnoncrystaldestroy);
        DragonFightManagerCustom.spawnOnCrystalRespawn = parseCrystalSpawnEntries(DragonfightMod.config.getCommonConfig().spawnoncrystalrespawn);
        DragonFightManagerCustom.spawnOnDragonSitting = parseEntityTypesStringList(DragonfightMod.config.getCommonConfig().spawnwhilelanded);
    }
    public static record ConfiguredSpawnData(EntitySpawnData entityData, String locationPreference) {}

    private static ImmutableList<ConfiguredSpawnData> parseCrystalSpawnEntries(final List<JsonObject> data) {
        final ImmutableList.Builder<ConfiguredSpawnData> builder = ImmutableList.builder();
        for (final JsonObject configEntry : data) { // Loop through JSON objects

            String entryString = null;
            if (configEntry.has("entry") && configEntry.get("entry").isJsonPrimitive() && configEntry.get("entry").getAsJsonPrimitive().isString()) {
                entryString = configEntry.get("entry").getAsString();
            } else {
                DragonfightMod.LOGGER.warn("Skipping crystal spawn entry missing 'entry' string: {}", configEntry.toString());
                continue;
            }

            String locationPref = "ground_level"; // Default location
            if (configEntry.has("location") && configEntry.get("location").isJsonPrimitive() && configEntry.get("location").getAsJsonPrimitive().isString()) {
                String loc = configEntry.get("location").getAsString().toLowerCase();
                if (loc.equals("pillar_top") || loc.equals("ground_level")) {
                    locationPref = loc;
                } else {
                     DragonfightMod.LOGGER.warn("Invalid 'location' value '{}' in crystal spawn entry, defaulting to 'ground_level'. Entry: {}", configEntry.get("location").getAsString(), configEntry.toString());
                }
            }

            String trimmedEntry = entryString.trim();
            if (trimmedEntry.isEmpty()) continue;

            int nbtStart = trimmedEntry.indexOf("{");
            String typeString;
            String nbtString = null;

            if (nbtStart == -1) { typeString = trimmedEntry; }
            else {
                typeString = trimmedEntry.substring(0, nbtStart).trim();
                String potentialNbt = trimmedEntry.substring(nbtStart).trim();
                if (potentialNbt.startsWith("{") && potentialNbt.endsWith("}") && potentialNbt.length() >= 2) {
                     nbtString = potentialNbt;
                } else {
                     DragonfightMod.LOGGER.warn("Potential malformed NBT structure in config entry string: '{}'. Ignoring NBT part.", entryString);
                     nbtString = null;
                }
            }

            final ResourceLocation id = ResourceLocation.tryParse(typeString);
            if (id == null) { DragonfightMod.LOGGER.error("Could not parse resource location: '{}' (from entry: '{}')", typeString, entryString); continue; }

            final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) { DragonfightMod.LOGGER.error("Entity type not found: '{}' (from entry: '{}')", typeString, entryString); continue; }

            CompoundTag parsedNbt = null;
            if (nbtString != null && !nbtString.equals("{}")) {
                 try { parsedNbt = TagParser.parseTag(nbtString); }
                 catch (Exception e) { DragonfightMod.LOGGER.error("NBT data could not be parsed for type '{}'. NBT String: '{}'. Error: {}", typeString, nbtString, e.getMessage()); }
            }

            if (parsedNbt == null) parsedNbt = new CompoundTag();
            if (!DragonfightMod.config.getCommonConfig().allowMobLootDrops && !parsedNbt.contains("DeathLootTable", 8)) {
                parsedNbt.putString("DeathLootTable", "minecraft:empty");
            }

            EntitySpawnData entityData = new EntitySpawnData(type, parsedNbt);
            builder.add(new ConfiguredSpawnData(entityData, locationPref));
        }
        return builder.build();
    }

    private static ImmutableList<EntitySpawnData> parseEntityTypesStringList(final List<String> data)
    {
        final ImmutableList.Builder<EntitySpawnData> builder = ImmutableList.builder();
        
        for (final String entry : data) 
        {
            // --- fixed parsing logic here ---
            String trimmedEntry = entry.trim(); 
            if (trimmedEntry.isEmpty()) {
                continue; 
            }

            int nbtStart = trimmedEntry.indexOf("{");
            String typeString;
            String nbtString = null; 

            if (nbtStart == -1) {
                // No NBT specified, the whole string is the type ID
                typeString = trimmedEntry;
            } else {
                // NBT is present
                typeString = trimmedEntry.substring(0, nbtStart).trim(); // Get ID part, trim space
                String potentialNbt = trimmedEntry.substring(nbtStart).trim();
                if (potentialNbt.startsWith("{") && potentialNbt.endsWith("}") && potentialNbt.length() >= 2) { // Allow "{}"
                     nbtString = potentialNbt;
                } else {
                     
                     DragonfightMod.LOGGER.warn("Potential malformed NBT structure in config entry string: '{}'. Ignoring NBT part.", entry);
                     nbtString = null; 
                }
            }

            final ResourceLocation id = ResourceLocation.tryParse(typeString);
            if (id == null) {
                DragonfightMod.LOGGER.error("Config entry could not be parsed, not a valid resource location: '{}' (from entry: '{}')", typeString, entry);
                continue;
            }

            final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                
                DragonfightMod.LOGGER.error("Config entry could not be parsed, entity type not found: '{}' (from entry: '{}')", typeString, entry);
                continue;
            }

            CompoundTag parsedNbt = null;
            // Only parse if nbtString is not null AND not just empty braces "{}"
            if (nbtString != null && !nbtString.equals("{}")) {
                try {
                    parsedNbt = TagParser.parseTag(nbtString);
                } catch (Exception e) {
                    DragonfightMod.LOGGER.error("Config entry NBT data could not be parsed for type '{}'. NBT String: '{}'. Error: {}", typeString, nbtString, e.getMessage());
                    // Keep parsedNbt as null
                }
            }

            // --- Apply global loot table setting --- 
            if (parsedNbt == null) {
                 parsedNbt = new CompoundTag(); // Create if it was null
            }
            // Apply empty loot table if drops are disabled globally AND no specific loot table is already set in NBT
            if (!DragonfightMod.config.getCommonConfig().allowMobLootDrops && !parsedNbt.contains("DeathLootTable", 8)) {
                parsedNbt.putString("DeathLootTable", "minecraft:empty");
            }
            

            
            builder.add(new EntitySpawnData(type, parsedNbt));

        } 

        return builder.build();
    }
}