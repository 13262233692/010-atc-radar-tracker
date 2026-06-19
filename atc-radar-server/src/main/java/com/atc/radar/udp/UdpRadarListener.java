package com.atc.radar.udp;

import com.atc.radar.asterix.AsterixParser;
import com.atc.radar.config.AtcRadarProperties;
import com.atc.radar.model.TrackData;
import com.atc.radar.service.TrackService;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class UdpRadarListener {

    private static final int RING_BUFFER_SIZE = 16384;
    private static final int RING_BUFFER_MASK = RING_BUFFER_SIZE - 1;

    private final AtcRadarProperties properties;
    private final AsterixParser asterixParser;
    private final TrackService trackService;

    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;

    private final AtomicLong writeSequence = new AtomicLong(0);
    private final AtomicLong readSequence = new AtomicLong(0);
    private final byte[][] ringBuffer = new byte[RING_BUFFER_SIZE][];
    private final int[] ringBufferLengths = new int[RING_BUFFER_SIZE];

    private ExecutorService parseExecutor;
    private ExecutorService updateExecutor;

    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsDropped = new AtomicLong(0);
    private final AtomicLong tracksProcessed = new AtomicLong(0);

    public UdpRadarListener(AtcRadarProperties properties, AsterixParser asterixParser, TrackService trackService) {
        this.properties = properties;
        this.asterixParser = asterixParser;
        this.trackService = trackService;
    }

    @PostConstruct
    public void start() {
        try {
            int port = properties.getUdp().getPort();
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(properties.getUdp().getBufferSize());

            parseExecutor = new ThreadPoolExecutor(
                    Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().availableProcessors() * 2,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(4096),
                    new ThreadPoolExecutor.DiscardOldestPolicy()
            );

            updateExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Track-Update-Processor");
                t.setDaemon(true);
                return t;
            });

            running.set(true);

            listenerThread = new Thread(this::listenLoop, "UDP-Radar-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            Thread consumerThread = new Thread(this::consumeLoop, "Ring-Buffer-Consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();

            log.info("UDP Radar Listener started on port: {}, buffer size: {}, ring buffer: {}",
                    port, properties.getUdp().getBufferSize(), RING_BUFFER_SIZE);
        } catch (Exception e) {
            log.error("Failed to start UDP Radar Listener: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (parseExecutor != null) {
            parseExecutor.shutdownNow();
        }
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        log.info("UDP Radar Listener stopped. Stats: received={}, dropped={}, tracks={}",
                packetsReceived.get(), packetsDropped.get(), tracksProcessed.get());
    }

    private void listenLoop() {
        byte[] buffer = new byte[properties.getUdp().getBufferSize()];

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                int length = packet.getLength();
                long currentWrite = writeSequence.get();
                long currentRead = readSequence.get();

                if (currentWrite - currentRead >= RING_BUFFER_SIZE) {
                    packetsDropped.incrementAndGet();
                    log.debug("Ring buffer full, dropping packet. Write={}, Read={}", currentWrite, currentRead);
                    continue;
                }

                int slot = (int) (currentWrite & RING_BUFFER_MASK);
                byte[] data = new byte[length];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, length);
                ringBuffer[slot] = data;
                ringBufferLengths[slot] = length;

                writeSequence.lazySet(currentWrite + 1);
                packetsReceived.incrementAndGet();

            } catch (java.net.SocketException e) {
                if (running.get()) {
                    log.warn("UDP Socket exception: {}", e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.error("Error receiving UDP packet: {}", e.getMessage(), e);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long currentRead = readSequence.get();
            long currentWrite = writeSequence.get();

            if (currentRead >= currentWrite) {
                Thread.yield();
                continue;
            }

            int slot = (int) (currentRead & RING_BUFFER_MASK);
            byte[] data = ringBuffer[slot];
            int length = ringBufferLengths[slot];
            ringBuffer[slot] = null;

            readSequence.lazySet(currentRead + 1);

            if (data != null && length > 0) {
                parseExecutor.submit(() -> processPacket(data));
            }
        }
    }

    private void processPacket(byte[] data) {
        try {
            List<TrackData> tracks = asterixParser.parse(data);
            if (!tracks.isEmpty()) {
                tracksProcessed.addAndGet(tracks.size());
                updateExecutor.submit(() -> {
                    for (TrackData track : tracks) {
                        trackService.updateTrack(track);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error processing UDP packet: {}", e.getMessage(), e);
        }
    }
}
