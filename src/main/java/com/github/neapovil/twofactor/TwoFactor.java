package com.github.neapovil.twofactor;

import java.awt.image.BufferedImage;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.neapovil.twofactor.gson.TwoFactorGson;
import com.github.neapovil.twofactor.listener.AuthListener;
import com.github.neapovil.twofactor.listener.SetupListener;
import com.github.neapovil.twofactor.map.QrMap;
import com.github.neapovil.twofactor.object.TwoFactorLogin;
import com.github.neapovil.twofactor.object.TwoFactorSetup;
import com.github.neapovil.twofactor.persistence.TwoFactorDataType;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import io.nayuki.qrcodegen.QrCode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TwoFactor extends JavaPlugin implements Listener
{
    private static TwoFactor instance;
    public final NamespacedKey twoFactorGsonKey = new NamespacedKey(this, "twofactorgson");
    public final NamespacedKey mapKey = new NamespacedKey(this, "map");
    public final Map<UUID, TwoFactorSetup> setups = new HashMap<>();
    public final Map<UUID, TwoFactorLogin> auths = new HashMap<>();
    public final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable()
    {
        instance = this;

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new SetupListener(), this);
        this.getServer().getPluginManager().registerEvents(new AuthListener(), this);

        new CommandAPICommand("2fa")
                .withPermission("twofactor.command")
                .withArguments(new LiteralArgument("setup").withRequirement(sender -> {
                    return !this.hasTwoFactor((Player) sender);
                }))
                .withArguments(new LiteralArgument("init").withRequirement(sender -> {
                    return !this.setups.containsKey(((Player) sender).getUniqueId());
                }))
                .executesPlayer((player, args) -> {
                    final TwoFactorSetup twofactorsetup = this.map(player);
                    this.setups.put(player.getUniqueId(), twofactorsetup);
                    player.sendMessage("Scan the qrcode, then /2fa setup validate <code>");
                    CommandAPI.updateRequirements(player);
                })
                .register();

        new CommandAPICommand("2fa")
                .withPermission("twofactor.command")
                .withArguments(new LiteralArgument("setup").withRequirement(sender -> {
                    return !this.hasTwoFactor((Player) sender);
                }))
                .withArguments(new LiteralArgument("validate").withRequirement(sender -> {
                    return this.setups.containsKey(((Player) sender).getUniqueId());
                }))
                .withArguments(new IntegerArgument("code"))
                .executesPlayer((player, args) -> {
                    final int code = (int) args.get("code");
                    final TwoFactorSetup twofactorsetup = this.setups.get(player.getUniqueId());

                    try
                    {
                        if (this.valid(twofactorsetup.secret, code))
                        {
                            this.setups.remove(player.getUniqueId());

                            final TwoFactorGson twofactorgson = new TwoFactorGson();
                            twofactorgson.secret = twofactorsetup.secret;

                            player.getPersistentDataContainer().set(this.twoFactorGsonKey, new TwoFactorDataType(), twofactorgson);

                            player.getInventory().all(Material.FILLED_MAP).forEach((index, itemStack) -> {
                                if (itemStack.getItemMeta().getPersistentDataContainer().has(this.mapKey))
                                {
                                    player.getInventory().setItem(index, null);
                                }
                            });

                            player.sendMessage("Two factor setup complete");
                            CommandAPI.updateRequirements(player);
                        }
                        else
                        {
                            player.sendRichMessage("<red>Invalid code");
                        }
                    }
                    catch (GeneralSecurityException e)
                    {
                        player.sendRichMessage("<red>Unable to validate");
                        this.getLogger().severe(e.getMessage());
                    }
                })
                .register();

        new CommandAPICommand("2fa")
                .withPermission("twofactor.command")
                .withArguments(new LiteralArgument("authenticate").withRequirement(sender -> {
                    return !this.authenticated((Player) sender);
                }))
                .withArguments(new IntegerArgument("code"))
                .executesPlayer((player, args) -> {
                    final int code = (int) args.get("code");
                    final TwoFactorGson twofactorgson = player.getPersistentDataContainer().get(this.twoFactorGsonKey, new TwoFactorDataType());
                    final TwoFactorLogin twofactorlogin = this.auths.get(player.getUniqueId());

                    try
                    {
                        if (this.valid(twofactorgson.secret, code))
                        {
                            this.auths.remove(player.getUniqueId());
                            player.getPersistentDataContainer().set(this.twoFactorGsonKey, new TwoFactorDataType(), twofactorgson);
                            player.sendMessage("Successfully authenticated");
                            CommandAPI.updateRequirements(player);
                        }
                        else
                        {
                            twofactorlogin.attempts++;

                            if (twofactorlogin.attempts == twofactorlogin.maxAttempts)
                            {
                                this.auths.remove(player.getUniqueId());
                                final String message = "<red>You've been kicked!\n\nYou failed too many times</red>";
                                player.kick(this.miniMessage.deserialize(message), PlayerKickEvent.Cause.PLUGIN);
                            }
                            else
                            {
                                player.sendRichMessage("<red>Invalid code");
                            }
                        }
                    }
                    catch (GeneralSecurityException e)
                    {
                        player.sendRichMessage("<red>Unable to authenticate");
                        this.getLogger().severe(e.getMessage());
                    }
                })
                .register();

        new CommandAPICommand("2fa")
                .withPermission("twofactor.command")
                .withArguments(new LiteralArgument("remove").withRequirement(sender -> {
                    return this.hasTwoFactor((Player) sender) && this.authenticated((Player) sender);
                }))
                .withArguments(new IntegerArgument("code"))
                .executesPlayer((player, args) -> {
                    final int code = (int) args.get("code");
                    final TwoFactorGson twofactorgson = player.getPersistentDataContainer().get(this.twoFactorGsonKey, new TwoFactorDataType());

                    try
                    {
                        if (this.valid(twofactorgson.secret, code))
                        {
                            player.sendMessage("Two factor removed");
                            player.getPersistentDataContainer().remove(this.twoFactorGsonKey);
                            CommandAPI.updateRequirements(player);
                        }
                        else
                        {
                            player.sendRichMessage("<red>Invalid code");
                        }
                    }
                    catch (GeneralSecurityException e)
                    {
                        player.sendMessage("<red>Unable to disable two factor");
                        this.getLogger().severe(e.getMessage());
                    }
                })
                .register();
    }

    @Override
    public void onDisable()
    {
    }

    public static TwoFactor instance()
    {
        return instance;
    }

    public boolean authenticated(Player player)
    {
        return !this.auths.containsKey(player.getUniqueId());
    }

    public boolean hasTwoFactor(Player player)
    {
        return player.getPersistentDataContainer().has(this.twoFactorGsonKey);
    }

    private TwoFactorSetup map(Player player)
    {
        final TwoFactorSetup twofactorsetup = new TwoFactorSetup();
        final QrCode qrcode = QrCode.encodeText("otpauth://totp/Neapovil:%s?secret=%s".formatted(player.getName(), twofactorsetup.secret), QrCode.Ecc.MEDIUM);
        final BufferedImage bufferedimage = new BufferedImage(qrcode.size + 4, qrcode.size + 4, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < bufferedimage.getWidth(); x++)
        {
            for (int y = 0; y < bufferedimage.getHeight(); y++)
            {
                boolean color = qrcode.getModule(x - 2, y - 2);
                bufferedimage.setRGB(x, y, color ? 0x008ccf : 0xffffff);
            }
        }

        final MapView mapview = this.getServer().createMap(player.getWorld());
        mapview.setScale(Scale.FARTHEST);
        mapview.getRenderers().clear();
        mapview.addRenderer(new QrMap(MapPalette.resizeImage(bufferedimage)));

        final ItemStack itemstack = new ItemStack(Material.FILLED_MAP);

        itemstack.editMeta(MapMeta.class, mapmeta -> {
            mapmeta.displayName(Component.text("Two Factor setup map"));
            mapmeta.setMapView(mapview);
            mapmeta.getPersistentDataContainer().set(this.mapKey, PersistentDataType.BOOLEAN, true);
        });

        player.getInventory().addItem(itemstack);

        return twofactorsetup;
    }

    private boolean valid(String secret, int code) throws GeneralSecurityException
    {
        return TimeBasedOneTimePasswordUtil.validateCurrentNumber(secret, code, 0);
    }
}
