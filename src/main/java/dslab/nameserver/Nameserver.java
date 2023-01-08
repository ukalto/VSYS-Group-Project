package dslab.nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver {

    private NameserverRemote nameserverRemote;
    private INameserverRemote iNameserverRemote;
    private final InputStream inputStream;
    private final PrintStream outputStream;
    private final String componentId;
    private final Config config;
    private final Shell shell;
    private Registry registry;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.inputStream = in;
        this.outputStream = out;
        this.shell = new Shell(in, out);
        shell.setPrompt(componentId + "> ");
        shell.register(this);
    }

    @Command
    @Override
    public void run() {
        String rootId = config.getString("root_id");
        String registryHost = config.getString("registry.host");
        int registryPort = config.getInt("registry.port");

        try {
            if (config.containsKey("domain")) {
                // not root server
                Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
                INameserverRemote rootNameserver = (INameserverRemote) registry.lookup(rootId);
                nameserverRemote = new NameserverRemote(config.getString("domain"));
                iNameserverRemote = (INameserverRemote) UnicastRemoteObject.exportObject(nameserverRemote, 0);
                rootNameserver.registerNameserver(config.getString("domain"), iNameserverRemote);
            } else {
                // root server
                this.registry = LocateRegistry.createRegistry(registryPort);
                nameserverRemote = new NameserverRemote(null);
                registry.rebind(rootId, UnicastRemoteObject.exportObject(nameserverRemote, 0));
            }
            shell.run();
            System.out.println("Bye, exiting the shell!");
        } catch (RemoteException | NotBoundException | InvalidDomainException | AlreadyRegisteredException e) {
            shutdown();
        }
    }

    @Command
    @Override
    public void nameservers() {
        nameserverRemote.printNameServers(outputStream);
    }

    @Command
    @Override
    public void addresses() {
        nameserverRemote.printMailServers(outputStream);
    }


    @Command
    @Override
    public void shutdown() {
        try {
            UnicastRemoteObject.unexportObject(nameserverRemote, true);
            inputStream.close();
            if (!config.containsKey("domain")) {
                this.registry.unbind(config.getString("root_id"));
                UnicastRemoteObject.unexportObject(this.registry, true);
            }
        } catch (NotBoundException | IOException e) {
        }
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
