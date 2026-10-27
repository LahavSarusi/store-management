package com.store.server.net;

import com.store.server.protocol.ErrorCode;
import com.store.server.protocol.JsonCodec;
import com.store.server.protocol.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP accept loop. Each connection gets a {@link ClientHandler} on its own thread; connections beyond
 * {@code maxClients} are refused with SERVER_BUSY instead of silently waiting in a queue.
 */
public final class StoreServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(StoreServer.class.getName());

    private final int requestedPort;
    private final int maxClients;
    private final RequestDispatcher dispatcher;
    private final SessionManager sessions;
    private final SessionLifecycle lifecycle;
    private final AtomicInteger activeClients = new AtomicInteger();
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-" + threadSeq.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;

    public StoreServer(int port, int maxClients, RequestDispatcher dispatcher,
                       SessionManager sessions, SessionLifecycle lifecycle) {
        this.requestedPort = port;
        this.maxClients = maxClients;
        this.dispatcher = dispatcher;
        this.sessions = sessions;
        this.lifecycle = lifecycle;
    }

    /** Binds the listening socket. Port 0 picks a free port (useful in tests). @return the bound port */
    public int open() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        running = true;
        LOG.info("Store server listening on port " + serverSocket.getLocalPort());
        return serverSocket.getLocalPort();
    }

    /** Accepts clients until {@link #close()}. Blocks the calling thread. */
    public void serve() {
        while (running) {
            try {
                accept(serverSocket.accept());
            } catch (SocketException e) {
                if (running) {
                    LOG.log(Level.WARNING, "Accept failed", e);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Accept failed", e);
            }
        }
    }

    private void accept(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        if (activeClients.incrementAndGet() > maxClients) {
            activeClients.decrementAndGet();
            refuse(socket);
            return;
        }
        try {
            pool.execute(new ClientHandler(socket, dispatcher, sessions, lifecycle, activeClients::decrementAndGet));
        } catch (IOException | RuntimeException e) {
            activeClients.decrementAndGet();
            socket.close();
            throw e;
        }
    }

    private static void refuse(Socket socket) {
        try (socket; OutputStream out = socket.getOutputStream()) {
            String busy = JsonCodec.encode(Response.error(null, ErrorCode.SERVER_BUSY, "Server is full, try again later"));
            out.write((busy + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // client went away
        }
    }

    public int activeClients() {
        return activeClients.get();
    }

    @Override
    public void close() {
        running = false;
        sessions.disconnectAll("Server is shutting down");
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        pool.shutdownNow();   // handlers block in readLine; closing sockets above unblocks them
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
