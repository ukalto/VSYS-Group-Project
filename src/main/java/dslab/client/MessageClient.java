package dslab.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.Mac;

public class MessageClient implements IMessageClient, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private Shell shell;
    private Socket mailboxSocket;
    private BufferedReader mailboxReader;
    private PrintWriter mailboxWriter;
    private Mac mac;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(Keys.readSecretKey(new File("keys/hmac.key")));
        } catch (NoSuchAlgorithmException | IOException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        String username = config.getString("mailbox.user");
        String password = config.getString("mailbox.password");
        String mailboxHost = config.getString("mailbox.host");
        int mailboxPort = config.getInt("mailbox.port");

        //TODO: use startsecure (Lorenz)

        try {
            mailboxSocket = new Socket(mailboxHost, mailboxPort);
            mailboxReader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));
            mailboxWriter = new PrintWriter(mailboxSocket.getOutputStream());
            // Skip ok DMAP2.0 line
            mailboxReader.readLine();
            mailboxWriter.println("login " + username + " " + password);
            mailboxWriter.flush();
            String response = mailboxReader.readLine();
            if (response == null) {
                System.out.println("Failed to log in, shutting down");
                shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        shell.run();
    }

    @Command
    @Override
    public void inbox() {
        mailboxWriter.println("list");
        mailboxWriter.flush();
        List<String> mails = new ArrayList<>();
        String mailEntry;
        try {
            while (((mailEntry = mailboxReader.readLine()) != null)) {
                if (mailEntry.startsWith("no")) {
                    shell.out().println(mailEntry);
                    return;
                }
                if (mailEntry.startsWith("ok")) {
                    break;
                }
                mails.add(mailEntry);
            }
            for(String mail : mails) {
                String mailId = mail.split(" ")[0];
                mailboxWriter.println("show " + mailId);
                mailboxWriter.flush();
                String from = mailboxReader.readLine().split("\\s", 2)[1];
                String to = mailboxReader.readLine().split("\\s", 2)[1];
                String subject = mailboxReader.readLine().split("\\s", 2)[1];
                String data = mailboxReader.readLine().split("\\s", 2)[1];
                // Don't need hash, but need to skip line
                mailboxReader.readLine();
                shell.out().println("Mail " + mailId + ", " + subject + ": ");
                shell.out().println("From: " + from);
                shell.out().println("To: " + to);
                shell.out().println(data + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void delete(String id) {
        mailboxWriter.println("delete " + id);
        mailboxWriter.flush();
        try {
            shell.out().println(mailboxReader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void verify(String id) {
        try {
            mailboxWriter.println("show " + id);
            mailboxWriter.flush();
            String from = mailboxReader.readLine().split("\\s", 2)[1];
            String to = mailboxReader.readLine().split("\\s", 2)[1];
            String subject = mailboxReader.readLine().split("\\s", 2)[1];
            String data = mailboxReader.readLine().split("\\s", 2)[1];
            String[] hashOpt = mailboxReader.readLine().split("\\s", 2);
            if (hashOpt.length <= 1)  {
                shell.out().println("error");
                return;
            }
            String hash = hashOpt[1];
            String msg = String.join("\n", from, to, subject, data);
            byte[] resultHash = mac.doFinal(msg.getBytes());
            if (hash.equals(Base64.getEncoder().withoutPadding().encodeToString(resultHash))) shell.out().println("ok");
            else shell.out().println("error");
        } catch (IOException e) {
            System.out.println("Failed to verify message");
        }
    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        String email = config.getString("transfer.email");
        String transferHost = config.getString("transfer.host");
        int transferPort = config.getInt("transfer.port");
        String[] commandOrder = {"begin", "to", "from", "subject", "data", "hash", "send", "quit"};
        try {
            Socket transferSocket = new Socket(transferHost, transferPort);
            BufferedReader transferReader = new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));
            PrintWriter transferWriter = new PrintWriter(transferSocket.getOutputStream());
            // Skip ok DMTP2.0 line
            transferReader.readLine();

            String msg = String.join("\n", email, to, subject, data);
            byte[] hash = mac.doFinal(msg.getBytes());

            // DMTP command loop
            for (String command : commandOrder) {
                String message = command;
                switch (command) {
                    case "to":
                        message = command + " " + to;
                        break;
                    case "from":
                        message = command + " " + email;
                        break;
                    case "subject":
                        message = command + " " + subject;
                        break;
                    case "data":
                        message = command + " " + data;
                        break;
                    case "hash":
                        message = command + " " + Base64.getEncoder().withoutPadding().encodeToString(hash);
                        break;
                }
                transferWriter.println(message);
                transferWriter.flush();
                String errMsg = transferReader.readLine();
                if (errMsg != null && errMsg.startsWith("error")) {
                    shell.out().println(errMsg);
                    transferWriter.println("quit");
                    transferWriter.flush();
                    return;
                }
            }
            shell.out().println("ok");
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }

    }

    @Command
    @Override
    public void shutdown() {
        mailboxWriter.println("logout");
        mailboxWriter.flush();
        if (mailboxSocket != null && !mailboxSocket.isClosed()) {
            try {
                mailboxSocket.close();
            } catch (Exception e) {
                System.err.println("Error while closing mailbox socket: " + e.getMessage());
            }
        }
        try {
            in.close();
            out.close();
        } catch (IOException e) {}
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
