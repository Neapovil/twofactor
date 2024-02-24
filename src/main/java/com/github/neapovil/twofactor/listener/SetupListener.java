package com.github.neapovil.twofactor.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.github.neapovil.twofactor.TwoFactor;

import dev.jorel.commandapi.CommandAPI;

public final class SetupListener implements Listener
{
    private final TwoFactor plugin = TwoFactor.instance();

    @EventHandler
    private void playerQuit(PlayerQuitEvent event)
    {
        plugin.setups.remove(event.getPlayer().getUniqueId());

        event.getPlayer().getInventory().all(Material.FILLED_MAP).forEach((index, itemStack) -> {
            if (itemStack.getItemMeta().getPersistentDataContainer().has(plugin.mapKey))
            {
                event.getPlayer().getInventory().setItem(index, null);
            }
        });
    }

    @EventHandler
    private void playerDropItem(PlayerDropItemEvent event)
    {
        if (event.getItemDrop().getItemStack().getItemMeta().getPersistentDataContainer().has(plugin.mapKey))
        {
            event.getItemDrop().remove();
            plugin.setups.remove(event.getPlayer().getUniqueId());
            CommandAPI.updateRequirements(event.getPlayer());
        }
    }

    @EventHandler
    private void playerDeath(PlayerDeathEvent event)
    {
        plugin.setups.remove(event.getPlayer().getUniqueId());
        event.getDrops().removeIf(i -> i.getItemMeta().getPersistentDataContainer().has(plugin.mapKey));
        CommandAPI.updateRequirements(event.getPlayer());
    }
}
