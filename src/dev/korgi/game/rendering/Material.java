package dev.korgi.game.rendering;

import dev.korgi.math.Vector4;

public class Material {
    private double opacity;
    private double metallic;
    private double roughness;
    private Vector4[][] albedoMap;
    private Vector4[][] normalMap;
    private Vector4[][] metallicMap;
    private Vector4[][] roughnessMap;
    private Vector4[][] emissiveMap;
    private Vector4 color;
    private Vector4 emission;
    private double emissionPower;
    private double ior;
    private double normalStrength;
    private Vector4 absorption;
    private boolean doubleSided;

    public Material() {
        Vector4 blenderGray = new Vector4(0.8, 0.8, 0.8, 1.0);
        Vector4 black = new Vector4(0, 0, 0, 1);

        this.color = blenderGray;
        this.albedoMap = new Vector4[][] {
                { blenderGray }
        };

        // PBR defaults
        this.metallic = 0.0;
        this.roughness = 0.5;
        this.opacity = 1.0;
        this.ior = 1.0;

        // Optional maps default to null (means "use scalar")
        this.metallicMap = null;
        this.roughnessMap = null;
        this.emissiveMap = null;

        // Normal mapping defaults
        this.normalMap = null;
        this.normalStrength = 1.0;

        // Emission
        this.emission = black;
        this.emissionPower = 0.0;

        // Volume / ray tracing
        this.absorption = black;

        this.doubleSided = false;
    }

    public Vector4 getColor() {
        return color;
    }

    public double getMetallic() {
        return metallic;
    }

    public double getRoughness() {
        return roughness;
    }

    public double getEmissionPower() {
        return emissionPower;
    }

    public Vector4 getAbsorption() {
        return absorption;
    }

    public Vector4[][] getAlbedoMap() {
        return albedoMap;
    }

    public Vector4 getEmission() {
        return emission;
    }

    public Vector4[][] getEmissiveMap() {
        return emissiveMap;
    }

    public double getIor() {
        return ior;
    }

    public Vector4[][] getMetallicMap() {
        return metallicMap;
    }

    public Vector4[][] getNormalMap() {
        return normalMap;
    }

    public double getNormalStrength() {
        return normalStrength;
    }

    public double getOpacity() {
        return opacity;
    }

    public Vector4[][] getRoughnessMap() {
        return roughnessMap;
    }

    public boolean getDoubleSided() {
        return doubleSided;
    }

    public void setColor(Vector4 color) {
        this.color = color;
    }

    public void setAbsorption(Vector4 absorption) {
        this.absorption = absorption;
    }

    public void setAlbedoMap(Vector4[][] albedoMap) {
        this.albedoMap = albedoMap;
    }

    public void setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
    }

    public void setEmission(Vector4 emission) {
        this.emission = emission;
    }

    public void setEmissionPower(double emissionPower) {
        this.emissionPower = emissionPower;
    }

    public void setEmissiveMap(Vector4[][] emissiveMap) {
        this.emissiveMap = emissiveMap;
    }

    public void setIor(double ior) {
        this.ior = ior;
    }

    public void setMetallic(double metallic) {
        this.metallic = metallic;
    }

    public void setMetallicMap(Vector4[][] metallicMap) {
        this.metallicMap = metallicMap;
    }

    public void setNormalMap(Vector4[][] normalMap) {
        this.normalMap = normalMap;
    }

    public void setNormalStrength(double normalStrength) {
        this.normalStrength = normalStrength;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }

    public void setRoughness(double roughness) {
        this.roughness = roughness;
    }

    public void setRoughnessMap(Vector4[][] roughnessMap) {
        this.roughnessMap = roughnessMap;
    }

}
