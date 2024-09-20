import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

public class AggregationServer {
    public static void main(String[] args) throws IOException {
        LamportClock lamportClock = new LamportClock();  // Starts with clock = 0

        // Start file cleanup thread
        new Thread(() -> {
            while (true) {
                try {
                    // Perform cleanup every 30 seconds
                    cleanUpFiles("src/aggr_data", 30); // Clean files not accessed for 30 seconds
                    TimeUnit.SECONDS.sleep(30); // Sleep for 30 seconds before running again
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();


        int port = 4567; // Port number
        // get port number if it's there

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Aggregation Server started on port " + port);
        System.out.println("Aggregation Server name: localhost");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected");
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    // Method to clean up files not accessed within the specified time limit (in seconds)
    private static void cleanUpFiles(String folderPath, long timeLimitInSeconds) throws IOException {
        Path directory = Paths.get(folderPath);
        long thresholdTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeLimitInSeconds);

        Files.list(directory).forEach(file -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                Instant lastAccessTime = attrs.lastAccessTime().toInstant();

                if (lastAccessTime.toEpochMilli() < thresholdTime) {
                    Files.delete(file);
                    System.out.println("Deleted file: " + file.getFileName());
                }
            } catch (IOException e) {
                System.err.println("Error processing file: " + file.getFileName());
                e.printStackTrace();
            }
        });
    }

}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // int receivedClock = Integer.parseInt(receivedClockLine.split(": ")[1]);
            // lamportClock.update(receivedClock);  // Update based on received clock

            String requestType = in.readLine();
            System.out.println("Request type: " + requestType);


            // PUT REQUEST
            if (requestType != null && "PUT".equals(requestType)) {
                System.out.println("Processing Put Request ... ");

                // Read & print headers
                StringBuilder responseHeaders = readHeaders(in);
                System.out.println(responseHeaders.toString());

                // read & print json data
                String jsonString = readJson(in);
                System.out.println("Recieved JSON data: " + jsonString);

                // get weather ID
                String weatherID = getWeatherID(jsonString);
                if (weatherID == null) {
                    throw new IllegalArgumentException("ID not found in JSON data");
                }

                try {
                    // Store the data in a file named after the weather ID
                    Path filePath = Paths.get("src/aggr_data/" + weatherID + ".json");

                    // Send success response (HTTP 201 for new, HTTP 200 for update)
                    if (!Files.exists(filePath)) {
                        // File does not exist yet, so create it and return 201 Created
                        Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
                        out.println("HTTP/1.1 201 Created");
                        out.println(); // End of headers
                        out.println(jsonString);
                        out.println(); // End of message
                    } else {
                        // File already exists, update its contents and return 200 OK
                        Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
                        out.println("HTTP/1.1 200 OK");
                        out.println(); // End of headers
                        out.println(jsonString);
                        out.println(); // End of message
                    }
                } catch (Exception e) {
                    // Handle JSON parsing error or invalid format
                    System.out.println("Invalid JSON format");
                    out.println("HTTP/1.1 500 Internal Server Error");
                }
                // Handle Lamport clock logic here
            }
            // GET REQUEST
            else if (requestType != null && "GET".equals(requestType)) {
                System.out.println("Processing GET request now ... ");;
                // if no ID specified, return latest data
                String id = in.readLine();
                System.out.println("ID: " + id);
                if (id == null || id.trim().isEmpty()){ // Send all weather data?
                    System.out.println("ID is empty");
                    // Retrieve stored JSON data
                    String jsonResponse = "{ \"weather\": \"Sunny\" }"; // Example response
                    out.println(jsonResponse);
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
                        out.println(); // End of headers
                        out.println(jsonResponse); // Send the error message
                        out.println(); // End of message
                    } else {
                        // Read the contents of the JSON file
                        jsonResponse = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                        System.out.println("Sending JSON Data:" + jsonResponse);
                        // Send the JSON data to the client
                        out.println("HTTP/1.1 200 OK");
                        out.println("Content-Type: application/json");
                        out.println("Content-Length: " + jsonResponse.length());
                        out.println(); // End of headers
                        out.println(jsonResponse); // Send the JSON data
                        out.println(); // End of message
                    }
                }
            }
            // INVALID REQUEST
            else {
                out.println("HTTP/1.1 400 Invalid request type");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Read headers function
    private StringBuilder readHeaders(BufferedReader in) throws IOException {
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        // Read headers line by line until an empty line is encountered
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        return responseHeaders;
    }

    // Read json file function
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

    // function to get weather_id
    private String getWeatherID(String jsonString){
        String weatherID = null;
        // Extract the weather ID from the JSON string
        int idIndex = jsonString.indexOf("\"id\":");
        if (idIndex != -1) {
            int startIndex = jsonString.indexOf("\"", idIndex + 5) + 1; // Skip past "id": "
            int endIndex = jsonString.indexOf("\"", startIndex); // Find the closing quote
            if (startIndex != -1 && endIndex != -1) {
                weatherID = jsonString.substring(startIndex, endIndex); // Extract the ID value
            }
        }
        return weatherID;
    }


}



