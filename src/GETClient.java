import java.io.*;
import java.net.*;

public class GETClient {
    private static LamportClock lamportClock;
    public static String serverName = "localhost"; // Aggregation server address
    public static int port = 4567; // Aggregation server port
    public static String fileID;
    public static String receivedData = "EMPTY";

    public static void main(String[] args) throws IOException {
        lamportClock = new LamportClock();

        // get servername and port number from input
        initVariables(args);

        int maxTries = 3;  // Max number of retry attempts
        int attempts = 0;  // Track the number of attempts
        boolean success = false;  // Track whether the operation succeeded

        // Retry loop for getting data
        while (attempts < maxTries && !success) {
            attempts++;  // Increment attempt count

            try (Socket socket = new Socket(serverName, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Attempt to get data from the server
                getData(in, out);

                // Check if getting data succeeded (you can include more checks here)
                success = true;  // If no exception, assume success

            } catch (IOException e) {
                System.out.println("Error in socket, attempt " + attempts);
                e.printStackTrace();

                if (attempts >= maxTries) {
                    System.out.println("Max retries reached. Unable to get data.");
                    return;  // Exit or handle failure
                } else {
                    System.out.println("Retrying...");
                    // Optionally wait before retrying
                    try {
                        Thread.sleep(1000);  // Wait 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();  // Restore interrupt status
                    }
                }
            }
        }
        System.out.println("Data retrieved successfully.");
    }

    private static void initVariables(String[] args)throws IOException{
        int no_id_flag = 0;
        if (args.length < 2) {
            if (args.length < 1){
                System.out.println("Usage: <servername>:<port> <file>");
                throw new IOException();
            }
            else { // if client does not specify file id, send the most recent data
                no_id_flag = 1;
            }
        }

        // Get the first argument and split by ':'
        String serverAndPort = args[0];
        String[] parts = serverAndPort.split(":");

        if (parts.length != 2) {
            System.out.println("Invalid format for server and port. Expected format: <servername>:<port>");
            return;
        }

        // Extract the server name and port
        serverName = parts[0];
        port = Integer.parseInt(parts[1]);
        if (no_id_flag == 0) {
            fileID = args[1];
        }
        else {
            fileID = "MOST_RECENT";
        }

        // Print to verify the extracted values
        System.out.println("Server Name: " + serverName);
        System.out.println("Port: " + port);
        System.out.println("File: " + fileID);
    }

    private static void getData(BufferedReader in, PrintWriter out) throws IOException {
        // update clock
        lamportClock.increment();

        // Send GET request, lamport clock value, and id
        out.println("GET");
        out.println(lamportClock.getClock());
        out.println(fileID); // either valid ID or MOST_RECENT

        // Read response headers
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }

        // Print headers (optional)
        System.out.println("Received Headers: \n" + responseHeaders);

        StringBuilder responseBody = new StringBuilder();
        char[] buffer = new char[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            responseBody.append(buffer, 0, bytesRead);
        }

        receivedData = responseBody.toString();
        System.out.println(receivedData);
    }

}

