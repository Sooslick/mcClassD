package ru.sooslick.outlaw;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandListener implements CommandExecutor {

    private final String COMMAND_VOTE = "votestart";
    private final String COMMAND_VOTE_ALIAS = "v";
    private final String COMMAND_SUGGEST = "suggest";
    private final String COMMAND_JOIN_REQUEST = "joinrequest";
    private final String COMMAND_ACCEPT = "accept";
    private final String COMMAND_CFG = "cfg";
    private final String COMMAND_HELP = "help";

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        //todo refactor to two methods for outlaw and for y command
        //y
        if (command.getName().equals("y")) {
            if (sender instanceof Player)
                Engine.getInstance().acceptJoinRequest((Player) sender);
            else
                printConsoleInfo(sender);
            return true;
        }

        //manhunt
        if (args.length == 0) {
            printInfo(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case COMMAND_VOTE:
            case COMMAND_VOTE_ALIAS:
                if (sender instanceof Player)       //todo refactor: action else print
                    Engine.getInstance().voteStart((Player) sender);  //todo: is possible refactor if sender instanceof Player to method and pass action as param?
                else
                    printConsoleInfo(sender);
                break;
            case COMMAND_SUGGEST:
                if (sender instanceof Player)
                    Engine.getInstance().suggest((Player) sender);
                else
                    printConsoleInfo(sender);
                break;
            case COMMAND_JOIN_REQUEST:
                if (sender instanceof Player)
                    Engine.getInstance().joinRequest((Player) sender);
                else
                    printConsoleInfo(sender);
                break;
            case COMMAND_ACCEPT:
                if (sender instanceof Player)
                    Engine.getInstance().acceptJoinRequest((Player) sender);
                else
                    printConsoleInfo(sender);
                break;
            case COMMAND_CFG:
                if (args.length == 1) {
                    sender.sendMessage("/manhunt cfg <parameter>");
                } else {
                    sender.sendMessage(Cfg.getValue(args[1]));
                }
                break;
            case COMMAND_HELP:
                printHelpInfo(sender);
                //no break, print default info after rules
            default:
                printInfo(sender);
        }
        return true;
    }

    private void printConsoleInfo(CommandSender s) {
        s.sendMessage("Console cannot do this. Try §6/manhunt help");
    }

    private void printInfo(CommandSender s) {
        s.sendMessage("§6\nAvailable commands:\n/manhunt help");                 //always send help
        if (s instanceof Player) {
            if (Engine.getInstance().getGameState().equals(GameState.GAME)) {
                s.sendMessage("§6/manhunt joinrequest\n/manhunt accept §7(/y)");  //send req / accept while game is running
            } else {
                s.sendMessage("§6/manhunt votestart §7(/manhunt v)\n§6/manhunt suggest");          //send vs / suggest otherwise
            }
        }
    }

    private void printHelpInfo(CommandSender s) {
        s.sendMessage("§6\nMinecraft Manhunt gamemode\n" +
                "§eIn this game one of players starts as §cVictim§e and others become §cHunters§e.\n" +
                "Victim has to complete the gamemode's objective avoiding Hunters and loses on death.\n" +
                "Hunters must prevent Victim from reaching their objective by any means. " +
                "They have unlimited respawns and spawn with compasses that always point to Victim's location.");
        if (Cfg.enableEscapeGamemode) { //todo: refactor to gamemode class and impl getRule method
            s.sendMessage("§6\nThe Wall gamemode\n" +
                    "§ePlayers start in square zone restricted by wall of bedrock. " +
                    "This wall has some obsidian spots " +
                    "and Victim has to escape the zone by breaking through one of them.\n" +
                    "Wall thickness: §c" + Cfg.wallThickness + "\n§eZone size: §c" + Cfg.playzoneSize);
        } else {
            s.sendMessage("§6\nMinecraft Any% gamemode\n" +
                    "§eLike in a vanilla Minecraft, Victim has to beat the Ender Dragon " +
                    "while Hunters try to prevent this.");
        }
    }
}
