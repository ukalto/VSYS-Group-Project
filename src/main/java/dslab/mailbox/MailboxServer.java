package dslab.mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.*;

public class MailboxServer implements IMailboxServer, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private ServerSocket dmtpServerSocket;
    private ServerSocket dmapServerSocket;
    private ConcurrentHashMap<String, List<Mail>> mailBoxes;
    private Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
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
        Config userConfig = new Config(config.getString("users.config"));
        String domain = config.getString("domain");
        mailBoxes = new ConcurrentHashMap<>();
        try {
            dmtpServerSocket = new ServerSocket(config.getInt("dmtp.tcp.port"));
            dmapServerSocket = new ServerSocket(config.getInt("dmap.tcp.port"));
            new Thread(new DMTPMailboxListenerThread(dmtpServerSocket, userConfig, mailBoxes, domain)).start();
            new Thread(new DMAPListenerThread(dmapServerSocket, userConfig, mailBoxes, componentId)).start();
        } catch (IOException e) {}
        shell.run();
    }

    @Command
    @Override
    public void shutdown() {
        if (dmtpServerSocket != null && !dmtpServerSocket.isClosed()) {
            try {
                dmtpServerSocket.close();
            } catch (Exception e) {
                System.err.println("Error while closing server socket: " + e.getMessage());
            }
        }
        if (dmapServerSocket != null && !dmapServerSocket.isClosed()) {
            try {
                dmapServerSocket.close();
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
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
