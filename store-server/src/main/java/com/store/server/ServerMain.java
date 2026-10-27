package com.store.server;

import com.store.server.net.StoreServer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point.
 * <pre>java -jar store-server.jar [port=5050] [baseDir=.] [maxClients=100]</pre>
 */
public final class ServerMain {

    private static final int DEFAULT_PORT = 5050;
    private static final int DEFAULT_MAX_CLIENTS = 100;

    private ServerMain() { }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path baseDir = Path.of(args.length > 1 ? args[1] : ".");
        int maxClients = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MAX_CLIENTS;

        ServerContext context = new ServerContext(baseDir);
        context.seedIfEmpty();
        StoreServer server = context.createServer(port, maxClients);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            context.close();
        }, "shutdown"));

        server.open();
        server.serve();
    }
}
