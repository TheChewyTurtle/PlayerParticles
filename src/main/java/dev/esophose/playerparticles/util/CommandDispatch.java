package dev.esophose.playerparticles.util;

import dev.esophose.playerparticles.particles.PPlayer;
import dev.esophose.playerparticles.manager.DataManager;
import dev.rosewood.rosegarden.scheduler.RoseScheduler;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Routes command work to its owning context after asynchronous data access. */
public final class CommandDispatch {
    private CommandDispatch() { }

    public static void loadPlayer(RoseScheduler scheduler, DataManager dataManager, Player player,
                                  Consumer<PPlayer> command) {
        UUID playerId = player.getUniqueId();
        // DataManager may perform a cold lookup inline on a tick thread. Keep lookup off it;
        // a cache hit is also safe here because its callback always returns to the entity below.
        scheduler.runTaskAsync(() -> dataManager.getPPlayer(playerId,
                data -> afterPlayerLoad(scheduler, player, data, command)));
    }

    public static void atSender(RoseScheduler scheduler, CommandSender sender, Runnable command) {
        if (sender instanceof Entity) {
            scheduler.runTaskAtEntity((Entity) sender, () -> {
                if (!(sender instanceof Player) || ((Player) sender).isOnline())
                    command.run();
            });
        } else if (sender instanceof BlockCommandSender) {
            scheduler.runTaskAtLocation(((BlockCommandSender) sender).getBlock().getLocation(), command);
        } else {
            // A console/RCON sender has no entity or region; use the global scheduler.
            scheduler.runTask(command);
        }
    }

    public static void afterPlayerLoad(RoseScheduler scheduler, Player player, PPlayer data,
                                       Consumer<PPlayer> command) {
        scheduler.runTaskAtEntity(player, () -> {
            if (player.isOnline()) command.accept(data);
        });
    }
}
