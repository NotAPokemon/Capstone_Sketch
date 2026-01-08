package dev.korgi.game.rendering;

import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.math.VectorConstants;

public class Material {
    private double opacity;
    private Vector4 color;
    private boolean rigid;
    private Vector3 size;
    private boolean isEntity;

    public Material() {
        Vector4 blenderGray = new Vector4(0.8, 0.8, 0.8, 1.0);

        this.color = blenderGray;

        this.opacity = 1.0;

        this.rigid = true;

        this.size = VectorConstants.ONE;

        this.isEntity = false;
    }

    public Vector4 getColor() {
        return color;
    }

    public double getOpacity() {
        return opacity;
    }

    public boolean isRigid() {
        return rigid;
    }

    public Vector3 getSize() {
        return size;
    }

    public boolean isEntity() {
        return isEntity;
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

    public void setSize(Vector3 size) {
        this.size = size;
    }

    public void setEntity(boolean isEntity) {
        this.isEntity = isEntity;
    }

}
