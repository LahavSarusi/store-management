package com.store.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A scripted stand-in for the store server: accepts one client on a random port and lets the test answer it. */
final class FakeServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final BlockingQueue<JsonObject> requests = new LinkedBlockingQueue<>();
    private volatile Socket client;
    private volatile BufferedWriter out;

    FakeServer() throws IOException {
        serverSocket = new ServerSocket(0);
        Thread t = new Thread(this::acceptAndRead, "fake-server");
        t.setDaemon(true);
        t.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    /** The next request the client sent. */
    JsonObject nextRequest() throws InterruptedException {
        JsonObject r = requests.poll(5, TimeUnit.SECONDS);
        if (r == null) {
            throw new AssertionError("The client sent no request");
        }
        return r;
    }

    void sendLine(String json) throws IOException, InterruptedException {
        waitForClient();
        synchronized (this) {
            out.write(json);
            out.write('\n');
            out.flush();
        }
    }

    void ok(String requestId, String payloadJson) throws IOException, InterruptedException {
        sendLine("{\"kind\":\"RESPONSE\",\"requestId\":\"" + requestId + "\",\"status\":\"OK\""
                + (payloadJson == null ? "" : ",\"payload\":" + payloadJson) + "}");
    }

    void error(String requestId, String code, String message) throws IOException, InterruptedException {
        String id = requestId == null ? "null" : "\"" + requestId + "\"";
        sendLine("{\"kind\":\"RESPONSE\",\"requestId\":" + id + ",\"status\":\"ERROR\","
                + "\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
    }

    void event(String event, String payloadJson) throws IOException, InterruptedException {
        sendLine("{\"kind\":\"EVENT\",\"event\":\"" + event + "\",\"payload\":" + payloadJson
                + ",\"timestamp\":\"2026-10-30T10:00:00Z\"}");
    }

    /** Drops the client connection, as the server does on FORCED_LOGOUT or shutdown. */
    void dropClient() throws IOException, InterruptedException {
        waitForClient();
        client.close();
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
        serverSocket.close();
    }

    private void acceptAndRead() {
        try {
            Socket s = serverSocket.accept();
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            client = s;
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                requests.add(JsonParser.parseString(line).getAsJsonObject());
            }
        } catch (IOException ignored) {
            // closed by the test
        }
    }

    private void waitForClient() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (client == null) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("No client connected");
            }
            Thread.sleep(10);
        }
    }
}
