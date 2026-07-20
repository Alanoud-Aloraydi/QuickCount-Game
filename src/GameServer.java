import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


public class GameServer {
    // Constants for server configuration
    private static final int PORT = 9090; // Port number the server listens on
    private static final int MIN_PLAYERS = 2; // Minimum players required to start a game
    private static final int MAX_PLAYERS = 4; // Maximum players allowed in a game
    private static final int GAME_START_DELAY = 30; // Delay in seconds before starting a game if there are too few players

    // Variables to manage game rooms and players
    // Wrapped with Collections.synchronized* since these are shared and mutated
    // concurrently by every client's handler thread.
    private static int roomCounter = 1; // Counter to generate unique room IDs
    private static Set<String> connectedPlayers = Collections.synchronizedSet(new HashSet<>()); // Set of all connected players
    private static List<String> waitingRoom = Collections.synchronizedList(new ArrayList<>()); // List of players waiting to start a game
    private static Map<String, List<String>> gameRooms = Collections.synchronizedMap(new HashMap<>()); // Map of game rooms and their players
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // Scheduler for delayed game starts
    private static Map<String, Integer> playerScores = Collections.synchronizedMap(new HashMap<>());
    private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>()); // List of active client handlers
    private static ScheduledFuture<?> gameStartTimer = null; // Timer for delayed game start
    private static Map<String, Set<Integer>> roomCompletedLevels = Collections.synchronizedMap(new HashMap<>());
    private static List<PrintWriter> clientOutputs = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        System.out.println("Game Server is running...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // Continuously accept new client connections
            while (true) {
                Socket clientSocket = serverSocket.accept(); // Accept a new client connection
                ClientHandler clientHandler = new ClientHandler(clientSocket); // Create a handler for the client
                clients.add(clientHandler); // Add the handler to the list of clients
                new Thread(clientHandler).start(); // Start a new thread to handle the client
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle any errors that occur
        }
    }

    // Inner class to handle communication with a single client
    private static class ClientHandler implements Runnable {
        private Socket socket; // Socket for communication with the client
        private PrintWriter out; // Output stream to send messages to the client
        private BufferedReader in; // Input stream to receive messages from the client
        private String username; // Username of the connected player

        public ClientHandler(Socket socket) {
            this.socket = socket; // Initialize the socket
        }

        public void run() {
            try {
                // Set up input and output streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                // Continuously read messages from the client
                while ((message = in.readLine()) != null) {
                    handleClientMessage(message); // Handle the received message
                }
            } catch (IOException e) {
                // A client closing its window resets the connection; that is a
                // normal disconnect, not an error, so just note it cleanly.
                System.out.println("Client disconnected"
                        + (username != null ? ": " + username : ""));
            } finally {
                disconnect(); // Clean up when the client disconnects
            }
        }

        // Method to handle different types of messages from the client
        private void handleClientMessage(String message) {
            if (message.startsWith("CONNECT")) {
                // Handle player connection
                username = message.substring(8); // Extract the username
                connectedPlayers.add(username); // Add the player to the connected players set
                sendToAll("PLAYER_LIST " + String.join(", ", connectedPlayers)); // Broadcast updated player list
            } else if (message.startsWith("PAIR_REQUEST")) {
                // Handle player request to join the waiting room
                addToWaitingRoom();
            }
           else if (message.startsWith("GET_GAMEROOM_PLAYERS")) {
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String roomId = parts[1];
            List<String> playersInRoom = gameRooms.get(roomId);
if (playersInRoom != null) {
    StringBuilder response = new StringBuilder("ROOM_PLAYERS:" + roomId);
    for (String p : playersInRoom) {
        int score = playerScores.getOrDefault(p, 0);
        response.append(":").append(p).append(",").append(score);
    }
    out.println(response.toString());
}
else {
                out.println("ERROR:Room not found"); 
            }
        } else {
            out.println("ERROR:Invalid room request"); 
        }
    } 
  else if (message.startsWith("UPDATE_SCORE:")) {
        String[] parts = message.split(":", 3);
        if (parts.length == 3) {
            String player = parts[1];
            try {
                int newScore = Integer.parseInt(parts[2]);
                if (connectedPlayers.contains(player)) {
                    updatePlayerScore(player, newScore);
                } else {
                    out.println("ERROR:Player not found");
                }
            } catch (NumberFormatException e) {
                out.println("ERROR:Invalid score format");
            }
        } else {
            out.println("ERROR:Invalid score update message");
        }

    } else if (message.startsWith("LEVEL_COMPLETE:")) {
    String[] parts = message.split(":", 3);
    if (parts.length == 3) {
        String player = parts[1];
        int levelNumber;

        try {
            levelNumber = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            out.println("ERROR:Invalid level number");
            return;
        }

        String roomId = getRoomIdByPlayer(player);

        if (roomId != null) {
            roomCompletedLevels.putIfAbsent(roomId, new HashSet<>());
            Set<Integer> completedLevels = roomCompletedLevels.get(roomId);

            if (!completedLevels.contains(levelNumber)) { 
                completedLevels.add(levelNumber);

                int newScore = playerScores.getOrDefault(player, 0) + 10;
                updatePlayerScore(player, newScore);

                sendToRoom(roomId, "WINNER:" + player + ":LEVEL:" + levelNumber);
            } else {
                out.println("INFO:Stage already completed by another player");
            }
        } else {
            out.println("ERROR:Room not found for player " + player);
        }
    }
}/*else if(message.startsWith("NO_PLAYERS:")){
    String[] parts = message.split(":", 3);
    if (parts.length == 3) {
        String player = parts[1];
        System.out.print(message);
        System.out.print(player);
      sendToAll("WINNER:" + player);
}
}*/

}

private void updatePlayerScore(String player, int newScore) {
    playerScores.put(player, newScore);
    String roomId = getRoomIdByPlayer(player);
    if (roomId == null) return;

    StringBuilder scoreUpdateMessage = new StringBuilder("SCORE_UPDATE");
    List<String> playersInRoom = gameRooms.get(roomId);

    if (playersInRoom != null && !playersInRoom.isEmpty()) {
        for (String p : playersInRoom) {
            scoreUpdateMessage.append(":").append(p).append(",").append(playerScores.getOrDefault(p, 0));
        }
    } else {
        System.out.println("⚠️ playersInRoom is null or empty for room: " + roomId);
    }

    System.out.println("📤 Sending score update: " + scoreUpdateMessage);
    sendToRoom(roomId, scoreUpdateMessage.toString());
}

public void broadcastToAllClients(String message, int roomID) {
    for (PrintWriter clientOut : clientOutputs) {
        System.out.println("broadcastToAllClients: " +message );
        clientOut.println("Winner:"+message);
    }
}




private String getRoomIdByPlayer(String player) {
    for (Map.Entry<String, List<String>> entry : gameRooms.entrySet()) {
        if (entry.getValue().contains(player)) {
            return entry.getKey();
        }
    }
    return null;
}


  // Method to add a player to the waiting room
private void addToWaitingRoom() {
    waitingRoom.add(username); // Add the player to the waiting room
    sendToAll("PLAYER_JOINED " + username); // Notify all players that a new player has joined
    sendToAll("WAITING_ROOM " + String.join(", ", waitingRoom)); // Broadcast updated waiting room list

    if (waitingRoom.size() == MIN_PLAYERS) {
        // Start the timer when exactly 2 players join
        startGameTimer();
    } else if (waitingRoom.size() >= MAX_PLAYERS) {
        // If 4 players join, start the game immediately
        if (gameStartTimer != null) {
            gameStartTimer.cancel(false); // Cancel the timer if it's running
        }
        startGameIfReady();
    }
}

// Method to start the game timer
private void startGameTimer() {
    if (gameStartTimer == null || gameStartTimer.isDone()) {
        sendToAll("TIMER_START " + GAME_START_DELAY); // Notify clients about the timer
        gameStartTimer = scheduler.schedule(() -> {
            startGameIfReady(); // Start the game after the delay
        }, GAME_START_DELAY, TimeUnit.SECONDS);
    }
}

        // Method to start a game if the conditions are met
        private void startGameIfReady() {
            if (waitingRoom.size() >= MIN_PLAYERS) {
        String roomId = "Room-" + roomCounter++;
        gameRooms.put(roomId, new ArrayList<>(waitingRoom));
        
        for (String player : waitingRoom) {
            playerScores.put(player, 0);
        }
         roomCompletedLevels.put(roomId, new HashSet<>()); 
        waitingRoom.clear();

        StringBuilder gameStartMessage = new StringBuilder("GAME_START:" + roomId);
        List<String> playersInRoom = gameRooms.get(roomId);

        if (playersInRoom != null) {
            for (String player : playersInRoom) {
                gameStartMessage.append(":").append(player).append(",0");
            }
        }

        sendToRoom(roomId, gameStartMessage.toString());
        sendToAll("WAITING_ROOM " + String.join(", ", waitingRoom));
    }
}

        // Method to handle client disconnection
     private void disconnect() {
        if (username != null) {
            connectedPlayers.remove(username);
            waitingRoom.remove(username);
            
            String roomIdToRemoveFrom = null;
            Iterator<Map.Entry<String, List<String>>> iterator = gameRooms.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<String, List<String>> entry = iterator.next();
                if (entry.getValue().contains(username)) {
                    roomIdToRemoveFrom = entry.getKey();
                    entry.getValue().remove(username);
                    
                    // Remove player from scores
                    playerScores.remove(username);
                    
                    // Send updated player list to room
                    StringBuilder response = new StringBuilder("ROOM_PLAYERS:" + roomIdToRemoveFrom);
                    for (String p : entry.getValue()) {
                        response.append(":").append(p).append(",").append(playerScores.getOrDefault(p, 0));
                    }
                    sendToRoom(roomIdToRemoveFrom, response.toString());
                    
                    break;
                }
            }

            sendToAll("PLAYER_LIST " + String.join(", ", connectedPlayers));
            sendToAll("WAITING_ROOM " + String.join(", ", waitingRoom));
            sendToAll("PLAYER_LEFT:" + username);
        }

        clients.remove(this);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
        // Method to send a message to all connected clients
        private void sendToAll(String message) {
            // Hold the list's lock while iterating a synchronized list, otherwise a
            // concurrent connect/disconnect can trigger ConcurrentModificationException.
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.out != null) {
                        client.out.println(message); // Send the message to each client
                    }
                }
            }
        }
private void sendToRoom(String roomId, String message) {
    List<String> playersInRoom = gameRooms.get(roomId);
    if (playersInRoom != null) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.username != null && client.out != null
                        && playersInRoom.contains(client.username)) {
                    client.out.println(message);
                    client.out.flush();
                }
            }
        }
    } else {
        System.out.println("Room not found: " + roomId);
    }
}
 



    }
}
