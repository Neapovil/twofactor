package com.github.neapovil.twofactor;

import java.security.GeneralSecurityException;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class TwoFactor extends JavaPlugin implements Listener
{
    private static TwoFactor instance;

    @Override
    public void onEnable()
    {
        instance = this;

        this.getServer().getPluginManager().registerEvents(this, this);

        this.registerCommand();
    }

    @Override
    public void onDisable()
    {
    }

    public static TwoFactor getInstance()
    {
        return instance;
    }

    @EventHandler
    private void playerLogin(PlayerLoginEvent event)
    {
        if (!event.getPlayer().hasPermission("twofactor.force"))
        {
            return;
        }

        if (!event.getPlayer().getPersistentDataContainer().has(Keys.AUTHENTICATED.getKey()))
        {
            event.getPlayer().getPersistentDataContainer().set(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER, 0);
            event.getPlayer().getPersistentDataContainer().set(Keys.ENABLED.getKey(), PersistentDataType.INTEGER, 0);
            event.getPlayer().getPersistentDataContainer().set(Keys.SECRET.getKey(), PersistentDataType.STRING, "");
        }

        if (event.getPlayer().getPersistentDataContainer().get(Keys.SECRET.getKey(), PersistentDataType.STRING) == "")
        {
            final String secret = TimeBasedOneTimePasswordUtil.generateBase32Secret();

            event.getPlayer().getPersistentDataContainer().set(Keys.SECRET.getKey(), PersistentDataType.STRING, secret);

            event.getPlayer().sendMessage(Component.text("Save the secret code below in your 2FA Authenticator APP and verify a code with /2fa verify <code>"));
            event.getPlayer().sendMessage(Component.text("Secret code: " + secret));
            return;
        }

        if (event.getPlayer().getPersistentDataContainer().get(Keys.ENABLED.getKey(), PersistentDataType.INTEGER) == 1)
        {
            event.getPlayer()
                    .sendMessage(Component.text("Please authenticate with the command /2fa verify <code>", NamedTextColor.RED, TextDecoration.BOLD));
            return;
        }
    }

    @EventHandler
    private void playerQuit(PlayerQuitEvent event)
    {
        if (event.getPlayer().getPersistentDataContainer().has(Keys.AUTHENTICATED.getKey()))
        {
            event.getPlayer().getPersistentDataContainer().set(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER, 0);
        }
    }

    @EventHandler
    private void playerMove(PlayerMoveEvent event)
    {
        if (!event.getPlayer().getPersistentDataContainer().has(Keys.AUTHENTICATED.getKey()))
        {
            return;
        }

        if (event.getPlayer().getPersistentDataContainer().get(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER) == 0)
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void playerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        if (!event.getPlayer().getPersistentDataContainer().has(Keys.AUTHENTICATED.getKey()))
        {
            return;
        }

        if (event.getPlayer().getPersistentDataContainer().get(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER) == 1)
        {
            return;
        }

        if (!event.getMessage().toLowerCase().startsWith("/2fa"))
        {
            event.setCancelled(true);
        }
    }

    private void registerCommand()
    {
        new CommandAPICommand("2fa")
                .withPermission("twofactor.command")
                .withArguments(new LiteralArgument("verify"))
                .withArguments(new IntegerArgument("code"))
                .executesPlayer((player, args) -> {
                    final int code = (int) args[0];

                    if (player.getPersistentDataContainer().get(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER) == 1)
                    {
                        throw CommandAPI.fail("You are already authenticated!");
                    }

                    final String secret = player.getPersistentDataContainer().get(Keys.SECRET.getKey(), PersistentDataType.STRING);

                    try
                    {
                        final boolean valid = TimeBasedOneTimePasswordUtil.validateCurrentNumber(secret, code,
                                TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS);

                        if (!valid)
                        {
                            throw CommandAPI.fail("The code is invalid!");
                        }

                        if (player.getPersistentDataContainer().get(Keys.ENABLED.getKey(), PersistentDataType.INTEGER) == 0)
                        {
                            player.getPersistentDataContainer().set(Keys.ENABLED.getKey(), PersistentDataType.INTEGER, 1);
                            player.getPersistentDataContainer().set(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER, 1);

                            player.sendMessage(Component.text("2FA is now enabled!", NamedTextColor.GREEN, TextDecoration.BOLD));
                            return;
                        }

                        player.getPersistentDataContainer().set(Keys.AUTHENTICATED.getKey(), PersistentDataType.INTEGER, 1);
                        player.sendMessage(Component.text("You are authenticated!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    }
                    catch (GeneralSecurityException e)
                    {
                        this.getLogger().severe("Unable to verify 2FA code for player " + player.getName() + ". Error message: " + e.getMessage());
                    }
                })
                .register();
    }

    enum Keys
    {
        AUTHENTICATED(new NamespacedKey(TwoFactor.getInstance(), "authenticated")),
        ENABLED(new NamespacedKey(TwoFactor.getInstance(), "enabled")),
        SECRET(new NamespacedKey(TwoFactor.getInstance(), "secret"));

        private final NamespacedKey key;

        Keys(NamespacedKey key)
        {
            this.key = key;
        }

        public NamespacedKey getKey()
        {
            return this.key;
        }
    }
}
