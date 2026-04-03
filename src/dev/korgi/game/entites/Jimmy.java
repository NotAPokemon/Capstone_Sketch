package dev.korgi.game.entites;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.physics.Entity;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Voxel;

public class Jimmy extends Entity {

    public Jimmy() {
        scale(0.125f);
    }

    @Override
    protected List<Voxel> createBody() {
        List<Voxel> body = fromModel("player");
        for (Voxel voxel : body) {
            WorldEngine.addVoxel(voxel);
        }
        return new ArrayList<>();
    }

    @Override
    protected void client(double dt) {

    }

    @Override
    protected void server(double dt) {

    }

}
