package com.github.neapovil.twofactor.map;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import com.github.neapovil.twofactor.TwoFactor;

public final class QrMap extends MapRenderer
{
    private BufferedImage image;
    private boolean draw = false;
    private Instant started = Instant.now();

    public QrMap(BufferedImage image)
    {
        this.image = image;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player)
    {
        if (!this.draw)
        {
            this.draw = true;
            canvas.drawImage(0, 0, this.image);
            map.setLocked(true);
        }

        if (Duration.between(this.started, Instant.now()).toMinutes() == 2)
        {
            player.getInventory().all(Material.FILLED_MAP).forEach((index, itemStack) -> {
                if (itemStack.getItemMeta().getPersistentDataContainer().has(TwoFactor.instance().mapKey))
                {
                    player.getInventory().setItem(index, null);
                }
            });
            player.sendRichMessage("<red>You took too long to setup");
        }
    }
}
