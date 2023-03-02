package com.quietcats.starwarscli;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

/**
 * The Star Wars search CLI
 */
public class CLI {
    public static void main(String[] args) {
        new CLI().startup();
    }

    /**
     * Starts the CLI
     */
    public void startup() {
        System.out.println("Welcome to the Star Wars CLI");
        StarWarsEventClient client = null;
        try {
            client = new StarWarsEventClient("http://localhost:3000");
            client.connect();
            runInputLoop(client);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            if(client != null) {
                client.disconnect();
            }
        }
    }

    /**
     * Runs the input loop
     * @param client    established socket.io client
     */
    private void runInputLoop(StarWarsEventClient client) {
        boolean isQuit = false;
        CountDownLatch responseLatch = null;
        while(!isQuit) {
            // Wait until complete response before giving control back to the user.
            if(responseLatch != null) {
                try {
                    // Wait until the response is complete. Then reset.
                    responseLatch.await();
                    responseLatch = null;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.print("Enter name to search or \"quit\" > ");
            Scanner sc = new Scanner(System.in);
            var line = sc.nextLine();
            if (line.equalsIgnoreCase("quit")) {
                isQuit = true;
                client.disconnect();
            } else {
                System.out.println("Searching for: [" + line + "]");
                // Establish the latch to wait until the response is complete before prompting again.
                responseLatch = new CountDownLatch(1);
                client.search(line, responseLatch);
            }
        }
    }
}
