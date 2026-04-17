package dev.korgi.networking;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.korgi.game.Game;
import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;

public abstract class NetworkObject {

    public String internal_id;
    @JSONIgnore
    private boolean canceledTickEnd = false;
    @JSONIgnore
    private Supplier<Boolean> cancelTick;

    @JSONIgnore
    protected List<Runnable> tickTasks = new ArrayList<>();

    public void loop(double dt) {
        if (NetworkStream.frameCount >= 50) {
            NetworkStream.frameCount = 0;
            NetworkStream.packetCount = 0;
        }
        NetworkStream.frameCount++;
        Packet incomming_packet = NetworkStream.getPacket(internal_id, Game.isClient);
        if (incomming_packet != null) {
            handleInPacket(incomming_packet);
            incomming_packet.getData().fillObject(this);
            NetworkStream.packetCount++;
        }
        if (cancelTick != null && cancelTick.get()) {
            return;
        }

        tickTasks.forEach((task) -> {
            task.run();
        });
        tickTasks.clear();

        if (Game.isClient) {
            client(dt);
        } else {
            server(dt);
        }

        if (canceledTickEnd) {
            canceledTickEnd = false;
            return;
        }

    }

    protected void cancelTickEnd() {
        canceledTickEnd = true;
    }

    protected void setCancelProtocol(Supplier<Boolean> supplier) {
        this.cancelTick = supplier;
    }

    protected void handleOutPacket(Packet out) {

    }

    private void handleInPacket(Packet in) {
        Class<?> clazz = this.getClass();
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods) || field.isAnnotationPresent(JSONIgnore.class)) {
                    continue;
                }
                if (Game.isClient && field.isAnnotationPresent(ClientSide.class)) {
                    fields.add(field);
                } else if (!Game.isClient && field.isAnnotationPresent(ServerSide.class)) {
                    fields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }

        JSONObject inData = in.getData();

        for (Field field : fields) {
            inData.set(field.getName(), null);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends NetworkObject> void run(Consumer<T> task) {
        tickTasks.add(() -> task.accept((T) this));
    }

    public void sendOut() {
        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, Game.isClient ? NetworkStream.SERVER : NetworkStream.CLIENT,
                Game.isClient ? NetworkStream.INPUT_HANDLE_REQUEST : NetworkStream.BROADCAST, outData);
        handleOutPacket(outPacket);
        NetworkStream.sendPacket(outPacket);
    }

    @ClientSide
    protected abstract void client(double dt);

    @ServerSide
    protected abstract void server(double dt);

}
