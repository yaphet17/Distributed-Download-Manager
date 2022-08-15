import Client.Client;
import picocli.CommandLine;

import static picocli.CommandLine.*;

@Command(
        name = "DDM",
        description = "start distributed download manager",
        mixinStandardHelpOptions = true,
        subcommands = {
                Server.Server.class,
                Client.class,
                Tracker.Server.class
        }
)
public class main implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(main.class).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
    }


}
