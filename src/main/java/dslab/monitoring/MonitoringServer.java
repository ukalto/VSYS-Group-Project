package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private DatagramSocket datagramSocket;
    private Map<String, Integer> addresses;
    private Map<String, Integer> servers;
    private Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        int udpPort = config.getInt("udp.port");
        addresses = new HashMap<>();
        servers = new HashMap<>();
        try {
            datagramSocket = new DatagramSocket(udpPort);
            new UDPConnectionThread(datagramSocket, addresses, servers).start();
        } catch (IOException e) {}
        shell.run();
    }

    @Command
    @Override
    public void addresses() {
        addresses.forEach((key, value) -> shell.out().println(key + " " + value));
    }

    @Command
    @Override
    public void servers() {
        servers.forEach((key, value) -> shell.out().println(key + " " + value));
    }

    @Command
    @Override
    public void shutdown() {
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            try {
                datagramSocket.close();
            } catch (Exception e) {
                System.err.println("Error while closing server socket: " + e.getMessage());
            }
        }
        try {
            in.close();
            out.close();
        } catch (IOException e) {}
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
