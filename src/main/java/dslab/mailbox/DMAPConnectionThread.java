package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class DMAPConnectionThread extends Thread {

    private final Socket socket;
    private final Config userConfig;
    private final ConcurrentHashMap<String, List<Mail>> mailBoxes;
    private String currentUser = null;
    private boolean quit = false;

    public DMAPConnectionThread(Socket socket, Config userConfig, ConcurrentHashMap<String, List<Mail>> mailBoxes) {
        this.socket = socket;
        this.userConfig = userConfig;
        this.mailBoxes = mailBoxes;
    }

    public void run() {
        try {

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());

            String request;

            writer.println("ok DMAP2.0");
            writer.flush();

            // read client requests
            while ((request = reader.readLine()) != null) {
                System.out.println("C: " + request);

                String[] parts = request.split("\\s");

                String response = "";

                if (request.startsWith("login")) {
                    if (parts.length != 3) response = "invalid number of arguments";
                    else {
                        response = login(parts[1], parts[2]);
                        if (response.equals("ok")) currentUser = parts[1];
                    }
                } else if (request.startsWith("list")) {
                    response = list();
                } else if (request.startsWith("show")) {
                    if (parts.length != 2) response = "invalid number of arguments";
                    else {
                        try {
                            response = show(Integer.parseInt(parts[1]));
                        } catch (NumberFormatException e) {
                            response = "parameter is not a valid message-id";
                        }
                    }
                    if (parts.length != 2) response = "invalid number of arguments";
                } else if (request.startsWith("delete")) {
                    if (parts.length != 2) response = "invalid number of arguments";
                    else {
                        try {
                            response = delete(Integer.parseInt(parts[1]));
                        } catch (NumberFormatException e) {
                            response = "parameter is not a valid message-id";
                        }
                    }
                } else if (request.startsWith("logout")) {
                    response = logout();
                } else if (request.startsWith("quit")) {
                    response = "ok bye";
                    quit = true;
                } else {
                    response = "error protocol error";
                    quit = true;
                }

                // print request
                System.out.println("S: " + response);
                writer.println(response);
                writer.flush();
                if (quit) break;
            }

            reader.close();
            writer.close();

        } catch (SocketException e) {
            // when the socket is closed, the I/O methods of the Socket will throw a SocketException
            // almost all SocketException cases indicate that the socket was closed
            System.out.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            // you should properly handle all other exceptions
            throw new UncheckedIOException(e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignored because we cannot handle it
                }
            }
        }
    }

    public String login(String username, String password)
    {
        if(userConfig.containsKey(username))
        {
            return userConfig.getString(username).equals(password) ? "ok" : "error wrong password";
        }
        return "error user not found";
    }

    public String list() {
        if (currentUser != null) {
            if (mailBoxes.containsKey(currentUser) && mailBoxes.get(currentUser).size() > 0) {
                String allMails = "";
                String separator = System.getProperty("line.separator");
                for(Mail mail : mailBoxes.get(currentUser)) {
                    allMails = allMails.concat(mail.toString());
                    if (!mail.equals(mailBoxes.get(currentUser).get(mailBoxes.get(currentUser).size() - 1))) allMails = allMails.concat(separator);
                }
                allMails = allMails.concat(separator);
                allMails = allMails.concat("ok");
                return allMails;
            }
            return "no mail";
        }
        return "error not logged in";
    }

    public String show(int messageId) {
        if (currentUser != null) {
            if (mailBoxes.containsKey(currentUser)) {
                for (Mail mail : mailBoxes.get(currentUser)) {
                    if (mail.getMessageId() == messageId) {
                        String separator = System.getProperty("line.separator");
                        String hash = mail.getHash() == null ? "" : mail.getHash();
                        String recipientList = Arrays.toString(mail.getRecipients().toArray());
                        return "from " + mail.getSender() + separator +
                                "to " + recipientList.substring(1, recipientList.length() - 1) + separator +
                                "subject " + mail.getSubject() + separator +
                                "data " + mail.getData() + separator +
                                "hash " + hash;
                    }
                }
            }
            return "error unknown message id";
        }
        return "error not logged in";
    }

    public String delete(int messageId) {
        if (currentUser != null) {
            if (mailBoxes.containsKey(currentUser)) {
                for (Mail mail : mailBoxes.get(currentUser)) {
                    if (mail.getMessageId() == messageId) {
                        mailBoxes.get(currentUser).remove(mail);
                        return "ok";
                    }
                }
            }
            return "error unknown message id";
        }
        return "error not logged in";
    }

    public String logout() {
        if (currentUser == null) return "error not logged in";
        currentUser = null;
        return "ok";
    }
}
