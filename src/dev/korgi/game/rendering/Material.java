package dev.korgi.game.rendering;

import dev.korgi.math.Vector4;

public class Material {
    private double opacity;
    private Vector4 color;
    private boolean rigid;
    private Integer textureLocation;
    private int size = 1;

    public Material() {
        this.color = new Vector4(0.8, 0.8, 0.8, 1.0);

        this.opacity = 1.0;

        this.rigid = true;

        this.textureLocation = -1;

        this.size = 1;
    }

    public Vector4 getColor() {
        return color;
    }

    public double getOpacity() {
        return opacity;
    }

    public Integer getTextureLocation() {
        return textureLocation;
    }

    public boolean isRigid() {
        return rigid;
    }

    public int getSize() {
        return size;
    }

    public void setColor(Vector4 color) {
        this.color = color;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }

    public void setRigid(boolean rigid) {
        this.rigid = rigid;
    }

    public void setTextureLocation(Integer textureLocation) {
        this.textureLocation = textureLocation;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Material copy() {
        Material m = new Material();
        m.setColor(this.color);
        m.setOpacity(this.opacity);
        m.setRigid(this.rigid);
        m.setTextureLocation(this.textureLocation);
        m.setSize(this.size);
        return m;
    }

}
