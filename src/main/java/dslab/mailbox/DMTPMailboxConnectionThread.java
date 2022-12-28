package dslab.mailbox;

import dslab.util.Config;
import dslab.util.DMTPConnectionThread;
import dslab.util.Mail;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DMTPMailboxConnectionThread extends DMTPConnectionThread {

    private final ConcurrentHashMap<String, List<Mail>> mailBoxes;
    protected final String domain;
    protected final Config userConfig;

    public DMTPMailboxConnectionThread(Socket socket, Config userConfig, ConcurrentHashMap<String, List<Mail>> mailBoxes, String domain) {
        super(socket);
        this.mailBoxes = mailBoxes;
        this.domain = domain;
        this.userConfig = userConfig;
    }

    @Override
    protected String to(String recipients) {
        int acceptCounter = 0;
        mail.setRecipients(Arrays.asList(recipients.trim().split("\\s*,\\s*")));
        try {
            for (String recipient : mail.getRecipients()) {
                String username = recipient.split("@")[0];
                String domain = recipient.split("@")[1];
                if (this.domain.equals(domain)) {
                    try {
                        userConfig.getString(username);
                        acceptCounter++;
                    } catch (MissingResourceException e) {
                        return "error unknown recipient " + username;
                    }
                }
            }
            return "ok " + acceptCounter;
        } catch (IndexOutOfBoundsException e) {
            return "recipient is not a valid email address";
        }
    }

    @Override
    protected synchronized String send() {
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
            for (String recipient : mail.getRecipients()) {
                String username = recipient.split("@")[0];
                String domain = recipient.split("@")[1];
                Mail sentEmail = new Mail(mail);
                if (this.domain.equals(domain)) {
                    if (!mailBoxes.containsKey(username) || mailBoxes.get(username).size() == 0) {
                        List<Mail> mails = Collections.synchronizedList(new ArrayList<>());
                        sentEmail.setMessageId(1);
                        mails.add(sentEmail);
                        mailBoxes.put(username, mails);
                    } else {
                        // Set message id of previous message + 1
                        sentEmail.setMessageId(mailBoxes.get(username).get(mailBoxes.get(username).size() - 1).getMessageId() + 1);
                        mailBoxes.get(username).add(sentEmail);
                    }
                }
            }
        }
        return "ok";
    }
}
