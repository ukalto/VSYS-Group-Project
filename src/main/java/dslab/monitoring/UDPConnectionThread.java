package dslab.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;

public class UDPConnectionThread extends Thread {

    private final DatagramSocket datagramSocket;
    private final Map<String, Integer> addresses;
    private final Map<String, Integer> servers;

    public UDPConnectionThread(DatagramSocket datagramSocket, Map<String, Integer> addresses, Map<String, Integer> servers) {
        this.datagramSocket = datagramSocket;
        this.addresses = addresses;
        this.servers = servers;
    }

    public void run() {
        byte[] buffer;
        DatagramPacket packet;
        try {
            while (true) {
                buffer = new byte[1024];
                packet = new DatagramPacket(buffer, buffer.length);

                // wait for incoming packets from client
                datagramSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                String server = message.split(" ")[0];
                String address = message.split(" ")[1];

                if (servers.containsKey(server)) {
                    servers.put(server, servers.get(server) + 1);
                } else {
                    servers.put(server, 1);
                }

                if (addresses.containsKey(address)) {
                    addresses.put(address, addresses.get(address) + 1);
                } else {
                    addresses.put(address, 1);
                }
            }
        } catch (SocketException e) {
            System.out.println("SocketException while waiting for/handling packets: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
            }
        }
    }


}
