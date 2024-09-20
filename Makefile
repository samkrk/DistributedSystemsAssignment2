# Define variables for Java compilation and running
JAVAC=javac
JAVA=java
SRC=src
BIN=bin
TEST=tests

# Compile the Java files
compile:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) $(SRC)/*.java

# Compile the JUnit tests
compile-tests:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) -cp "lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar" $(TEST)/*.java

# Run the server
run-server:
	$(JAVA) -cp $(BIN) AggregationServer 4567

# Run the content server
run-content:
	$(JAVA) -cp $(BIN) ContentServer localhost src/content/weather0.txt

# Run the client
run-client:
	$(JAVA) -cp $(BIN) GETClient localhost:4567 IDS60901

# Run the JUnit tests
run-tests: compile-tests
	$(JAVA) -cp "$(BIN):lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar" org.junit.runner.JUnitCore tests.AggregationServerTest

# Clean up the compiled files
clean:
	rm -rf $(BIN)/*.class
