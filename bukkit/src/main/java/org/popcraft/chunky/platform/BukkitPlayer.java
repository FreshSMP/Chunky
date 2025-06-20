package org.popcraft.chunky.platform;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.ChunkyBukkit;
import org.popcraft.chunky.platform.util.Location;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.popcraft.chunky.util.Translator.translateKey;

public class BukkitPlayer extends BukkitSender implements Player {
    private static final boolean ACTION_BAR_SUPPORTED;
    private final JavaPlugin plugin = JavaPlugin.getPlugin(ChunkyBukkit.class);

    static {
        boolean barSupported;
        try {
            org.bukkit.entity.Player.class.getMethod("spigot");
            barSupported = true;
        } catch (NoSuchMethodException e) {
            barSupported = false;
        }
        ACTION_BAR_SUPPORTED = barSupported;
    }

    final org.bukkit.entity.Player player;

    public BukkitPlayer(final org.bukkit.entity.Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public World getWorld() {
        return new BukkitWorld(player.getWorld());
    }

    @Override
    public Location getLocation() {
        final org.bukkit.Location location = player.getLocation();
        return new Location(getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public void teleport(final Location location) {
        final org.bukkit.World world = Bukkit.getWorld(location.getWorld().getName());
        final org.bukkit.Location loc = new org.bukkit.Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        final Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            teleportAsync(player, loc);
        } else if (Paper.isPaper() && player.getWorld().equals(loc.getWorld())) {
            Paper.teleportAsyncWithPassengers(vehicle, loc);
        } else if (Folia.isFolia() && !Folia.isTickThread(player.getLocation())) {
            Folia.schedule(plugin, player, () -> teleport(location), 1);
        } else {
            final List<Entity> passengers = vehicle.getPassengers();
            if (Folia.isFolia()) {
                Folia.schedule(plugin, vehicle, vehicle::eject, 1);
            } else {
                vehicle.eject();
            }
            teleportAsync(player, loc).thenAcceptAsync(ignored -> {
                teleportAsync(vehicle, loc);
                for (final Entity passenger : passengers) {
                    teleportAsync(passenger, loc);
                    if (passenger instanceof final org.bukkit.entity.Player playerPassenger) {
                        playerPassenger.hideEntity(plugin, vehicle);
                        playerPassenger.showEntity(plugin, vehicle);
                    }
                    if (Folia.isFolia()) {
                        Folia.schedule(plugin, vehicle, () -> vehicle.addPassenger(passenger), 1);
                    } else {
                        vehicle.addPassenger(passenger);
                    }
                }
            }, command -> {
                if (Folia.isFolia()) {
                    Folia.schedule(plugin, player, command, 1);
                } else {
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, command, 1);
                }
            });
        }
    }

    private CompletableFuture<Boolean> teleportAsync(final Entity entity, final org.bukkit.Location location) {
        if (Paper.isPaper()) {
            return Paper.teleportAsync(entity, location);
        } else {
            return CompletableFuture.completedFuture(entity.teleport(location));
        }
    }

    @Override
    public void sendActionBar(final String key) {
        if (ACTION_BAR_SUPPORTED) {
            final String message = formatColored(translateKey(key, false));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } else {
            this.sendMessage(key);
        }
    }
}
