package org.valarpirai.redis;

public class CommandExecutor {
    private IStorage storage;

    public CommandExecutor(IStorage storage) {
        this.storage = storage;
    }

    public String execute(String commandStr) {
        String[] commands = commandStr.split(" ");

        if (commands.length == 0) {
            return null;
        }

        switch (commands[0]) {
            case "PING":
                return "PONG";
            case "GET":
                return storage.get(commands[1]);
            case "SET":
                return storage.set(commands[1], commands[2]);
        }
        return "ERROR";
    }
}
