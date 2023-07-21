package com.bgsoftware.superiorskyblock.listener;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.core.Singleton;
import com.bgsoftware.superiorskyblock.core.key.KeyImpl;
import com.bgsoftware.superiorskyblock.module.BuiltinModules;
import com.bgsoftware.superiorskyblock.module.upgrades.type.UpgradeTypeEntityLimits;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public class EntityTrackingListener implements Listener {

    private final SuperiorSkyblockPlugin plugin;
    private final Singleton<BlockChangesListener> blockChangesListener;

    public EntityTrackingListener(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        this.blockChangesListener = plugin.getListener(BlockChangesListener.class);
        this.registerDeathListener();
    }

    /* ENTITY SPAWNING */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onEntitySpawn(CreatureSpawnEvent e) {
        onEntitySpawn(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onHangingPlace(HangingPlaceEvent e) {
        onEntitySpawn(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onVehicleSpawn(VehicleCreateEvent e) {
        onEntitySpawn(e.getVehicle());
    }

    public void onEntitySpawn(Entity entity) {
        if (!BuiltinModules.UPGRADES.isUpgradeTypeEnabled(UpgradeTypeEntityLimits.class) ||
                BukkitEntities.canBypassEntityLimit(entity) || !BukkitEntities.canHaveLimit(entity.getType()))
            return;

        Island island = plugin.getGrid().getIslandAt(entity.getLocation());

        if (island == null)
            return;

        island.getEntitiesTracker().trackEntity(KeyImpl.of(entity), 1);
    }

    /* ENTITY DESPAWNING */

    public void onEntityDespawn(Entity entity) {
        if (!BuiltinModules.UPGRADES.isUpgradeTypeEnabled(UpgradeTypeEntityLimits.class) ||
                BukkitEntities.canBypassEntityLimit(entity) || !BukkitEntities.canHaveLimit(entity.getType()))
            return;

        Island island = plugin.getGrid().getIslandAt(entity.getLocation());

        if (island == null)
            return;

        island.getEntitiesTracker().untrackEntity(KeyImpl.of(entity), 1);

        if (!(entity instanceof Minecart))
            return;

        if (entity.hasMetadata("SSB-VehicleDestory")) {
            entity.removeMetadata("SSB-VehicleDestory", plugin);
            return;
        }

        // Vehicle was not registered by VehicleDestroyEvent; We want to register its block break
        Key blockKey = plugin.getNMSAlgorithms().getMinecartBlock((Minecart) entity);
        this.blockChangesListener.get().onBlockBreak(blockKey, entity.getLocation(), 1,
                BlockChangesListener.Flag.DIRTY_CHUNK, BlockChangesListener.Flag.SAVE_BLOCK_COUNT);
    }

    /* INTERNAL */

    private void registerDeathListener() {
        Listener deathListener;

        try {
            Class.forName("com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent");
            deathListener = new PaperDeathListener();
        } catch (ClassNotFoundException error) {
            deathListener = new SpigotDeathListener();
        }

        plugin.getServer().getPluginManager().registerEvents(deathListener, plugin);
    }

    private class PaperDeathListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        private void onEntityRemove(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent e) {
            onEntityDespawn(e.getEntity());
        }

    }

    private class SpigotDeathListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        private void onEntityDeath(EntityDeathEvent e) {
            onEntityDespawn(e.getEntity());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        private void onVehicleDestroy(VehicleDestroyEvent e) {
            onEntityDespawn(e.getVehicle());
        }

    }

}
