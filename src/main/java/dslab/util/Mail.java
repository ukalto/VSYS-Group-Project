package dslab.util;

import java.util.List;

public class Mail {

    private int messageId;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String data;
    private String hash;

    public Mail() {}

    public Mail(String sender, List<String> recipients, String subject, String data) {
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.data = data;
    }

    public Mail(int messageId, String sender, List<String> recipients, String subject, String data, String hash) {
        this.messageId = messageId;
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.data = data;
        this.hash = hash;
    }

    public Mail(Mail that) {
        this(that.getMessageId(), that.getSender(), that.getRecipients(), that.getSubject(), that.getData(), that.getHash());
    }

    public int getMessageId() {
        return messageId;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public String getData() {
        return data;
    }

    public String getHash() { return hash; }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setHash(String hash) {this.hash = hash;}

    @Override
    public String toString() {
        return messageId +
                " " + sender +
                " " + subject;
    }
}
