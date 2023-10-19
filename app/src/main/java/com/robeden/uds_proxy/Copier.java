package com.robeden.uds_proxy;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.AFUNIXSocketPair;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Arrays;

class Copier implements Runnable, Closeable {
    private static final int MAX_READ_SIZE = 32 * 1024;

    private final Closeable src, dest;
    private final IOSupplier<FileDescriptor[]> src_fd_supplier;
    private final IOConsumer<FileDescriptor[]> dest_fd_consumer;

    private final String header_id;
    private final String header;

    private int next_fd_index = 0;


    Copier(AFUNIXSocket src, AFUNIXSocket dest, boolean direction_ltr, String header_id) {
        this(src, dest, src::getReceivedFileDescriptors, dest::setOutboundFileDescriptors, direction_ltr, header_id);
    }

    Copier(AFUNIXSocketChannel src, AFUNIXSocketChannel dest, boolean direction_ltr, String header_id) {
        this(src, dest, src::getReceivedFileDescriptors, dest::setOutboundFileDescriptors, direction_ltr, header_id);
    }

    private Copier(Closeable src, Closeable dest, IOSupplier<FileDescriptor[]> src_fd_supplier,
                   IOConsumer<FileDescriptor[]> dest_fd_consumer,
                   boolean direction_ltr, String header_id) {
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
             Source source = Okio.buffer(Okio.source(src));
             Sink sink = Okio.sink(dest)){

            long read;
            while((read = source.read(buffer, MAX_READ_SIZE)) >= 0) {
                FileDescriptor[] descriptors = src_fd_supplier.get();
                if (descriptors != null && descriptors.length != 0) {
                    System.out.println(header + ": " + Arrays.toString(descriptors));

                    for(int i = 0; i < descriptors.length; i++) {
                        int fd_id = next_fd_index++;

                        AFUNIXSocketPair<AFUNIXSocketChannel> pair = AFUNIXSocketPair.open();
                        AFUNIXSocketChannel out_local = pair.getSocket1();
                        AFUNIXSocketChannel out_remote = pair.getSocket2();

                        Thread.ofVirtual().start(
                            new Copier(out_local, out_remote, true, header_id + " (FD " + fd_id + ")"));
                        Thread.ofVirtual().start(
                            new Copier(out_remote, out_local, false, header_id + " (FD " + fd_id + ")"));
                    }

                    dest_fd_consumer.consume(descriptors);
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
