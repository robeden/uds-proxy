package com.robeden.uds_proxy;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.*;
import java.util.Arrays;

class Copier implements Runnable, Closeable {
    private static final int MAX_READ_SIZE = 32 * 1024;
    private final AFUNIXSocket src_socket, dest_socket;
    private final String header;
    private final InputStream src;
    private final OutputStream dest;


    Copier(AFUNIXSocket src, AFUNIXSocket dest, String header) throws IOException {
        this.src_socket = src;
        this.dest_socket = dest;
        this.header = header;
        this.src = src.getInputStream();
        this.dest = dest.getOutputStream();
    }

    @Override
    public void run() {
        try (Buffer buffer = new Buffer();
             Source source = Okio.buffer(Okio.source(src));
             Sink sink = Okio.sink(dest)){

            long read;
            while((read = source.read(buffer, MAX_READ_SIZE)) >= 0) {
                FileDescriptor[] descriptors = src_socket.getReceivedFileDescriptors();
                if (descriptors != null && descriptors.length != 0) {
                    System.out.println(header + ": " + Arrays.toString(descriptors));
                    dest_socket.setOutboundFileDescriptors(descriptors);
                }

                if (read == 0) {
                    System.out.println(header + ": read 0");
                    continue;
                }

                System.out.println(header + ": " + buffer.snapshot().hex());
                sink.write(buffer, read);
                sink.flush();
            }
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
