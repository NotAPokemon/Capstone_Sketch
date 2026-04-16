package dev.korgi.game.items;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import dev.korgi.game.Game;
import dev.korgi.game.entites.Entity;
import dev.korgi.game.entites.StorageEntity;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.ui.builder.UIBuilder;
import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONIgnore;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.Packet;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;
import dev.korgi.utils.VoxTranslator;
import processing.core.PImage;

public abstract class Item extends Entity {

    private static Map<String, PImage> iconCache = new HashMap<>();

    private boolean dropped = true;

    @JSONIgnore
    private PImage icon;

    @JSONIgnore
    private static UI display = UIBuilder.create("debug")
            .canvas(new Canvas("Jimmy"))
            .text(new Text("dropped"))
            .position(100, 100)
            .color(0xFFFF0000)
            .backToParent()
            .backToParent()
            .build();

    public Item() {
        try {
            icon = iconCache.get(getIconName());
            if (icon == null) {
                File file = new File("./texture/" + Game.config.getString("pack") + "/items/" + getIconName());
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
        displayAxis = VectorConstants.ONE;
        display.open();
    }

    public boolean isDropped() {
        return dropped;
    }

    @ServerSide
    public void drop(StorageEntity entity) {
        dropped = entity.removeFromInventory(this);
        if (dropped) {
            assert entity instanceof Entity;
            Entity e = (Entity) entity;
            position.copyFrom(e.getPosition());
        }
    }

    @Override
    protected List<Voxel> createBody() {
        return VoxTranslator.fromModel(getModelName());
    }

    protected abstract String getModelName();

    protected abstract String getIconName();

    @ServerSide
    public void rmb() {
    }

    @ServerSide
    public void lmb() {

    }

    @Override
    @ServerSide
    public void onCollide(Entity other, Vector3 bodyPart, Vector3 otherBodyPart, Vector3 penetration) {
        if (dropped && other instanceof StorageEntity e) {
            dropped = !e.addToInventory(this);
        }
    }

    @Override
    @ServerSide
    protected List<Voxel> createHitbox() {
        return this.body;
    }

    public PImage getIcon() {
        return icon;
    }

    @Override
    @ClientSide
    protected void client(double dt) {
        boolean visible = getBody().get(0).getMaterial().getOpacity() > 0.001;
        if (dropped && !visible) {
            setOpacity(1);
        } else if (!dropped && visible) {
            setOpacity(0);
        }
        display.getStyle().set("dropped.value", Boolean.toString(dropped));
    }

    @Override
    protected void handleInPacket(Packet in) {
        super.handleInPacket(in);
        if (!Game.isClient) {
            in.getData().set("dropped", null);
        }
    }

}
