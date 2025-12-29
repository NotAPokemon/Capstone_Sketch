package dev.korgi.networking;

import dev.korgi.game.Game;
import dev.korgi.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkStream {

    public static final int PROTOCOL_VERSION = 1;

    // Destinations
    public static final int CLIENT = 0;
    public static final int SERVER = 1;

    // Packet types
    public static final int BROADCAST = -1;
    public static final int INPUT_HANDLE_REQUEST = 0;
    public static final int WORLD_UPDATE = 1;
    public static final int HANDSHAKE_REQUEST = 2;
    public static final int HANDSHAKE_RESPONSE = 3;
    public static final int PING = 9;
    public static final int DISCONNECT = 10;

    private static final long HANDSHAKE_TIMEOUT_MS = 5000;
    private static final long PING_TIMEOUT_MS = 15000;
    private static final long PING_INTERVAL_MS = 3000;

    // Packet queues
    public static final List<Packet> serverPackets = new ArrayList<>();
    public static final List<Packet> clientPackets = new ArrayList<>();

    // Sockets
    private static ServerSocket serverSocket;
    private static Socket clientSocket;

    private static final Map<String, Socket> clientSockets = new HashMap<>();
    private static final Map<Socket, String> socketToClientId = new HashMap<>();
    private static final Set<Socket> pendingSockets = new HashSet<>();
    private static final Map<Socket, Long> pendingSince = new HashMap<>();
    private static final Map<String, Long> lastPing = new HashMap<>();

    public static String clientId = null;
    private static long lastPingSent = 0;
    private static long lastRoundTripPing = -1;

    // Incoming queue
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
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
        }
        sendHandshake();
        System.out.println("Connected to " + host + ":" + port);
    }

    private static void sendPingPacket() {
        JSONObject data = new JSONObject();
        long timestamp = System.currentTimeMillis();
        data.set("timestamp", timestamp);
        sendPacket(new Packet(clientId, SERVER, PING, data));
        lastPingSent = timestamp;
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
                sendPingPacket();
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
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

            // BROADCAST handling
            if (packet.getType() == BROADCAST && packet.getDestination() == CLIENT) {
                for (Socket s : clientSockets.values())
                    sendString(s, json);
                return;
            }

            switch (packet.getDestination()) {
                case CLIENT: // server -> specific client
                    if (packet.network_destination != null) {
                        Socket s = clientSockets.get(packet.network_destination);
                        if (s != null)
                            sendString(s, json);
                    }
                    break;
                case SERVER: // client -> server
                    if (clientSocket != null && !clientSocket.isClosed())
                        sendString(clientSocket, json);
                    break;
                default:
                    System.err.println("Unknown destination: " + packet.getDestination());
            }

        } catch (SocketException e) {
            if (Game.isClient) {
                e.printStackTrace();
            } else {
                disconnectClient(packet.getInternalId());
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

        if (packet.getDestination() == CLIENT)
            clientPackets.add(packet);
        else
            serverPackets.add(packet);
    }

    // ========================
    // Protocol handling
    // ========================
    private static boolean handleProtocolPackets(Socket socket, Packet packet, JSONObject raw) {
        // HANDSHAKE REQUEST (server side)
        if (packet.getType() == HANDSHAKE_REQUEST) {
            if (!pendingSockets.contains(socket))
                return true;

            JSONObject data = raw.getJSONObject("data");
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

        // HANDSHAKE RESPONSE (client side)
        if (packet.getType() == HANDSHAKE_RESPONSE) {
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
        if (packet.getType() == PING) {
            if (Game.isClient) {
                JSONObject data = packet.getData();
                if (data.hasKey("timestamp")) {
                    long sentTime = data.getLong("timestamp");
                    lastRoundTripPing = System.currentTimeMillis() - sentTime;
                }
                lastPing.put(packet.getInternalId(), System.currentTimeMillis());
            } else {
                JSONObject data = packet.getData();
                if (data.hasKey("timestamp")) {
                    JSONObject replyData = new JSONObject();
                    replyData.set("timestamp", data.getLong("timestamp"));
                    sendPacket(new Packet("server", CLIENT, PING, replyData));
                }

                lastPing.put(packet.getInternalId(), System.currentTimeMillis());
            }
            return true;
        }

        // DISCONNECT
        if (packet.getType() == DISCONNECT) {
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
        sendPacket(new Packet(clientId, SERVER, HANDSHAKE_REQUEST, data));
    }

    private static void sendHandshakeResponse(Socket socket, boolean ok, String reason) {
        try {
            JSONObject data = new JSONObject();
            data.set("accepted", ok);
            data.set("reason", reason);
            Packet p = new Packet("server", CLIENT, HANDSHAKE_RESPONSE, data);
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
        try {
            lastPing.entrySet().removeIf(e -> {
                if (now - e.getValue() > PING_TIMEOUT_MS) {
                    disconnectClient(e.getKey());
                    return true;
                }
                return false;
            });
        } catch (ConcurrentModificationException e) {
        }

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
        List<Packet> packets = new ArrayList<>();
        for (Packet p : list) {
            if (p.getInternalId().equals(internalId)) {
                packets.add(p);
            }
        }
        for (Packet p : packets) {
            list.remove(p);
        }
        if (packets.size() > 0) {
            return packets.get(packets.size() - 1);
        } else {
            return null;
        }
    }

    public static List<Packet> getAllPackets(String internalId, boolean isClient) {
        List<Packet> list = isClient ? clientPackets : serverPackets;
        List<Packet> packets = new ArrayList<>();
        for (Packet p : list) {
            if (p.getInternalId().equals(internalId)) {
                packets.add(p);
            }
        }
        for (Packet p : packets) {
            list.remove(p);
        }
        return packets;
    }

    public static long getPing() {
        return lastRoundTripPing;
    }

}
