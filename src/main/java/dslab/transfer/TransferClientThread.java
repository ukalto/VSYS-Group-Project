package dslab.transfer;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Client application that connects to a Server via TCP to send provided user-input.
 */
public class TransferClientThread implements Runnable {

    private final LinkedBlockingQueue<Mail> mailQueue;
    private final Map<String, String> domains;
    private final Config config;

    public TransferClientThread(LinkedBlockingQueue<Mail> mailQueue, Map<String, String> domains, Config config) {
        this.mailQueue = mailQueue;
        this.domains = domains;
        this.config = config;
    }

    @Override
    public void run() {
        Socket mailServerSocket = null;
        DatagramSocket monitoringServerSocket = null;
        byte[] buffer;
        DatagramPacket packet;
        Mail mailToSend;
        String[] commandOrder = {"begin", "to", "from", "subject", "data", "hash", "send", "quit"};
        String monitoringServerAddress = config.getString("monitoring.host");
        int monitoringServerPort = config.getInt("monitoring.port");

        try {
            monitoringServerSocket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Could not create datagram socket (no monitoring)");
        }

        // fallback/default address
        String currentAddress = "mailer@[transferserver]";
        try {
            currentAddress = "mailer@" + InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println("Could not get current IP address in TransferClient" + e.getMessage());
        }


        try {
            while (true) {
                mailToSend = mailQueue.take();
                List<String> domainList = new ArrayList<>();
                // get domains
                for (String recipient : mailToSend.getRecipients()) {
                    String domain = recipient.split("@")[1];
                    if (!domainList.contains(domain)) domainList.add(domain);
                }
                // send to every mailserver
                for (String domain : domainList) {
                    boolean success = true;
                    String sender = mailToSend.getSender();

                    String recipientList = Arrays.toString(mailToSend.getRecipients().toArray());

                    if (domains.get(domain) == null) {
                        if (mailToSend.getSender().equals(currentAddress)) continue;
                        Mail errorMail = new Mail(currentAddress, List.of(sender), "delivery failed", "error domain not found");
                        mailQueue.put(errorMail);
                        continue;
                    }

                    String address = domains.get(domain).split(":")[0];
                    int port = Integer.parseInt(domains.get(domain).split(":")[1]);

                    mailServerSocket = new Socket(address, port);

                    BufferedReader serverReader = new BufferedReader(new InputStreamReader(mailServerSocket.getInputStream()));
                    PrintWriter serverWriter = new PrintWriter(mailServerSocket.getOutputStream());
                    //skip ok DMTP line
                    serverReader.readLine();

                    // DMTP command loop
                    for (String command : commandOrder) {
                        String message = command;
                        switch (command) {
                            case "to":
                                message = command + " " + recipientList.substring(1, recipientList.length() - 1);
                                break;
                            case "from":
                                message = command + " " + mailToSend.getSender();
                                break;
                            case "subject":
                                message = command + " " + mailToSend.getSubject();
                                break;
                            case "data":
                                message = command + " " + mailToSend.getData();
                                break;
                            case "hash":
                                if (mailToSend.getHash() != null) {
                                    message = command + " " + mailToSend.getHash();
                                    break;
                                } else {
                                    continue;
                                }
                        }
                        serverWriter.println(message);
                        serverWriter.flush();
                        // read server response and write it to console
                        String mailServerResponse = serverReader.readLine();
                        if (mailServerResponse.startsWith("error")) {
                            success = false;
                            if (mailToSend.getSender().equals(currentAddress)) break;
                            serverWriter.println("quit");
                            serverWriter.flush();
                            Mail errorMail = new Mail(currentAddress, List.of(sender), "delivery failed", mailServerResponse);
                            mailQueue.put(errorMail);
                            break;
                        }
                    }

                    // send statistics to monitoring server
                    if (success) {
                        String message = currentAddress.split("@")[1] + ":" + config.getString("tcp.port") + " " + mailToSend.getSender();
                        buffer = message.getBytes();

                        packet = new DatagramPacket(buffer, buffer.length,
                                InetAddress.getByName(monitoringServerAddress),
                                monitoringServerPort);

                        if (monitoringServerSocket != null) {
                            monitoringServerSocket.send(packet);
                        }
                    }
                }
            }
        } catch (UnknownHostException e) {
            System.out.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            // when the socket is closed, the I/O methods of the Socket will throw a SocketException
            // almost all SocketException cases indicate that the socket was closed
            System.out.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            // you should properly handle all other exceptions
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            System.out.println("Interrupted in TransferServer-MailboxServer connection: " + e.getMessage());
        } finally {
            if (mailServerSocket != null && !mailServerSocket.isClosed()) {
                try {
                    mailServerSocket.close();
                } catch (IOException e) {
                    // Ignored because we cannot handle it
                }
            }
            if (monitoringServerSocket != null && !monitoringServerSocket.isClosed()) {
                monitoringServerSocket.close();
            }
        }
    }
}
