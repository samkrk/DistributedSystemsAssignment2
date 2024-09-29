import java.io.*;
import java.net.*;
/*
public class test {
    private static LamportClock lamportClock;
    public static String serverName = "localhost"; // Aggregation server address
    public static int port = 4567; // Aggregation server port
    public static String fileID;

    public static void main(String[] args) throws IOException {
        lamportClock = new LamportClock();

        // get servername and port number from input
        initVariables(args);

        // initialise socket
        Socket socket = new Socket(serverName, port);

        // get data
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            getData(in,out);
        } catch (IOException e) {
            System.out.println("Error in socket");
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    private static void initVariables(String[] args)throws IOException{
        int no_id_flag = 0;
        if (args.length < 2) {
            if (args.length < 1){
                System.out.println("Usage: <servername>:<port> <file>");
                return;
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
        // Send GET request
        out.println("GET");
        // Send ID only if it exists
        out.println(fileID); // either valid ID or MOST_RECENT

        // Read response headers
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }

        // Print headers (optional)
        // System.out.println("Received Headers: \n" + responseHeaders.toString());

        StringBuilder responseBody = new StringBuilder();
        char[] buffer = new char[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            responseBody.append(buffer, 0, bytesRead);
        }

        // Print body
        System.out.println(responseBody.toString());
    }

}

 */

