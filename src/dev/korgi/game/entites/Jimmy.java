package dev.korgi.game.entites;

import java.util.List;

import dev.korgi.game.rendering.Voxel;

public class Jimmy extends Entity {

    public Jimmy() {
        scale(0.0625f);
    }

    @Override
    protected List<Voxel> createBody() {
        return fromModel("player");
    }

    @Override
    protected void client(double dt) {

    }

    @Override
    protected void server(double dt) {

    }

}
