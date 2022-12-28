package dslab.transfer;

import dslab.util.Mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class DMTPTransferListenerThread implements Runnable {

    private final ServerSocket serverSocket;
    private final LinkedBlockingQueue<Mail> mailQueue;

    public DMTPTransferListenerThread(ServerSocket serverSocket, LinkedBlockingQueue<Mail> mailQueue) {
        this.serverSocket = serverSocket;
        this.mailQueue = mailQueue;
    }

    public void run() {
        Socket socket = null;
        try {
            while (true) {
                // wait for Client to connect
                socket = serverSocket.accept();
                new DMTPTransferConnectionThread(socket, mailQueue).start();
            }
        } catch (IOException e) {
            System.out.println("IOException while running DMTP listener: " + e.getMessage());
        }
    }
}
