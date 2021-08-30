package io.github.lukeeey.deathview.bukkit;

import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DeathView extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("teleport-to-spawn") && getConfig().getBoolean("teleport-to-coords")) {
            throw new RuntimeException("Teleporting to spawn enabled in config, but teleporting to specific coordinates is also enabled.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof Player && ((Player) entity).getHealth() - event.getDamage() <= 0) {
            Player player = (Player) entity;

            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE ||
                    !player.hasPermission("deathview.spectator")) {
                return;
            }

            event.setCancelled(true);
            player.setGameMode(GameMode.SPECTATOR);

            sendMessageFromConfig("death-message.died.player", event, player);
            sendMessageFromConfig("death-message.died.all", event);

            getServer().getScheduler().runTaskLater(this, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(20);

                World world = findWorld();

                if (getConfig().getBoolean("teleport-to-spawn")) {
                    player.teleport(world.getSpawnLocation());
                }
                if (getConfig().getBoolean("teleport-to-coords")) {
                    player.teleport(new Location(world, getConfig().getInt("teleport-to-coords.x"),
                            getConfig().getInt("teleport-to-coords.y"), getConfig().getInt("teleport-to-coords.z"), 0, 0));
                }

                Boolean keepInventory = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
                boolean clearInventory = getConfig().getBoolean("clear-inventory-on-death");

                if (keepInventory != null && !keepInventory && !clearInventory) {
                    for (ItemStack stack : player.getInventory().getContents()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), stack);
                    }
                }

                if (clearInventory) {
                    player.getInventory().clear();
                }
            }, getConfig().getInt("time") * 20);
        }
    }

    private String placeholder(EntityDamageEvent event, String text) {
        return text
                .replace("{victim}", event.getEntity().getName())
                .replace("{world}", event.getEntity().getWorld().getName());
    }

    private World findWorld() {
        World defaultLevel = getServer().getWorlds().get(0);
        String configWorld = getConfig().getString("teleport-to-world", "default");

        if (configWorld.equalsIgnoreCase("default")) {
            return defaultLevel;
        }

        World level = getServer().getWorld(configWorld);
        if (level == null) {
            throw new RuntimeException("Invalid world name specified in config: " + configWorld);
        }
        return level;
    }

    private void sendMessageFromConfig(String type, EntityDamageEvent event) {
        sendMessageFromConfig(type, event, null);
    }

    private void sendMessageFromConfig(String type, EntityDamageEvent event, Player player) {
        String chatMessage = getConfig().getString(type + ".chat");
        String titleMessage = getConfig().getString(type + ".title");
        String subtitleMessage = getConfig().getString(type + ".subtitle");

        List<Player> players = (player != null ? Collections.singletonList(player) : new ArrayList<>(getServer().getOnlinePlayers()));

        if (!chatMessage.isEmpty()) {
            players.forEach(p -> p.sendMessage(placeholder(event, chatMessage)));
        }
        if (!titleMessage.isEmpty() || !subtitleMessage.isEmpty()) {
            players.forEach(p -> p.sendTitle(placeholder(event, titleMessage), placeholder(event, subtitleMessage)));
        }
    }
}
