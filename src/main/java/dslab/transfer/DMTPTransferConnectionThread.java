package dslab.transfer;

import dslab.util.DMTPConnectionThread;
import dslab.util.Mail;

import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class DMTPTransferConnectionThread extends DMTPConnectionThread {

    private final LinkedBlockingQueue<Mail> mailQueue;

    public DMTPTransferConnectionThread(Socket socket, LinkedBlockingQueue<Mail> mailQueue) {
        super(socket);
        this.mailQueue = mailQueue;
    }

    protected String to(String recipients) {
        int acceptCounter = 0;
        mail.setRecipients(Arrays.asList(recipients.trim().split("\\s*,\\s*")));
        try {
            for (String recipient : mail.getRecipients()) {
                String domain = recipient.split("@")[1];
                acceptCounter++;
            }
            return "ok " + acceptCounter;
        } catch (IndexOutOfBoundsException e) {
            return "recipient is not a valid email address";
        }
    }

    protected String send() {
        if (mail.getRecipients() == null) {
            return "error no recipients";
        }
        else if (mail.getSender() == null) {
            return "error no sender";
        }
        else if (mail.getSubject() == null) {
            return "error no subject";
        }
        else if (mail.getData() == null) {
            return "error no data";
        }
        else {
            Mail sentEmail = new Mail(mail);
            try {
                mailQueue.put(sentEmail);
            } catch (Exception e) {
                System.out.println("Error when adding Mail to Queue: " + e.getMessage());
            }
        }
        return "ok";
    }
}
