package dslab.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public abstract class DMTPConnectionThread extends Thread {

    protected final Socket socket;
    private boolean quit = false;
    private boolean editMode = false;
    protected Mail mail = new Mail();

    public DMTPConnectionThread(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());

            String request;

            writer.println("ok DMTP");
            writer.flush();

            // read client requests
            while ((request = reader.readLine()) != null) {
                System.out.println("C: " + request);

                String[] parts = request.split("\\s", 2);

                String response = "error missing parameters";

                if (request.startsWith("begin")) {
                    response = "ok";
                    editMode = true;
                    mail = new Mail();
                } else if (!request.startsWith("to") && !request.startsWith("from") && !request.startsWith("subject") && !request.startsWith("data") && !request.startsWith("send")) {
                    response = "error protocol error";
                    quit = true;
                }

                if (!editMode) {
                    response = "error must begin email first";
                } else if (request.startsWith("send")) {
                    response = send();
                    if (response.equals("ok")) editMode = false;
                } else if (request.startsWith("to") && parts.length >= 2) {
                    response = to(parts[1]);
                } else if (request.startsWith("from") && parts.length >= 2) {
                    mail.setSender(parts[1]);
                    response = "ok";
                } else if (request.startsWith("subject") && parts.length >= 2) {
                    mail.setSubject(parts[1]);
                    response = "ok";
                } else if (request.startsWith("data") && parts.length >= 2) {
                    mail.setData(parts[1]);
                    response = "ok";
                }

                if (request.startsWith("quit")) {
                    response = "ok bye";
                    quit = true;
                }

                // print response
                System.out.println("S: " + response);
                writer.println(response);
                writer.flush();
                if (quit) break;
            }

            reader.close();
            writer.close();

        } catch (SocketException e) {
            System.out.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }
    }

    protected abstract String to(String recipients);

    protected abstract String send();
}
