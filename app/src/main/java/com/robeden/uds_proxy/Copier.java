package com.robeden.uds_proxy;

import okio.*;

import java.io.*;
import java.net.Socket;

class Copier implements Runnable, Closeable {
    private final Socket src_socket, dest_socket;
    private final String header;
    private final InputStream src;
    private final OutputStream dest;


    Copier(Socket src, Socket dest, String header) throws IOException {
        this.src_socket = src;
        this.dest_socket = dest;
        this.header = header;
        this.src = src.getInputStream();
        this.dest = dest.getOutputStream();
    }

    @Override
    public void run() {
        try (Buffer buffer = new Buffer();
             Source source = Okio.source(src);
             Sink sink = Okio.sink(dest)){

            long read = source.read(buffer, buffer.size());
            System.out.println(header + ": " + buffer.snapshot().hex());
            sink.write(buffer, read);
        }
        catch(IOException ex) {
            // ignore
        }
    }

    public void close() {
        closeBlindly(src);
        closeBlindly(dest);
        closeBlindly(src_socket);
        closeBlindly(dest_socket);
    }

    static void closeBlindly(Closeable c) {
        if (c == null) return;

        try {
            c.close();
        }
        catch(IOException ex) {
            // ignore
        }
    }
}
