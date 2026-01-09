package dev.korgi.game.rendering;

import dev.korgi.math.Vector4;

public class Material {
    private double opacity;
    private Vector4 color;
    private boolean rigid;
    private Integer textureLocation;

    public Material() {
        this.color = new Vector4(0.8, 0.8, 0.8, 1.0);

        this.opacity = 1.0;

        this.rigid = true;

        this.textureLocation = -1;
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

}
