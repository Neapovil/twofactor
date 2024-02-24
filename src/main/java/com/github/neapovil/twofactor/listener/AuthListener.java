package com.github.neapovil.twofactor.listener;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.github.neapovil.twofactor.TwoFactor;
import com.github.neapovil.twofactor.gson.TwoFactorGson;
import com.github.neapovil.twofactor.object.TwoFactorLogin;
import com.github.neapovil.twofactor.persistence.TwoFactorDataType;

import io.papermc.paper.event.player.AsyncChatEvent;

public final class AuthListener implements Listener
{
    private final TwoFactor plugin = TwoFactor.instance();

    @EventHandler
    private void playerJoin(PlayerJoinEvent event)
    {
        if (event.getPlayer().getPersistentDataContainer().has(plugin.twoFactorGsonKey))
        {
            plugin.auths.put(event.getPlayer().getUniqueId(), new TwoFactorLogin());
            event.getPlayer().sendMessage("You have two factor enabled");
            event.getPlayer().sendMessage("/2fa authenticate <code>");
        }
    }

    @EventHandler
    private void playerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        if (plugin.authenticated(event.getPlayer()))
        {
            return;
        }

        if (!event.getMessage().toLowerCase().startsWith("/2fa"))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void playerMove(PlayerMoveEvent event)
    {
        if (!plugin.authenticated(event.getPlayer()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void asyncChat(AsyncChatEvent event)
    {
        if (!plugin.authenticated(event.getPlayer()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void playerDropItem(PlayerDropItemEvent event)
    {
        if (!plugin.authenticated(event.getPlayer()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void playerQuit(PlayerQuitEvent event)
    {
        final TwoFactorGson twofactorgson = event.getPlayer().getPersistentDataContainer().get(plugin.twoFactorGsonKey, new TwoFactorDataType());

        if (twofactorgson != null)
        {
            plugin.auths.keySet().removeIf(i -> i.equals(event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler
    private void playerInteract(PlayerInteractEvent event)
    {
        if (!plugin.authenticated(event.getPlayer()))
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void serverTickStart(ServerTickStartEvent event)
    {
        if (event.getTickNumber() % 20 != 0)
        {
            return;
        }

        final Map<UUID, TwoFactorLogin> auths = new HashMap<>(plugin.auths);

        for (Map.Entry<UUID, TwoFactorLogin> i : auths.entrySet())
        {
            final Player player = plugin.getServer().getPlayer(i.getKey());

            if (player == null)
            {
                continue;
            }

            if (!player.isOnline())
            {
                continue;
            }

            if (Duration.between(Instant.ofEpochMilli(player.getLastLogin()), Instant.now()).toMinutes() > i.getValue().validForMinutes)
            {
                final String message = "<red>You've been kicked!</red>\n\nYou took too long to authenticate";
                player.kick(plugin.miniMessage.deserialize(message), PlayerKickEvent.Cause.PLUGIN);
            }
        }
    }
}
