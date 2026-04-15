package dev.korgi.game.items;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import dev.korgi.game.Game;
import dev.korgi.game.entites.Entity;
import dev.korgi.game.entites.StorageEntity;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONIgnore;
import dev.korgi.math.Vector3;
import dev.korgi.utils.VoxTranslator;
import processing.core.PImage;

public abstract class Item extends Entity {

    private static Map<String, PImage> iconCache = new HashMap<>();

    private boolean dropped = true;
    @JSONIgnore
    private PImage icon;
    @JSONIgnore
    private List<Voxel> sideStep = new ArrayList<>();

    public Item() {
        try {
            icon = iconCache.get(getIconName());
            if (icon == null) {
                File file = new File("./texture/items/" + Game.config.getString("pack") + "/" + getIconName());
                BufferedImage img = ImageIO.read(file);
                int w = img.getWidth();
                int h = img.getHeight();
                icon = new PImage(w, h, PImage.ARGB);
                img.getRGB(
                        0, 0,
                        w,
                        h,
                        icon.pixels,
                        0,
                        w);
                icon.updatePixels();
                iconCache.put(getIconName(), icon);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isDropped() {
        return dropped;
    }

    public void drop(StorageEntity entity) {
        dropped = entity.removeFromInventory(this);
        assert entity instanceof Entity;
        Entity e = (Entity) entity;
        position.copyFrom(e.getPosition());
    }

    @Override
    protected List<Voxel> createBody() {
        return VoxTranslator.fromModel(getModelName());
    }

    protected abstract String getModelName();

    protected abstract String getIconName();

    @Override
    public void onCollide(Entity other, Vector3 bodyPart, Vector3 otherBodyPart, Vector3 penetration) {
        if (dropped && other instanceof StorageEntity e) {
            dropped = !e.addToInventory(this);
        }
    }

    @Override
    protected List<Voxel> createHitbox() {
        return this.body;
    }

    public PImage getIcon() {
        return icon;
    }

    @Override
    protected void client(double dt) {
        boolean visible = getBody().get(0).getMaterial().getOpacity() > 0.001;
        if (dropped && !visible) {
            setOpacity(1);
        } else if (!dropped && visible) {
            setOpacity(0);
        }
    }

}
