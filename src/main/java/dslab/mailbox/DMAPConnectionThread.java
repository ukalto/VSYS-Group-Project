package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
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
    private final String componentId;

    private SecretKey secretKey;
    private IvParameterSpec iv;

    private boolean aesEstablished = false;

    public DMAPConnectionThread(Socket socket, Config userConfig, ConcurrentHashMap<String, List<Mail>> mailBoxes, String componentId) {
        this.socket = socket;
        this.userConfig = userConfig;
        this.mailBoxes = mailBoxes;
        this.componentId = componentId;
    }

    public void run() {
        try {

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());

            String request;

            writer.println("ok DMAP2.0");
            writer.flush();

            //Handshake-Protocol Variables
            boolean secureStarted = false;
            boolean rsaComplete = false;
            PrivateKey privateKey = null;
            String challenge = null;
            // read client requests
            while ((request = reader.readLine()) != null) {
                //Check if client verified servers identity

                //If RSA-Completed then decrypt AES-Encrypted messages
                if (rsaComplete) {
                    request = aesDecrypt(request);
                }

                System.out.println("C: " + request);

                String[] parts = request.split("\\s");

                String response = "";
                if (request.startsWith("startsecure")) {
                    System.out.println("Startsecure initiated");
                    response = "ok " + componentId;
                    secureStarted = true;
                } else if (secureStarted) {
                    System.out.println("SecureChannel: Receiving client response");
                    File privateKeyFile = new File("./keys/server/" + componentId + ".der");
                    byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
                    PKCS8EncodedKeySpec keySpecPrivate = new PKCS8EncodedKeySpec(privateKeyBytes);
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    privateKey = keyFactory.generatePrivate(keySpecPrivate);
                    String decryptedMsg = rsaDecrypt(request, privateKey);
                    System.out.println("Server received: " + decryptedMsg);
                    String[] msgComponents = decryptedMsg.split(" ");
                    challenge = msgComponents[1];
                    //Set AES Parameters
                    byte[] secretKeyBytes = decode(msgComponents[2]);
                    secretKey = new SecretKeySpec(secretKeyBytes, "AES");
                    iv = new IvParameterSpec(decode(msgComponents[3]));

                    //Send decrypted Challenge to Client
                    response = "ok " + challenge;
                    response = aesEncrypt(response);
                    secureStarted = false;
                    rsaComplete = true;
                } else if (request.equals("ok") && !aesEstablished) {
                    aesEstablished = true; //Finalize Handshake
                    continue;
                } else if (request.startsWith("login")) {
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

                // print request, depending on handshake finalized encrypt message
                System.out.println("S: " + response);
                if (aesEstablished && !request.equals("list") && !request.startsWith("show")) {
                    writer.println(aesEncrypt(response));
                } else {
                    writer.println(response);
                }
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    public String login(String username, String password) {
        if (userConfig.containsKey(username)) {
            return userConfig.getString(username).equals(password) ? "ok" : "error wrong password";
        }
        return "error user not found";
    }

    public String list() throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (currentUser != null) {
            if (mailBoxes.containsKey(currentUser) && mailBoxes.get(currentUser).size() > 0) {
                String allMails = "";
                String separator = System.getProperty("line.separator");
                for (Mail mail : mailBoxes.get(currentUser)) {
                    if(aesEstablished){
                        allMails = allMails.concat(aesEncrypt(mail.toString()));
                    }else{
                        allMails = allMails.concat(mail.toString());
                    }
                    if (!mail.equals(mailBoxes.get(currentUser).get(mailBoxes.get(currentUser).size() - 1)))
                        allMails = allMails.concat(separator);
                }
                allMails = allMails.concat(separator);
                if(aesEstablished) {
                    allMails = allMails.concat(aesEncrypt("ok"));
                }else{
                    allMails = allMails.concat("ok");
                }
                return allMails;
            }
            return "no mail";
        }
        return "error not logged in";
    }

    public String show(int messageId) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (currentUser != null) {
            if (mailBoxes.containsKey(currentUser)) {
                for (Mail mail : mailBoxes.get(currentUser)) {
                    if (mail.getMessageId() == messageId) {
                        String separator = System.getProperty("line.separator");
                        String hash = mail.getHash() == null ? "" : mail.getHash();
                        String recipientList = Arrays.toString(mail.getRecipients().toArray());
                        if(aesEstablished){
                            String res = aesEncrypt("from " + mail.getSender());
                            res += separator;
                            res += (aesEncrypt("to " + recipientList.substring(1, recipientList.length() - 1)));
                            res += separator;
                            res += (aesEncrypt("subject " + mail.getSubject()));
                            res += separator;
                            res += (aesEncrypt("data " + mail.getData()));
                            res += separator;
                            res += (aesEncrypt("hash " + hash));
                            return res;
                        }else{
                            return "from " + mail.getSender() + separator +
                                    "to " + recipientList.substring(1, recipientList.length() - 1) + separator +
                                    "subject " + mail.getSubject() + separator +
                                    "data " + mail.getData() + separator +
                                    "hash " + hash;
                        }
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

    public static String rsaDecrypt(String msg, PrivateKey key) throws Exception {
        byte[] encryptedBytes = decode(msg);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedMessage = cipher.doFinal(encryptedBytes);
        return new String(decryptedMessage, "UTF8");
    }

    private String aesEncrypt(String msg) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, this.iv);
        byte[] cipherText = cipher.doFinal(msg.getBytes());
        return encode(cipherText);
    }

    private String aesDecrypt(String msg) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, this.secretKey, this.iv);
        byte[] cipherText = cipher.doFinal(decode(msg));
        return new String(cipherText);
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }
}
