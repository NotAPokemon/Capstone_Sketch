package dev.korgi.game.entites;

import java.util.List;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.math.VectorConstants;
import dev.korgi.utils.VoxTranslator;

public class Jimmy extends Entity {

    public Jimmy() {
        scale(0.125f);
        scaleHitbox(-1);
        hitboxOffset(VectorConstants.FORWARD.multiply(VectorConstants.HALF));
    }

    @Override
    protected List<Voxel> createBody() {
        return VoxTranslator.fromModel("jimmy");
    }

    @Override
    protected List<Voxel> createHitbox() {
        return VoxTranslator.fromModel("default");
    }

    @Override
    protected void client(double dt) {

    }

}
