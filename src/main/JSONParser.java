package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JSONParser {

    // Method to read the file and convert it to JSON string
    public static String convertFileToJSON(String fileName) {
        // Read the file content
        String input = readFile(fileName);

        if (input == null) {
            return null;  // Return null if file reading fails
        }

        // Parse the input into a Map
        Map<String, String> weatherData = parseInput(input);

        // Convert the Map to a JSON string (manually)
        return convertToJSON(weatherData);
    }

    // Function to read the file and return its contents as a single string
    private static String readFile(String fileName) {
        StringBuilder contentBuilder = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String currentLine;

            while ((currentLine = br.readLine()) != null) {
                contentBuilder.append(currentLine).append("\n");
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return null;
        }

        return contentBuilder.toString();
    }

    // Parse the input and store it in a Map
    private static Map<String, String> parseInput(String input) {
        Map<String, String> map = new HashMap<>();
        String[] lines = input.split("\n");

        for (String line : lines) {
            String[] keyValue = line.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }

    // Convert the parsed data to a JSON string
    private static String convertToJSON(Map<String, String> data) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");

        int count = 0;
        int size = data.size();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            jsonBuilder.append("  \"")
                    .append(entry.getKey())
                    .append("\": \"")
                    .append(entry.getValue())
                    .append("\"");

            // Only append a comma and newline if it's not the last entry
            count++;
            if (count < size) {
                jsonBuilder.append(",\n");
            } else {
                jsonBuilder.append("\n");
            }
        }

        jsonBuilder.append("}");

        return jsonBuilder.toString();
    }

}