# Define variables for Java compilation and running
JAVAC=javac
JAVA=java
SRC=src
BIN=bin
TEST=tests
LIB=lib

# Compile the Java files
compile:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) $(SRC)/*.java

# Run the aggregation server with an optional port argument (default: 4567)
run-server:
	$(JAVA) -cp $(BIN) AggregationServer $(PORT)

# Run the content server with arguments for server address, port, and directory
# Example: make run-content SERVER_ADDR=localhost PORT=4567 DIR=src/content/IDS60901.txt
# run-content:
	# $(JAVA) -cp $(BIN) ContentServer $(SERVER_ADDR):$(PORT) $(DIR)

run-content:
	$(JAVA) -cp $(BIN) ContentServer localhost:4567 src/content/IDS60901.txt

# Run the GET client with server address, port, and optional ID
# Example: make run-client SERVER_ADDR=localhost PORT=4567 ID=IDS60901
# run-client:
#	$(JAVA) -cp $(BIN) GETClient $(SERVER_ADDR):$(PORT) $(ID)

run-client:
	$(JAVA) -cp $(BIN) GETClient localhost:4567 IDS60901

# Compile the source code and tests
compile-tests:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) -cp "$(LIB)/junit-4.13.2.jar:$(LIB)/hamcrest-core-1.3.jar" $(SRC)/*.java $(TEST)/*.java

# Run the JUnit tests
run-tests: compile-tests
	$(JAVA) -cp "$(BIN):$(LIB)/junit-4.13.2.jar:$(LIB)/hamcrest-core-1.3.jar" org.junit.runner.JUnitCore IntegrationTests

# Clean up the compiled files
clean:
	rm -rf $(BIN)/*.class


### HARD CODED EXAMPLES FOR EASE OF USE ###
_run-content:
	$(JAVA) -cp $(BIN) ContentServer localhost:4567 src/content/IDS60901.txt

_run-client:
	$(JAVA) -cp $(BIN) GETClient localhost:4567 IDS60901

_run-content2:
	$(JAVA) -cp $(BIN) ContentServer localhost:4567 src/content/IDS60902.txt
