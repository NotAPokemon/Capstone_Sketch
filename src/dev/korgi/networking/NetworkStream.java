package dev.korgi.networking;

import dev.korgi.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkStream {

    // Packet storage
    public static List<Packet> serverPackets = new ArrayList<>();
    public static List<Packet> clientPackets = new ArrayList<>();
    private static Map<String, Socket> clientSockets = new HashMap<>();
    private static Queue<String> incomingStrings = new ConcurrentLinkedQueue<>();

    // Connections mapping
    private static Map<String, String> connections = new HashMap<>();

    // Server and client sockets
    private static ServerSocket serverSocket;
    private static Socket clientSocket;

    // ========================
    // Initialize server
    // ========================
    public static void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
    }

    // ========================
    // Initialize client
    // ========================
    public static void startClient(String host, int port) throws IOException {
        clientSocket = new Socket(host, port);
        new Thread(() -> listenSocket(clientSocket)).start();
        System.out.println("Connected to server " + host + ":" + port);
    }

    // ========================
    // Main update loop (called each tick)
    // ========================
    public static void update(boolean isServer) {
        try {
            if (isServer && serverSocket != null) {
                // Accept new clients without blocking
                if (serverSocket != null && serverSocket.isBound()) {
                    serverSocket.setSoTimeout(1);
                    try {
                        Socket client = serverSocket.accept();
                        String id = UUID.randomUUID().toString();
                        clientSockets.put(id, client);
                        connections.put(id, id);
                        new Thread(() -> listenSocket(client)).start();
                        System.out.println("Client connected: " + id);
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            }

            // Handle incoming JSON strings
            while (!incomingStrings.isEmpty()) {
                String json = incomingStrings.poll();
                acceptPacket(json);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================
    // Listen to a socket for incoming packets
    // ========================
    private static void listenSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                incomingStrings.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================
    // Send a packet
    // ========================
    public static void sendPacket(Packet packet) {
        try {
            String json = new JSONObject(packet).toJSONString();

            if (packet.getDestination() == 0) { // client packet to server
                if (clientSocket != null && !clientSocket.isClosed()) {
                    sendString(clientSocket, json);
                }
            } else { // server sending to clients
                if (packet.getType() == -1) { // broadcast
                    for (Socket client : clientSockets.values()) {
                        if (client != null && !client.isClosed()) {
                            sendString(client, json);
                        }
                    }
                } else {
                    Socket client = clientSockets.get(packet.network_destination);
                    if (client != null && !client.isClosed()) {
                        sendString(client, json);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendString(Socket socket, String message) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(message);
    }

    // ========================
    // Accept a JSON string as a packet
    // ========================
    private static void acceptPacket(String packetString) {
        JSONObject obj = new JSONObject(packetString);
        Packet packet = new Packet(obj.getString("internal_id"),
                obj.getInt("destination"), obj.getInt("type"), obj.getJSONObject("data"));
        if (packet.getDestination() == 0) {
            clientPackets.add(packet);
        } else {
            serverPackets.add(packet);
        }
    }

    public static String locate(String internal_id) {
        return connections.get(internal_id);
    }

    public static Packet getPacket(String internal_id, boolean isClient) {
        List<Packet> array = isClient ? clientPackets : serverPackets;
        for (Packet p : array) {
            if (p.getInternalId().equals(internal_id)) {
                return p;
            }
        }
        return null;
    }
}
