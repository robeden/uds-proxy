package com.robeden.uds_proxy;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.AFUNIXSocketPair;
import org.newsclub.net.unix.FileDescriptorCast;

import java.io.*;
import java.nio.channels.Channels;
import java.util.Arrays;

class Copier implements Runnable, Closeable {
    static final boolean LEFT_TO_RIGHT = true;
    static final boolean RIGHT_TO_LEFT = false;

    private static final int MAX_READ_SIZE = 32 * 1024;

    private final Closeable src_sock, dest_sock;
    private final Source src;
    private final Sink dest;
    private final IOSupplier<FileDescriptor[]> src_fd_supplier;
    private final IOConsumer<FileDescriptor[]> dest_fd_consumer;

    private final String header_id;
    private final String header;

    private int next_fd_index = 0;


    Copier(AFUNIXSocket src, AFUNIXSocket dest, boolean direction_ltr, String header_id) throws IOException {
        this(src, dest, Okio.source(src.getInputStream()), Okio.sink(dest.getOutputStream()),
            src::getReceivedFileDescriptors, dest::setOutboundFileDescriptors, direction_ltr, header_id);
    }

    Copier(AFUNIXSocketChannel src, AFUNIXSocketChannel dest, boolean direction_ltr, String header_id)
        throws IOException {

        this(src, dest, Okio.source(Channels.newInputStream(src)), Okio.sink(Channels.newOutputStream(dest)),
            src::getReceivedFileDescriptors, dest::setOutboundFileDescriptors, direction_ltr, header_id);
    }

    private Copier(Closeable src_sock, Closeable dest_sock,
                   Source src, Sink dest,
                   IOSupplier<FileDescriptor[]> src_fd_supplier,
                   IOConsumer<FileDescriptor[]> dest_fd_consumer,
                   boolean direction_ltr, String header_id) {
        this.src_sock = src_sock;
        this.dest_sock = dest_sock;
        this.src = src;
        this.dest = dest;
        this.src_fd_supplier = src_fd_supplier;
        this.dest_fd_consumer = dest_fd_consumer;
        this.header_id = header_id;
        this.header = (direction_ltr ? "|--> " : "|<-- ") + header_id + ": ";
    }


    @Override
    public void run() {
        try (Buffer buffer = new Buffer();
             Source source = Okio.buffer(src);
             Sink sink = dest) {

            long read;
            while((read = source.read(buffer, MAX_READ_SIZE)) >= 0) {
                FileDescriptor[] descriptors = src_fd_supplier.get();
                if (descriptors != null && descriptors.length != 0) {
//                    System.out.println(header + Arrays.toString(descriptors));

                    FileDescriptor[] to_set_descriptors = new FileDescriptor[descriptors.length];
                    for(int i = 0; i < descriptors.length; i++) {
                        int fd_id = next_fd_index++;

                        System.out.println(header + "FD " + fd_id + " (" +
                            FileDescriptorCast.using(descriptors[i]).as(Integer.class) + ")");

                        FileInputStream fd_in = new FileInputStream(descriptors[i]);
                        FileOutputStream fd_out = new FileOutputStream(descriptors[i]);

                        AFUNIXSocketPair<AFUNIXSocketChannel> pair = AFUNIXSocketPair.open();
                        var local_sock = pair.getSocket1().socket();
                        var remote_sock = pair.getSocket2();

                        Thread.ofVirtual().start(new Copier(
                            fd_in, local_sock,
                            Okio.source(fd_in), Okio.sink(local_sock.getOutputStream()),
                            () -> null, fds -> {},       // not supporting FDs
                            Copier.LEFT_TO_RIGHT, header_id + " (FD " + fd_id + ")"));
                        Thread.ofVirtual().start(new Copier(
                            local_sock, fd_out,
                            Okio.source(local_sock.getInputStream()), Okio.sink(fd_out),
                            () -> null, fds -> {},       // not supporting FDs
                            Copier.RIGHT_TO_LEFT, header_id + " (FD " + fd_id + ")"));

                        to_set_descriptors[i] = remote_sock.getFileDescriptor();
                    }

                    dest_fd_consumer.consume(to_set_descriptors);
                }

                if (read == 0) {
                    System.out.println(header + "read 0");
                    continue;
                }

                System.out.println(header + buffer.snapshot().hex());
                sink.write(buffer, read);
                sink.flush();
            }
        }
        catch(IOException ex) {
            // ignore
            System.out.println(header + ex.getMessage());
        }
        finally {
            System.out.println(header + "closing");
        }
    }

    public void close() {
        closeBlindly(src);
        closeBlindly(dest);
        closeBlindly(src_sock);
        closeBlindly(dest_sock);
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


    interface IOSupplier<T> {
        T get() throws IOException;
    }

    interface IOConsumer<T> {
        void consume(T value) throws IOException;
    }
}
