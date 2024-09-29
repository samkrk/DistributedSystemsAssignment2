# Define variables for Java compilation and running
JAVAC=javac
JAVA=java
SRC=src
BIN=bin
TEST=tests
LIB = lib

# Compile the Java files
compile:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) $(SRC)/*.java

# Run the server
run-server:
	$(JAVA) -cp $(BIN) AggregationServer 4567

# Run the content server
run-content:
	$(JAVA) -cp $(BIN) ContentServer localhost:4567 src/content/weather0.txt

# Run the client
run-client:
	$(JAVA) -cp $(BIN) GETClient localhost:4567 IDS60901

# Compile the source code and tests
compile-tests:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) -cp "$(LIB)/junit-4.13.2.jar:$(LIB)/hamcrest-core-1.3.jar" $(SRC)/*.java $(TEST)/*.java

# Run the JUnit tests
run-tests: compile-tests
	$(JAVA) -cp "$(BIN):$(LIB)/junit-4.13.2.jar:$(LIB)/hamcrest-core-1.3.jar" org.junit.runner.JUnitCore AggregationServerTest
# Clean up the compiled files
clean:
	rm -rf $(BIN)/*.class
