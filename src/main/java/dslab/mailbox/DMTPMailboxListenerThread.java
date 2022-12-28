package dslab.mailbox;

import dslab.mailbox.DMTPMailboxConnectionThread;
import dslab.mailbox.IMailboxServer;
import dslab.transfer.DMTPTransferConnectionThread;
import dslab.util.Config;
import dslab.util.Mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DMTPMailboxListenerThread implements Runnable {

    private final ServerSocket serverSocket;
    private final Config userConfig;
    private final ConcurrentHashMap<String, List<Mail>> mailBoxes;
    private final String domain;

    public DMTPMailboxListenerThread(ServerSocket serverSocket, Config userConfig, ConcurrentHashMap<String, List<Mail>> mailBoxes, String domain) {
        this.serverSocket = serverSocket;
        this.userConfig = userConfig;
        this.mailBoxes = mailBoxes;
        this.domain = domain;
    }

    public void run() {
        Socket socket = null;
        try {
            while (true) {
                // wait for Client to connect
                socket = serverSocket.accept();
                new DMTPMailboxConnectionThread(socket, userConfig, mailBoxes, domain).start();
            }
        } catch (IOException e) {
            System.out.println("IOException while running DMTP listener: " + e.getMessage());
        }
    }
}
