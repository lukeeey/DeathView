package io.github.lukeeey.deathview;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;

public class DeathView extends PluginBase implements Listener {

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

        if (entity instanceof Player && entity.getHealth() - event.getDamage() <= 0) {
            Player player = (Player) entity;

            if (player.getGamemode() == 1 || !player.hasPermission("deathview.spectator")) {
                return;
            }

            event.setCancelled();
            player.setGamemode(3);

            String playerMessage = getConfig().getString("death-message.died.player");
            String allMessage = getConfig().getString("death-message.died.all");

            if (!playerMessage.isEmpty()) {
                player.sendMessage(placeholder(event, playerMessage));
            }

            if (!allMessage.isEmpty()) {
                getServer().broadcastMessage(placeholder(event, allMessage));
            }

            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                player.setGamemode(0);
                player.heal(20);

                Level world = findWorld();

                if (getConfig().getBoolean("teleport-to-spawn")) {
                    player.teleport(world.getSafeSpawn());
                }
                if (getConfig().getBoolean("teleport-to-coords")) {
                    player.teleport(new Location(getConfig().getInt("teleport-to-coords.x"),
                            getConfig().getInt("teleport-to-coords.y"), getConfig().getInt("teleport-to-coords.z"), 0, 0, world));
                }
            }, getConfig().getInt("time") * 20);
        }
    }

    private String placeholder(EntityDamageEvent event, String text) {
        return text
                .replace("{victim}", event.getEntity().getName())
                .replace("{world}", event.getEntity().getLevel().getName());
    }

    private Level findWorld() {
        Level defaultLevel = getServer().getDefaultLevel();
        String configWorld = getConfig().getString("teleport-to-world", "default");

        if (configWorld.equalsIgnoreCase("default")) {
            return defaultLevel;
        }

        Level level = getServer().getLevelByName(configWorld);
        if (level == null) {
            throw new RuntimeException("Invalid world name specified in config: " + configWorld);
        }
        return level;
    }
}
