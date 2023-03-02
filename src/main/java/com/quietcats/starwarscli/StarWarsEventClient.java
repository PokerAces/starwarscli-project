package com.quietcats.starwarscli;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.*;

/**
 * Represents the Star Wars event client.
 */
public class StarWarsEventClient {
    private final Socket socket;
    private String connectionId;

    public StarWarsEventClient(final String uri) throws URISyntaxException {
        this.socket = IO.socket(uri);
    }

    public void connect() {
        CountDownLatch connectLatch = new CountDownLatch(1);
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                StarWarsEventClient.this.connectionId = socket.id();
                System.out.println("Connected (ID: "+socket.id()+")");
                connectLatch.countDown();
            }
        });
        this.socket.connect();
        // Wait until the latch it complete.
        try {
            connectLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        if(this.socket.connected()) {
            CountDownLatch disconnectLatch = new CountDownLatch(1);
            socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    System.out.println("Disconnected (ID: "+connectionId+")");
                    disconnectLatch.countDown();
                }
            });
            this.socket.disconnect();
            try {
                disconnectLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Issues a search request.
     *
     * @param searchVal     text to search for
     * @param responseLatch latch that blocks user input until the response is complete
     */
    public void search(String searchVal, final CountDownLatch responseLatch) {
        JSONObject json = new JSONObject();
        try {
            json.put("query", searchVal);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // Add the listener for this search
        var listener = new SearchListener(responseLatch, this.socket);
        this.socket.on("search", listener);
        this.socket.emit("search", json);
    }

    /**
     * Handles incoming search events and the coordination of response completions.
     */
    static class SearchListener implements Emitter.Listener {
        private final CountDownLatch responseCompletionLatch;
        private final Socket parentSocket;

        public SearchListener(CountDownLatch responseCompletionLatch, Socket parentSocket) {
            this.responseCompletionLatch = responseCompletionLatch;
            this.parentSocket = parentSocket;
        }
        @Override
        public void call(Object... args) {
            JSONObject result = (JSONObject)args[0];
            boolean isError = false;
            int page = -1;
            int resultCount = -1;
            try {
                page = result.getInt("page");
                resultCount = result.getInt("resultCount");
                isError = (page < 0);
                if(isError) {
                    System.out.println("ERROR: "+result.getString("error"));
                } else {
                    var name = result.getString("name");
                    var films = result.getString("films");
                    System.out.println(page+"/"+resultCount+": "+name+" - "+films);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } finally {
                // Return the control to the user if the result is:
                // - an error
                // - the page is at the total result count
                if(isError || (page == resultCount)) {
                    parentSocket.off("search", this);
                    responseCompletionLatch.countDown();
                }
            }
        }
    }
}
