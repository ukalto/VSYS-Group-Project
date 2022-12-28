package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Mail;

public class TransferServer implements ITransferServer, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private ServerSocket dmtpServerSocket;
    private LinkedBlockingQueue<Mail> mailQueue;
    private Shell shell;
    private final Map<String, String> domains;
    ExecutorService pool;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        this.domains = new HashMap<>();
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        try {
            dmtpServerSocket = new ServerSocket(config.getInt("tcp.port"));
            mailQueue = new LinkedBlockingQueue<>();
            Config domainConfig = new Config("domains");
            domainConfig.listKeys().forEach(domain -> domains.put(domain, domainConfig.getString(domain)));

            pool = Executors.newFixedThreadPool(2);
            pool.execute(new Thread(new DMTPTransferListenerThread(dmtpServerSocket, mailQueue)));
            pool.execute(new Thread(new TransferClientThread(mailQueue, domains, config)));
        } catch (Exception e) {
            try {
                throw e;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
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
        try {
            in.close();
            out.close();
        } catch (IOException e) {}
        pool.shutdownNow();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
