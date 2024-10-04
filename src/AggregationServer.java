import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.*;

public class AggregationServer {
    private static volatile boolean running = false; // Flag to control server running state
    public static LamportClock lamportClock;
    private static ServerSocket serverSocket;
    public static int port;
    public static final Map<String, Long> lastContactMap = new ConcurrentHashMap<>();
    public static final long INACTIVITY_THRESHOLD = 30000; // 30 seconds


    public static void main(String[] args){
        startUp(args);
        startFileCleanupThread(); // start clean up
        running = true;
        listen(serverSocket);
    }

    public static void startUp(String[] args){
        System.out.println("Server is starting up...");
        lamportClock = new LamportClock();  // Starts with clock = 0
        port = getPortNumber(args); // get port number from input
        startShutdownListener();
        startSocket(port); // start socket on given port number
    }

    public static int getPortNumber(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]); // Return the port number from command line
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number provided, using default: 4567");
            }
        }
        return 4567; // Default port number
    }

    public static int getPort(){
        return port;
    }

    public static void cleanUpFiles(long timeLimitInSeconds) throws IOException {
        Path directory = Paths.get("src/aggr_data");
        long thresholdTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeLimitInSeconds);

        Files.list(directory).forEach(file -> {
            String fileName = file.getFileName().toString();
            String fileId = fileName.substring(0, fileName.indexOf('.')); // Assuming fileId is part of the file name

            Long lastContactTime = lastContactMap.get(fileId);
            if (lastContactTime == null || lastContactTime < thresholdTime) {
                // If no heartbeat has been received within the time limit, delete the file
                try {
                    Files.delete(file);
                    System.out.println("Deleted inactive server file: " + file.getFileName());
                    lastContactMap.remove(fileId); // Also remove the file from the map
                } catch (IOException e) {
                    System.err.println("Error deleting file: " + file.getFileName());
                    e.printStackTrace();
                }
            }
        });
    }

    public static void startFileCleanupThread() {
        new Thread(() -> {
            while (running) {
                try {
                    cleanUpFiles(30); // Clean up files not accessed for 30 seconds
                    TimeUnit.SECONDS.sleep(30); // Sleep for 30 seconds before running again
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void startSocket(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Aggregation Server started on port " + port);
        } catch (IOException e) {
            System.out.println("Error while creating server socket on port " + port);
            e.printStackTrace();
        }
    }

    public static void listen(ServerSocket serverSocket) {
        if (serverSocket == null) {
            return; // Don't proceed if serverSocket wasn't created
        }

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // System.out.println("New client connected");
                new Thread(new ClientHandler(clientSocket)).start();
            } catch (IOException e) {
                if (!running) {
                    break; // Exit the loop if server is shutting down
                }
                e.printStackTrace();
            }
        }
    }

    private static void startShutdownListener() {
        // Start a new thread to listen for shutdown command
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("shutdown")) {
                    running = false;
                    System.out.println("Shutting down the server...");
                }
            }
            scanner.close();
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close(); // Close the server socket
                }
                System.out.println("Server has been shut down.");
            } catch (IOException e) {
                System.out.println("Error while shutting down the server.");
                e.printStackTrace();
            }
        }).start();
    }

    public static void shutdown() {
        running = false; // Stop the server loop
        System.out.println("Shutting down the server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Close the server socket
            }
            System.out.println("Server has been shut down.");
        } catch (IOException e) {
            System.out.println("Error while shutting down the server.");
            e.printStackTrace();
        }
    }

    public static int getClock(){
        return lamportClock.getClock();
    }


} // Aggregation Server


class ClientHandler extends AggregationServer implements Runnable{
    private final Socket clientSocket;
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            // Increment clock on request receipt
            AggregationServer.lamportClock.increment();

            // get request type
            String requestType = in.readLine();

            if (requestType.equals("PUT")) {
                System.out.println("Request type: PUT");
                processPut(in, out);
            } else if (requestType.equals("GET")) {
                System.out.println("Request type: GET");
                processGet(in, out);
            } else if (requestType.equals("HEARTBEAT")) {
                processHeartbeat(in, out);
            } else {
                out.println("HTTP/1.1 400 Invalid request type");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void processPut(BufferedReader in, PrintWriter out) throws IOException {
        // Read lamport clock value from content server
        String receivedClockString = in.readLine();
        int receivedClock = Integer.parseInt(receivedClockString);
        AggregationServer.lamportClock.update(receivedClock);

        // Read & print headers
        StringBuilder responseHeaders = readHeaders(in);
        System.out.println(responseHeaders);

        // read & print json data
        String jsonString = readJson(in);
        System.out.println("Received JSON data: " + jsonString);
        System.out.println("Lamport Clock before processing PUT: " + AggregationServer.lamportClock.getClock());

        // if json data is empty
        if (jsonString.isEmpty()){
            System.out.println("Empty JSON message");
            out.println("HTTP/1.1 204 No Content");
            return;
        }

        // get weather ID
        String weatherID = getWeatherID(jsonString);
        if (weatherID == null) {
            throw new IllegalArgumentException("ID not found in JSON data");
        }

        try {
            // Store the data in a file named after the weather ID
            Path filePath = Paths.get("src/aggr_data/" + weatherID + ".json");

            long timestamp = System.currentTimeMillis(); // Use current time as the timestamp
            lastContactMap.put(weatherID, timestamp);

            // Send success response (HTTP 201 for new, HTTP 200 for update)
            if (!Files.exists(filePath)) {
                // Create the file and write content
                Files.writeString(filePath, jsonString, StandardOpenOption.CREATE);
                out.println("HTTP/1.1 201 Created");
            } else {
                // File already exists, update its content
                Files.writeString(filePath, jsonString, StandardOpenOption.TRUNCATE_EXISTING);
                out.println("HTTP/1.1 200 OK");
            }

            out.println(); // End of headers
            out.println(jsonString); // Send back the JSON data
            out.flush();  // Ensure all the output is flushed
        } catch (Exception e) {
            // Handle JSON parsing error or invalid format
            System.out.println("Invalid JSON format");
            out.println("HTTP/1.1 500 Internal Server Error");
        }
        AggregationServer.lamportClock.increment(); // Increment clock after processing PUT
    }

    public void processGet(BufferedReader in, PrintWriter out) throws IOException {
        // Read lamport clock value from content server
        String receivedClockString = in.readLine();
        int receivedClock = Integer.parseInt(receivedClockString);
        AggregationServer.lamportClock.update(receivedClock);

        // if no ID specified, return latest data
        String id = in.readLine();
        System.out.println("ID: " + id);
        System.out.println("Lamport Clock before processing GET: " + AggregationServer.lamportClock.getClock());

        if (id.equals("MOST_RECENT")){ // Send the most recently updated weather file
            String most_recent_file = getMostRecentFileId();
            Path filePath = Paths.get("src/aggr_data/" + most_recent_file + ".json");
            System.out.println(most_recent_file);

            String jsonResponse;

            if (most_recent_file.equals("empty") || !Files.exists(filePath)) {
                // If the aggregation server is empty, send a 404 Not Found response
                jsonResponse = "{\"error\": \"No data in aggregation server\"}";
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: application/json");

            } else {
                jsonResponse = Files.readString(filePath);
                System.out.println("Sending JSON Data associated with ID :" + id);
                // Send the JSON data to the client
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonResponse.length());
                out.println("No ID Specified. Sending Most Recent Data");
            }
            out.println(); // End of headers
            out.println(jsonResponse);
            out.println(); // End of message
        } else {
            // Retrieve stored JSON data WITH ID
            System.out.println("Searching for ID: " + id);

            Path filePath = Paths.get("src/aggr_data/" + id + ".json");
            String jsonResponse;

            if (!Files.exists(filePath)){ // if we cant find this file
                // If the file does not exist, send a 404 Not Found response
                jsonResponse = "{\"error\": \"Not Found\"}";
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: application/json");
            } else {
                // Read the contents of the JSON file
                jsonResponse = Files.readString(filePath);
                System.out.println("Sending JSON Data associated with ID :" + id);
                // Send the JSON data to the client
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonResponse.length());
            }
            out.println(); // End of headers
            out.println(jsonResponse);
            out.println(); // End of message
        }
        AggregationServer.lamportClock.increment(); // Increment clock after processing GET
    }

    public static String getMostRecentFileId() {
        if (lastContactMap.isEmpty()){
            System.out.println("No files currently in aggregation server");
            return "empty";
        }
        return lastContactMap.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue()) // Get the entry with the highest contact time (most recent)
                .map(Map.Entry::getKey) // Extract the key (fileId)
                .orElse("empty"); // Return empty if the map is empty
    }

    private void processHeartbeat(BufferedReader in, PrintWriter out) throws IOException {
        String filePath = in.readLine(); // Read the attached file ID
        int startIndex = filePath.lastIndexOf('/') + 1; // Start after the last '/'
        int endIndex = filePath.lastIndexOf('.'); // End before the '.txt'
        String fileId = filePath.substring(startIndex, endIndex);

        long timestamp = System.currentTimeMillis(); // Use current time as the timestamp

        lastContactMap.put(fileId, timestamp);

        // Respond with acknowledgment
        out.println("HTTP/1.1 200 OK");
        System.out.println("Received heartbeat from " + fileId);
    }

    private StringBuilder readHeaders(BufferedReader in) throws IOException {
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        // Read headers line by line until an empty line is encountered
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        return responseHeaders;
    }

    private String readJson(BufferedReader in) throws IOException {
        StringBuilder jsonData = new StringBuilder();
        String line;
        // Read the entire JSON data from the BufferedReader
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            jsonData.append(line).append("\n"); // Append each line to the JSON data
        }

        // Convert the JSON data to a single string
        return jsonData.toString();
    }

    private String getWeatherID(String jsonString){
        String weatherID = null;
        // Extract the weather ID from the JSON string
        int idIndex = jsonString.indexOf("\"id\":");
        if (idIndex != -1) {
            int startIndex = jsonString.indexOf("\"", idIndex + 5) + 1; // Skip past "id": "
            int endIndex = jsonString.indexOf("\"", startIndex); // Find the closing quote
            if (endIndex != -1) {
                weatherID = jsonString.substring(startIndex, endIndex); // Extract the ID value
            }
        }
        return weatherID;
    }
}

