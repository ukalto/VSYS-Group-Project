package dslab.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
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

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;

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
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private SecretKey secretKey;
    private IvParameterSpec initVec;

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

    public static String rsaEncrypt(String msg, PublicKey key) throws Exception {
        byte[] msgToByte = msg.getBytes();
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedMsg = cipher.doFinal(msgToByte);
        return encode(encryptedMsg);
    }

    public static String rsaDecrypt(String msg, PrivateKey key) throws Exception
    {
        byte[] encryptedBytes = decode(msg);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedMessage = cipher.doFinal(encryptedBytes);
        return new String(decryptedMessage, "UTF8");
    }

    private String aesEncrypt(String msg) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, this.initVec);
        byte[] cipherText = cipher.doFinal(msg.getBytes());
        return encode(cipherText);
    }

    private String aesDecrypt(String msg) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, this.secretKey, this.initVec);
        byte[] cipherText = cipher.doFinal(decode(msg));
        return new String(cipherText);
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
    private static byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    private static SecretKey generateSecretKey(int size) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(size);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }

    private static IvParameterSpec generateInitVec(int size) {
        byte[] iv = new byte[size];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    private static byte[] generateChallenge(int size) {
        byte[] challenge = new byte[size];
        new SecureRandom().nextBytes(challenge);
        return challenge;
    }
    @Override
    public void run() {

        String username = config.getString("mailbox.user");
        String password = config.getString("mailbox.password");
        String mailboxHost = config.getString("mailbox.host");
        int mailboxPort = config.getInt("mailbox.port");

        try {
            mailboxSocket = new Socket(mailboxHost, mailboxPort);
            mailboxReader = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));
            mailboxWriter = new PrintWriter(mailboxSocket.getOutputStream());
            // Skip ok DMAP2.0 line
            mailboxReader.readLine();
            //Establish secure connection
            mailboxWriter.println("startsecure");
            mailboxWriter.flush();
            //Retrieve Server Component Id
            String res = mailboxReader.readLine();
            if(!res.startsWith("ok")){
                System.out.println("Component Id Error, shutting down");
                shutdown();
            }
            res = res.substring(res.indexOf(" ") + 1);
            File publicKeyFile = new File("./keys/client/" + res + "_pub.der");
            File privateKeyFile= new File("./keys/server/mailbox-earth-planet.der");

            try {
                //Generate Challenge, Secret Key and Initialization Vector
                byte[] challenge = generateChallenge(32);
                secretKey = generateSecretKey(256);
                initVec = generateInitVec(16);

                byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());
                byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());

                X509EncodedKeySpec keySpecPublic = new X509EncodedKeySpec(publicKeyBytes);
                PKCS8EncodedKeySpec keySpecPrivate = new PKCS8EncodedKeySpec(privateKeyBytes);

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                publicKey = keyFactory.generatePublic(keySpecPublic);
                privateKey = keyFactory.generatePrivate(keySpecPrivate);

                System.out.println(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
                System.out.println(Base64.getEncoder().encodeToString(privateKey.getEncoded()));

                String challengeMsg = "ok " + encode(challenge) + " " + encode(secretKey.getEncoded()) + " " + encode(initVec.getIV());
                String encryptedChallengeMsg = rsaEncrypt(challengeMsg, publicKey);
                System.out.println(challengeMsg);
                mailboxWriter.println(encryptedChallengeMsg);
                mailboxWriter.flush();
                //Check if challenge is correct
                res = mailboxReader.readLine();
                res = aesDecrypt(res);
                res = res.substring(res.indexOf(' ') + 1);
                if(!res.equals(encode(challenge)))
                {
                    System.out.println("RSA Handshake failed, shutting down");
                    shutdown();
                }else{
                    mailboxWriter.println(aesEncrypt("ok"));
                    mailboxWriter.flush();
                }
                //RSA-Handshake completed proceed with login
                String request = "login " + username + " " + password;
                request = aesEncrypt(request);

                mailboxWriter.println(request);
                mailboxWriter.flush();

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeySpecException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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
    public void inbox() throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        mailboxWriter.println((aesEncrypt("list")));
        mailboxWriter.flush();
        List<String> mails = new ArrayList<>();
        String mailEntry;
        try {
            mailEntry = aesDecrypt(mailboxReader.readLine());
            if (mailEntry.startsWith("no")) {
                shell.out().println(mailEntry);
                return;
            }
            String[] allEntries = mailEntry.split("\r\n");
            for(String entry : allEntries)
            {
                if(entry.startsWith("ok")){
                    continue;
                }else{
                    mails.add(entry);
                }
            }
            for(String mail : mails) {
                String mailId = mail.split(" ")[0];
                mailboxWriter.println(aesEncrypt("show " + mailId));
                mailboxWriter.flush();
                String[] msgData = aesDecrypt(mailboxReader.readLine()).split("\r\n");
                String from = msgData[0];
                String to = msgData[1];
                String subject = msgData[2];
                String data = msgData[3];
                String hash = msgData[4];

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
    public void delete(String id) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        mailboxWriter.println(aesEncrypt("delete " + id));
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
            mailboxWriter.println(aesEncrypt("show " + id));
            mailboxWriter.flush();
            String from = aesDecrypt(mailboxReader.readLine()).split("\\s", 2)[1];
            String to = aesDecrypt(mailboxReader.readLine()).split("\\s", 2)[1];
            String subject = aesDecrypt(mailboxReader.readLine()).split("\\s", 2)[1];
            String data = aesDecrypt(mailboxReader.readLine()).split("\\s", 2)[1];
            String[] hashOpt = aesDecrypt(mailboxReader.readLine()).split("\\s", 2);
            if (hashOpt.length <= 1)  {
                shell.out().println("error");
                return;
            }
            String hash = hashOpt[1];
            String msg = String.join("\n", from, to, subject, data);
            byte[] resultHash = mac.doFinal(msg.getBytes());
            if (hash.equals(Base64.getEncoder().withoutPadding().encodeToString(resultHash))) shell.out().println("ok");
            else shell.out().println("error");
        } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
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
