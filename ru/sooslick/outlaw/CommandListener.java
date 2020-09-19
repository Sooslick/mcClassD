package ru.sooslick.outlaw;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandListener implements CommandExecutor {

    private final String COMMAND_VOTE = "votestart";
    private final String COMMAND_VOTE_ALIAS = "v";
    private final String COMMAND_SUGGEST = "suggest";

    Engine engine;

    public CommandListener(Engine e) {
        engine = e;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            printInfo(sender);
        }
        switch (args[0]) {
            case COMMAND_VOTE:
            case COMMAND_VOTE_ALIAS:
                if (sender instanceof Player)       //todo refactor: action else print
                    engine.voteStart((Player) sender);
                else
                    printInfo(sender);
                break;
            case COMMAND_SUGGEST:
                if (sender instanceof Player)
                    engine.suggest((Player) sender);
                else
                    printInfo(sender);
                break;
            default:
                printInfo(sender);
        }
        return true;
    }

    private void printInfo(CommandSender s) {
        s.sendMessage("commands:");
        s.sendMessage("/outlaw votestart");
        s.sendMessage("/outlaw suggest");
        //todo check gamestate
        //todo check instanceof
    }
}
