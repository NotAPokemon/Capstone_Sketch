package dev.korgi.networking;

import dev.korgi.Game;
import dev.korgi.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkStream {

    public static final int PROTOCOL_VERSION = 1;

    private static final long HANDSHAKE_TIMEOUT_MS = 5000;
    private static final long PING_TIMEOUT_MS = 15000;
    private static final long PING_INTERVAL_MS = 3000;

    // ========================
    // Packet queues
    // ========================
    public static List<Packet> serverPackets = new ArrayList<>();
    public static List<Packet> clientPackets = new ArrayList<>();

    // ========================
    // Sockets
    // ========================
    private static ServerSocket serverSocket;
    private static Socket clientSocket;

    private static final Map<String, Socket> clientSockets = new HashMap<>();
    private static final Map<Socket, String> socketToClientId = new HashMap<>();

    private static final Set<Socket> pendingSockets = new HashSet<>();
    private static final Map<Socket, Long> pendingSince = new HashMap<>();

    private static final Map<String, Long> lastPing = new HashMap<>();

    public static String clientId;
    private static long lastPingSent = 0;

    // ========================
    // Incoming queue (socket-aware)
    // ========================
    private static final Queue<Incoming> incoming = new ConcurrentLinkedQueue<>();

    private static class Incoming {
        Socket socket;
        String json;

        Incoming(Socket socket, String json) {
            this.socket = socket;
            this.json = json;
        }
    }

    // ========================
    // Startup
    // ========================
    public static void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1);
        System.out.println("Server listening on port " + port);
    }

    public static void startClient(String host, int port) throws IOException {
        clientSocket = new Socket(host, port);
        new Thread(() -> listenSocket(clientSocket)).start();

        clientId = UUID.randomUUID().toString();
        sendHandshake();

        System.out.println("Connected to " + host + ":" + port);
    }

    // ========================
    // Tick update
    // ========================
    public static void update(boolean isServer) {
        try {
            if (isServer && serverSocket != null) {
                try {
                    Socket client = serverSocket.accept();
                    pendingSockets.add(client);
                    pendingSince.put(client, System.currentTimeMillis());
                    new Thread(() -> listenSocket(client)).start();
                    System.out.println("Incoming connection...");
                } catch (SocketTimeoutException ignored) {
                }
            }

            handleHandshakeTimeouts();
            handlePingTimeouts();

            if (!isServer && clientSocket != null &&
                    System.currentTimeMillis() - lastPingSent > PING_INTERVAL_MS) {
                sendPacket(new Packet(clientId, 0, 9, new JSONObject()));
                lastPingSent = System.currentTimeMillis();
            }

            while (!incoming.isEmpty()) {
                Incoming in = incoming.poll();
                acceptPacket(in.socket, in.json);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================
    // Socket listener
    // ========================
    private static void listenSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                incoming.add(new Incoming(socket, line));
            }

        } catch (IOException ignored) {
        }
    }

    // ========================
    // Sending
    // ========================
    public static void sendPacket(Packet packet) {
        try {
            String json = new JSONObject(packet).toJSONString();

            if (packet.getDestination() == 0) {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    sendString(clientSocket, json);
                }
            } else {
                if (packet.getType() == -1) {
                    for (Socket s : clientSockets.values()) {
                        sendString(s, json);
                    }
                } else {
                    Socket s = clientSockets.get(packet.network_destination);
                    if (s != null)
                        sendString(s, json);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendString(Socket socket, String msg) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(msg);
    }

    // ========================
    // Receiving
    // ========================
    private static void acceptPacket(Socket socket, String packetString) {
        JSONObject obj = JSONObject.fromJSONString(packetString);

        Packet packet = new Packet(
                obj.getString("internal_id"),
                obj.getInt("destination"),
                obj.getInt("type"),
                obj.getJSONObject("data"));

        if (handleProtocolPackets(socket, packet, obj))
            return;

        if (packet.getDestination() == 0)
            clientPackets.add(packet);
        else
            serverPackets.add(packet);
    }

    // ========================
    // Protocol handling
    // ========================
    private static boolean handleProtocolPackets(Socket socket, Packet packet, JSONObject raw) {

        // HANDSHAKE REQUEST
        if (packet.getType() == 2) {
            if (!pendingSockets.contains(socket))
                return true;

            JSONObject data = raw.getJSONObject("data");
            System.out.println(data);
            int version = data.getInt("protocol");
            if (version != PROTOCOL_VERSION) {
                sendHandshakeResponse(socket, false, "Protocol mismatch");
                close(socket);
                return true;
            }

            pendingSockets.remove(socket);
            pendingSince.remove(socket);

            clientSockets.put(packet.getInternalId(), socket);
            socketToClientId.put(socket, packet.getInternalId());
            lastPing.put(packet.getInternalId(), System.currentTimeMillis());

            sendHandshakeResponse(socket, true, "OK");
            System.out.println("Handshake accepted: " + packet.getInternalId());
            Game.playerConnected(packet.getInternalId());
            return true;
        }

        // HANDSHAKE RESPONSE (client)
        if (packet.getType() == 3) {
            boolean ok = raw.getJSONObject("data").getBoolean("accepted");
            if (!ok) {
                System.out.println("Handshake rejected");
                close(clientSocket);
            } else {
                System.out.println("Handshake successful");
                Game.playerConnected(clientId);
            }
            return true;
        }

        // PING
        if (packet.getType() == 9) {
            lastPing.put(packet.getInternalId(), System.currentTimeMillis());
            return true;
        }

        // DISCONNECT
        if (packet.getType() == 10) {
            disconnectClient(packet.getInternalId());
            return true;
        }

        return false;
    }

    // ========================
    // Handshake helpers
    // ========================
    private static void sendHandshake() {
        JSONObject data = new JSONObject();
        data.set("protocol", PROTOCOL_VERSION);

        sendPacket(new Packet(clientId, 0, 2, data));
    }

    private static void sendHandshakeResponse(Socket socket, boolean ok, String reason) {
        try {
            JSONObject data = new JSONObject();
            data.set("accepted", ok);
            data.set("reason", reason);

            Packet p = new Packet("server", 0, 3, data);
            sendString(socket, new JSONObject(p).toJSONString());
        } catch (IOException ignored) {
        }
    }

    // ========================
    // Timeouts
    // ========================
    private static void handleHandshakeTimeouts() {
        long now = System.currentTimeMillis();
        pendingSockets.removeIf(s -> {
            if (now - pendingSince.getOrDefault(s, now) > HANDSHAKE_TIMEOUT_MS) {
                close(s);
                pendingSince.remove(s);
                return true;
            }
            return false;
        });
    }

    private static void handlePingTimeouts() {
        long now = System.currentTimeMillis();
        lastPing.entrySet().removeIf(e -> {
            if (now - e.getValue() > PING_TIMEOUT_MS) {
                disconnectClient(e.getKey());
                return true;
            }
            return false;
        });
    }

    // ========================
    // Utilities
    // ========================
    private static void disconnectClient(String id) {
        Socket s = clientSockets.remove(id);
        if (s != null)
            close(s);
        socketToClientId.values().remove(id);
        lastPing.remove(id);
        System.out.println("Client disconnected: " + id);
    }

    private static void close(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    public static Packet getPacket(String internalId, boolean isClient) {
        List<Packet> list = isClient ? clientPackets : serverPackets;
        Iterator<Packet> it = list.iterator();
        while (it.hasNext()) {
            Packet p = it.next();
            if (p.getInternalId().equals(internalId)) {
                it.remove();
                return p;
            }
        }
        return null;
    }
}
