package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DMAPListenerThread implements Runnable {

    private final ServerSocket serverSocket;
    private final Config userConfig;
    private final ConcurrentHashMap<String, List<Mail>> mailBoxes;

    private final String componentId;

    public DMAPListenerThread(ServerSocket serverSocket, Config userConfig, ConcurrentHashMap<String, List<Mail>> mailBoxes, String componentId) {
        this.serverSocket = serverSocket;
        this.userConfig = userConfig;
        this.mailBoxes = mailBoxes;
        this.componentId = componentId;
    }

    public void run() {
        Socket socket = null;
        try {
            while (true) {
                // wait for Client to connect
                socket = serverSocket.accept();
                new DMAPConnectionThread(socket, userConfig, mailBoxes, componentId).start();
            }
        } catch (IOException e) {
            System.out.println("IOException while running DMAP listener: " + e.getMessage());
        }
    }
}
