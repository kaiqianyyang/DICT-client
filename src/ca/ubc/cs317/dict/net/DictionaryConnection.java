package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    public Socket socket;
    public BufferedReader input;
    public PrintWriter output;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        // resource: https://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html
        // resource: https://blog.csdn.net/earbao/article/details/17588325
        // resource: https://www.geekality.net/2013/04/30/java-simple-check-to-see-if-a-server-is-listening-on-a-port/
        // TODO Add your code here
        try {
            // check if host is valid
            if (!isHostnameValid(host)) {  // Test[6] PASSED
                throw new DictConnectionException("Invalid host.");
            }
            // client connects to a DICT server
            socket = new Socket(host, port);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            Status status = Status.readStatus(input);
            // check status code
            if (status.getStatusCode() != 220) {
                throw new DictConnectionException("Failed to connect to a client's IP address: " + status.getDetails());
            }
        } catch (IOException e) {
            throw new DictConnectionException("Invalid port.");
        }

    }

    // Todo: Check host validity DONE
    // resource: https://stackoverflow.com/questions/3114595/java-regex-for-accepting-a-valid-hostname-ipv4-or-ipv6-address
    public boolean isHostnameValid(String hostname) {
        try {
            InetAddress.getAllByName(hostname); // throws an error when the hostnme could not be found, if so, then return false
            return true;
        } catch (Exception exc) {
            return false;
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    // v1 only fail one test
    public synchronized void close() {
        // TODO Add your code here
        // resource: https://www.tabnine.com/code/java/methods/java.io.BufferedWriter/write Test [8] ?
        try {
            // send QUIT command
            String command = "QUIT";
            output.println(command);
            // receive reply
            String response = input.readLine();
            // check status code, and close socket and streams
            if (response.startsWith("221")) {
                socket.close();
                input.close();
                output.close(); // closes the connection with the server
            }
        } catch (Exception ignored) {

        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        // TODO Add your code here
        String databaseName = database.getName();
        String command = "DEFINE " + databaseName + " \"" + word + "\"\r\n"; // command: DEFINE jargon PEBKAC
        output.println(command);
        // check initial status - responses
        Status initStatus = Status.readStatus(input);
        int initStatusCode = initStatus.getStatusCode();
        switch (initStatusCode) {
            case 550: // Invalid Database
            case 552: // No match
                break;
            case 150:
                // convert initial status message, and get the number of retrieved definitions
                String initDetail = initStatus.getDetails();
                String[] initDetails = DictStringParser.splitAtoms(initDetail); // [n, definitions, retrieved]
                int numOfDefinitions = Integer.parseInt(initDetails[0]); // n
                // list all the definitions we get
                for (int i = 0; i < numOfDefinitions; i++) {
                    // check each definition status
                    Status currStatus = Status.readStatus(input);
                    int currStatusCode = currStatus.getStatusCode();
                    // check if each definition status code is 151, otherwise throw error
                    if (currStatusCode != 151) {
                        throw new DictConnectionException("Response bad.");
                    }
                    String currDetail = currStatus.getDetails();
                    String[] currDetails = DictStringParser.splitAtoms(currDetail);
                    Definition definition = new Definition(currDetails[0], currDetails[1]);
                    while (true) {
                        try {
                            String curDefinition = input.readLine();
                            if (curDefinition.equals(".")) {
                                break;
                            }
                            definition.appendDefinition(curDefinition);
                        } catch (IOException e) {
                            throw new DictConnectionException("Bad msg.");
                        }
                    }
                    set.add(definition);
                }
                // check final status code is 250
                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();
                if (endStatusCode != 250) {
                    throw new DictConnectionException("Not 250.");
                }
                break;
        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        String command = "MATCH " + database.getName() + " " + strategy.getName() + " \"" + word + "\"\r\n"; // MATCH database strategy word
        output.println(command);
        // check initial status - responses
        Status initStatus = Status.readStatus(input);
        int initStatusCode = initStatus.getStatusCode();
        switch (initStatusCode) {
            case 550: // Invalid Database
            case 551: // Invalid strategy
            case 552: // No match
                break;
            case 152:
                while (true) {
                    try {
                        String curDetail = input.readLine();
                        String[] curDetails = DictStringParser.splitAtoms(curDetail);
                        if (curDetail.equals(".")) {
                            break;
                        }
                        set.add(curDetails[1]);
                    } catch (IOException e) {
                        throw new DictConnectionException("Bad msg.");
                    }
                }
                // check final status code is 250
                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();
                if (endStatusCode != 250) {
                    throw new DictConnectionException("Not 250 ok.");
                }
                break;
        }
        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        // TODO Add your code here
        String command = "SHOW DB\r\n";
        output.println(command);
        // check initial status - responses
        Status initStatus = Status.readStatus(input);
        int initStatusCode = initStatus.getStatusCode();
        switch (initStatusCode) {
            case 110:
                while (true) {
                    try {
                        String detail = input.readLine();
                        if (detail.equals(".")) {
                            break;
                        }
                        String[] details = DictStringParser.splitAtoms(detail);
                        Database database = new Database(details[0], details[1]);
                        String databaseName = database.getName();
                        databaseMap.put(databaseName, database);
                    } catch (IOException e) {
                        throw new DictConnectionException("Bad msg.");
                    }
                }
                // check final status code is 250
                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();
                if (endStatusCode != 250) {
                    throw new DictConnectionException("Not 250 ok.");
                }
                break;
            case 554: // Invalid Database
                break;
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        String command = "SHOW STRAT\r\n"; // command: DEFINE jargon PEBKAC
        output.println(command);
        // check initial status - responses
        Status initStatus = Status.readStatus(input);
        int initStatusCode = initStatus.getStatusCode();
        switch (initStatusCode) {
            case 555: // No strategies available
                break;
            case 111: // n strategies available - text follows
                while (true) {
                    try {
                        String currDetail = input.readLine();
                        String[] currDetails = DictStringParser.splitAtoms(currDetail);
                        if (currDetail.equals(".")) {
                            break;
                        }
                        MatchingStrategy strategy = new MatchingStrategy(currDetails[0], currDetails[1]);
                        set.add(strategy);
                    } catch (IOException e) {
                        throw new DictConnectionException("Bad msg.");
                    }
                }
                // check final status code is 250
                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();
                if (endStatusCode != 250) {
                    throw new DictConnectionException("Not 250 ok.");
                }
                break;
            default:
                throw new DictConnectionException("The connection was interrupted or the messages don't match their expected value.");
        }
        return set;
    }
}