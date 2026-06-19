package com.atc.radar.udp;

import com.atc.radar.asterix.AsterixParser;
import com.atc.radar.config.AtcRadarProperties;
import com.atc.radar.model.TrackData;
import com.atc.radar.service.TrackService;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class UdpRadarListener {

    private final AtcRadarProperties properties;
    private final AsterixParser asterixParser;
    private final TrackService trackService;

    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private Thread listenerThread;

    @PostConstruct
    public void start() {
        try {
            int port = properties.getUdp().getPort();
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(properties.getUdp().getBufferSize());

            executorService = Executors.newFixedThreadPool(4);
            running.set(true);

            listenerThread = new Thread(this::listenLoop, "UDP-Radar-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            log.info("UDP Radar Listener started on port: {}, buffer size: {}",
                    port, properties.getUdp().getBufferSize());
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
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        log.info("UDP Radar Listener stopped");
    }

    private void listenLoop() {
        byte[] buffer = new byte[properties.getUdp().getBufferSize()];

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                log.debug("Received UDP packet from {}:{}, length={}",
                        packet.getAddress(), packet.getPort(), data.length);

                executorService.submit(() -> processPacket(data));

            } catch (java.net.SocketException e) {
                if (running.get()) {
                    log.warn("UDP Socket exception: {}", e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.error("Error receiving UDP packet: {}", e.getMessage(), e);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processPacket(byte[] data) {
        try {
            List<TrackData> tracks = asterixParser.parse(data);
            if (!tracks.isEmpty()) {
                log.debug("Parsed {} track(s) from UDP packet", tracks.size());
                for (TrackData track : tracks) {
                    trackService.updateTrack(track);
                }
            }
        } catch (Exception e) {
            log.error("Error processing UDP packet: {}", e.getMessage(), e);
        }
    }
}
