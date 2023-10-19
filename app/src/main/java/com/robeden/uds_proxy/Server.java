package com.robeden.uds_proxy;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;

class Server implements Runnable {
    private final AFUNIXServerSocket server;
    private final AFUNIXSocketAddress destination;

    private Server(AFUNIXServerSocket server, Path destination) throws SocketException {
        this.server = server;
        this.destination = AFUNIXSocketAddress.of(destination);
    }

    @Override
    public void run() {
        while(true) {
            Socket socket_in;
            try {
                socket_in = server.accept();
            }
            catch (IOException e) {
                System.out.println("Server stopping: " + e.getMessage());
                return;
            }

            AFUNIXSocket socket_out;
            Copier copier1 = null;
            Copier copier2 = null;
            try {
                socket_out = AFUNIXSocket.connectTo(destination);
                copier1 = new Copier(socket_out, socket_in, "|<-- " + socket_in);
                copier2 = new Copier(socket_in, socket_out, "|--> " + socket_in);
            }
            catch (IOException e) {
                Copier.closeBlindly(copier1);
                Copier.closeBlindly(copier2);
                System.out.println("Error connecting " + socket_in + ": " + e.getMessage());
            }

            Thread.ofVirtual().start(copier1);
            Thread.ofVirtual().start(copier2);
        }
    }


    static Thread start(AFUNIXServerSocket server, Path destination) throws Exception {
        return Thread.ofVirtual().name("Server").start(new Server(server, destination));
    }
}
