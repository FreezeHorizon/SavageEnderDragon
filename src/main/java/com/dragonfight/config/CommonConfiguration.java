package com.dragonfight.config;


import com.cupboard.config.ICommonConfig;
import com.dragonfight.DragonfightMod; 
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class CommonConfiguration implements ICommonConfig
{
    public int          maxMeleeAddsLimit          = 30;
    public boolean      allowMobLootDrops          = false;
    public int          dragonHpOverride           = -1;
    public int          dragonBaseHp               = 200;
    public int          antiFlightIntervalTicks    = 800;
    public int          antiFlightEffectDurationTicks = 300;
    public int          dragonDifficulty           = 2;
    public boolean      printDragonPhases          = false;
    public boolean      disableDragonAreaSpawns    = true;
    public List<JsonObject> spawnoncrystaldestroy = new ArrayList<>();
    public List<JsonObject> spawnoncrystalrespawn = new ArrayList<>();
    public List<String> spawnwhilelanded           = Lists.newArrayList("minecraft:enderman");
    public double       crystalRespawnTimeModifier = 1.0;
    public double       lightningExplosionDensity  = 1.0;
    public boolean      disableLightning           = false;
    public boolean      antiflightAbility          = true;
    public double       dragonHealthModifier       = 2.0;
    public double       dragonDamageModifier       = 1.0;
    public double       mobSpawnAmountModifier     = 1.0;
    public double       dragonXPModifier           = 1.0;

    public CommonConfiguration()
    {
        // Defaulting to no loot unless allowMobLootDrops is true

        JsonObject phantomEntry = new JsonObject();
        phantomEntry.addProperty("entry", "minecraft:phantom{DeathLootTable:'minecraft:empty'}");
        phantomEntry.addProperty("location", "pillar_top");
        this.spawnoncrystaldestroy.add(phantomEntry);

        JsonObject evokerEntry = new JsonObject();
        phantomEntry.addProperty("entry", "minecraft:evoker{DeathLootTable:'minecraft:empty'}");
        phantomEntry.addProperty("location", "ground_level");
        this.spawnoncrystaldestroy.add(evokerEntry);

        JsonObject blazeEntry = new JsonObject();
        blazeEntry.addProperty("entry", "minecraft:blaze{DeathLootTable:'minecraft:empty'}");
        blazeEntry.addProperty("location", "pillar_top");
        this.spawnoncrystalrespawn.add(blazeEntry);

        JsonObject ravegerEntry = new JsonObject();
        blazeEntry.addProperty("entry", "minecraft:raveger{DeathLootTable:'minecraft:empty'}");
        blazeEntry.addProperty("location", "ground_level");
        this.spawnoncrystalrespawn.add(ravegerEntry);
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry = new JsonObject();
        entry.addProperty("desc:", "Sets the dragon difficulty modifier, the higher the more difficult the dragon gets."
                                     + "Scales up mob spawn amount, dragon damage and health aswell as crystal respawn intervals. Note that the difficulty already scales on the playercount involved in the fight, this is a static bonus ontop."
                                     + "default:2, vanilla:0");
        entry.addProperty("dragonDifficulty", dragonDifficulty);
        root.add("dragonDifficulty", entry);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Modifies crystal respawn time, 0.5 = spawns twice as fast, 2 = twice as slow. default:1.0");
        entry8.addProperty("crystalRespawnTimeModifier", crystalRespawnTimeModifier);
        root.add("crystalRespawnTimeModifier", entry8);

        final JsonObject entry9 = new JsonObject();
        entry9.addProperty("desc:", "Modifies lightning and explosion density, 0.5 = half as many, 2 = twice as many. default:1.0");
        entry9.addProperty("lightningExplosionDensity", lightningExplosionDensity);
        root.add("lightningExplosionDensity", entry9);

        final JsonObject entry10 = new JsonObject();
        entry10.addProperty("desc:", "Disables lightning spawns: default:false");
        entry10.addProperty("disableLightning", disableLightning);
        root.add("disableLightning", entry10);

        final JsonObject entry15 = new JsonObject();
        entry15.addProperty("desc:", "Enables anti-flight ability: default:true");
        entry15.addProperty("antiflightAbility", antiflightAbility);
        root.add("antiflightAbility", entry15);

        final JsonObject entryAntiFlightInt = new JsonObject();
        entryAntiFlightInt.addProperty("desc:", "Interval in ticks between global anti-flight (Levitation 200/negative levitation) events. 20 ticks = 1 second. default:800 (40 seconds)");
        entryAntiFlightInt.addProperty("antiFlightIntervalTicks", antiFlightIntervalTicks); // Syntax: Integer value (e.g., 1200)
        root.add("antiFlightIntervalTicks", entryAntiFlightInt);

        final JsonObject entryAntiFlightDur = new JsonObject();
        entryAntiFlightDur.addProperty("desc:", "Duration in ticks for the anti-flight (Levitation 250) effect. 20 ticks = 1 second. default:300 (15 seconds)");
        entryAntiFlightDur.addProperty("antiFlightEffectDurationTicks", antiFlightEffectDurationTicks);
        root.add("antiFlightEffectDurationTicks", entryAntiFlightDur);

        final JsonObject entryHpOverride = new JsonObject();
        entryHpOverride.addProperty("desc:", "Set a specific Max HP value for the Ender Dragon, ignoring scaling and modifiers. Set to -1 (default) to disable override and use scaling. (interger)");
        entryHpOverride.addProperty("dragonHpOverride", dragonHpOverride); 
        root.add("dragonHpOverride", entryHpOverride);

        final JsonObject entryBaseHp = new JsonObject();
        entryBaseHp.addProperty("desc:", "Base HP used for the dragon *if* dragonHpOverride is disabled (-1). Vanilla is 200. Scaling/modifiers apply to this. default:200 (integer)");
        entryBaseHp.addProperty("dragonBaseHp", dragonBaseHp); // Syntax: Integer value (e.g., 200)
        root.add("dragonBaseHp", entryBaseHp);

        final JsonObject entry16 = new JsonObject();
        entry16.addProperty("desc:", "Sets the dragon health modifier: default:2.0 Vanilla:1.0");
        entry16.addProperty("dragonHealthModifier", dragonHealthModifier);
        root.add("dragonHealthModifier", entry16);

        final JsonObject entry17 = new JsonObject();
        entry17.addProperty("desc:", "Sets the dragon damage modifier: default:1.0");
        entry17.addProperty("dragonDamageModifier", dragonDamageModifier);
        root.add("dragonDamageModifier", entry17);

        final JsonObject entryMaxAdds = new JsonObject();
        entryMaxAdds.addProperty("desc:", "Absolute maximum limit for concurrently active 'melee adds' (mobs spawned while dragon flies). Set to -1 to disable this specific limit check entirely (spawns potentially unlimited, capped only by performance/spawn timer). Default: 30");
        entryMaxAdds.addProperty("maxMeleeAddsLimit", maxMeleeAddsLimit);
        root.add("maxMeleeAddsLimit", entryMaxAdds);

        final JsonObject entry18 = new JsonObject();
        entry18.addProperty("desc:", "Multiplier for mob spawns. Affects:" +
                                     "\n1. Quantity of mobs spawned by crystal events (destroy/respawn)." +
                                     "\n2. Frequency of 'melee add' spawns (higher value = shorter interval between spawns)." +
                                     " default:1.0");
        entry18.addProperty("mobSpawnAmountModifier", mobSpawnAmountModifier);
        root.add("mobSpawnAmountModifier", entry18);

        final JsonObject entryLoot = new JsonObject();
        entryLoot.addProperty("desc:", "If true, mobs spawned by this mod will drop loot/XP as normal. If false (default), they drop nothing unless specific NBT overrides it.");
        entryLoot.addProperty("allowMobLootDrops", allowMobLootDrops);
        root.add("allowMobLootDrops", entryLoot);

        final JsonObject entry20 = new JsonObject();
        entry20.addProperty("desc:", "Sets the XP drop modifier: default:1.0");
        entry20.addProperty("dragonXPModifier", dragonXPModifier);
        root.add("dragonXPModifier", entry20);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Prints the dragon phase in chat if enabled: default:false");
        entry2.addProperty("printDragonPhases", printDragonPhases);
        root.add("printDragonPhases", entry2);

        final JsonObject entry3 = new JsonObject();
        entry3.addProperty("desc:", "Disables mob spawning on the Dragon island during the fight: default:true");
        entry3.addProperty("disableDragonAreaSpawns", disableDragonAreaSpawns);
        root.add("disableDragonAreaSpawns", entry3);

        root.addProperty("descSpawnEntries",
        "Below are configuration options for entity spawning." +
        " For 'spawnoncrystaldestroy' and 'spawnoncrystalrespawn', each entry in the list MUST be an object: { \"entry\": \"id{NBT}\", \"location\": \"pillar_top\" OR \"ground_level\" }." +
        " The 'entry' string follows the format 'modid:entity_id{NBTData}' (NO space before {, use single quotes '' for strings inside NBT)." +
        " For 'spawnwhilelanded', entries are just strings: \"modid:entity_id{NBTData}\"." +
        " Example crystal entry: { \"entry\": \"minecraft:zombie{Health:50f,DeathLootTable:'minecraft:empty'}\", \"location\": \"ground_level\" }");

        final JsonObject entry4 = new JsonObject();
        entry4.addProperty("desc:",
        "List of mobs to spawn when a crystal is destroyed by a ranged player."+
        " Define 'entry' (mob ID + NBT string) and 'location' ('pillar_top' or 'ground_level').");
        final JsonArray list4 = new JsonArray();
        for (final JsonObject obj : spawnoncrystaldestroy)
        {
            list4.add(obj.deepCopy());
        }
        entry4.add("spawnoncrystaldestroy", list4);
        root.add("spawnoncrystaldestroy", entry4);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:",
        "List of mobs to spawn when a crystal respawns."+
        " Define 'entry' (mob ID + NBT string) and 'location' ('pillar_top' or 'ground_level').");
        final JsonArray list5 = new JsonArray();
        for (final JsonObject obj : spawnoncrystalrespawn)
        {
            list5.add(obj.deepCopy());
        }
        entry5.add("spawnoncrystalrespawn", list5);
        root.add("spawnoncrystalrespawn", entry5);

        final JsonObject entry6 = new JsonObject();
        entry6.addProperty("desc:",
        "List of mobs ('melee adds') to potentially spawn periodically while the dragon is flying (Format: \"id{NBT}\")."+
        " One mob is chosen randomly from this list each time the spawn timer triggers (up to the limit).");
        final JsonArray list6 = new JsonArray();
        for (final String name : spawnwhilelanded)
        {
            list6.add(name);
        }
        entry6.add("spawnwhilelanded", list6);
        root.add("spawnwhilelanded", entry6);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        try {
            if (data.has("dragonDifficulty") && data.get("dragonDifficulty").isJsonObject())
                dragonDifficulty = data.getAsJsonObject("dragonDifficulty").get("dragonDifficulty").getAsInt();
            else dragonDifficulty = 2;
            if (data.has("maxMeleeAddsLimit") && data.get("maxMeleeAddsLimit").isJsonObject())
                maxMeleeAddsLimit = data.getAsJsonObject("maxMeleeAddsLimit").get("maxMeleeAddsLimit").getAsInt();
            else maxMeleeAddsLimit = 30;
            if (data.has("allowMobLootDrops") && data.get("allowMobLootDrops").isJsonObject())
                allowMobLootDrops = data.getAsJsonObject("allowMobLootDrops").get("allowMobLootDrops").getAsBoolean();
            else allowMobLootDrops = false;
            if (data.has("dragonHpOverride") && data.get("dragonHpOverride").isJsonObject())
                dragonHpOverride = data.getAsJsonObject("dragonHpOverride").get("dragonHpOverride").getAsInt();
            else dragonHpOverride = -1;
            if (data.has("dragonBaseHp") && data.get("dragonBaseHp").isJsonObject())
                dragonBaseHp = data.getAsJsonObject("dragonBaseHp").get("dragonBaseHp").getAsInt();
            else dragonBaseHp = 200;
            if (data.has("antiFlightIntervalTicks") && data.get("antiFlightIntervalTicks").isJsonObject())
                antiFlightIntervalTicks = data.getAsJsonObject("antiFlightIntervalTicks").get("antiFlightIntervalTicks").getAsInt();
            else antiFlightIntervalTicks = 800;
            if (data.has("antiFlightEffectDurationTicks") && data.get("antiFlightEffectDurationTicks").isJsonObject())
                antiFlightEffectDurationTicks = data.getAsJsonObject("antiFlightEffectDurationTicks").get("antiFlightEffectDurationTicks").getAsInt();
            else antiFlightEffectDurationTicks = 240;
            if (data.has("printDragonPhases") && data.get("printDragonPhases").isJsonObject())
                printDragonPhases = data.getAsJsonObject("printDragonPhases").get("printDragonPhases").getAsBoolean();
            else printDragonPhases = false;

        } catch (Exception e) {
            DragonfightMod.LOGGER.error("Failed to parse primary config options, using defaults.", e);
        }

        spawnoncrystaldestroy = new ArrayList<>();
        try {
            if (data.has("spawnoncrystaldestroy") && data.get("spawnoncrystaldestroy").isJsonObject()) {
                JsonObject container = data.getAsJsonObject("spawnoncrystaldestroy");
                if (container.has("spawnoncrystaldestroy") && container.get("spawnoncrystaldestroy").isJsonArray()) {
                    for (final JsonElement element : container.getAsJsonArray("spawnoncrystaldestroy")) {
                        if (element.isJsonObject()) { 
                            spawnoncrystaldestroy.add(element.getAsJsonObject());
                        } else {
                            DragonfightMod.LOGGER.warn("Skipping invalid non-object entry in spawnoncrystaldestroy list: {}", element.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            DragonfightMod.LOGGER.error("Failed to parse 'spawnoncrystaldestroy' list, list will be empty.", e);
            spawnoncrystaldestroy = new ArrayList<>();
        }


        spawnoncrystalrespawn = new ArrayList<>();
        try {
            if (data.has("spawnoncrystalrespawn") && data.get("spawnoncrystalrespawn").isJsonObject()) {
                 JsonObject container = data.getAsJsonObject("spawnoncrystalrespawn");
                 if (container.has("spawnoncrystalrespawn") && container.get("spawnoncrystalrespawn").isJsonArray()) {
                     for (final JsonElement element : container.getAsJsonArray("spawnoncrystalrespawn")) {
                         if (element.isJsonObject()) {
                             spawnoncrystalrespawn.add(element.getAsJsonObject());
                         } else {
                             DragonfightMod.LOGGER.warn("Skipping invalid non-object entry in spawnoncrystalrespawn list: {}", element.toString());
                         }
                     }
                 }
            }
        } catch (Exception e) {
             DragonfightMod.LOGGER.error("Failed to parse 'spawnoncrystalrespawn' list, list will be empty.", e);
             spawnoncrystalrespawn = new ArrayList<>();
        }


        spawnwhilelanded = new ArrayList<>();
        try {
             if (data.has("spawnwhilelanded") && data.get("spawnwhilelanded").isJsonObject()) {
                 JsonObject container = data.getAsJsonObject("spawnwhilelanded");
                 if (container.has("spawnwhilelanded") && container.get("spawnwhilelanded").isJsonArray()) {
                     for (final JsonElement element : container.getAsJsonArray("spawnwhilelanded"))
                     {
                         if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                             spawnwhilelanded.add(element.getAsString());
                         } else {
                              DragonfightMod.LOGGER.warn("Skipping invalid non-string entry in spawnwhilelanded list: {}", element.toString());
                         }
                     }
                 }
             }
        } catch (Exception e) {
             DragonfightMod.LOGGER.error("Failed to parse 'spawnwhilelanded' list, list will be empty.", e);
             spawnwhilelanded = new ArrayList<>();
        }


        ConfigurationCache.onConfigChanged();
    }
}