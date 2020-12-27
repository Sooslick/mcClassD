package ru.sooslick.outlaw;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class CommandListener implements CommandExecutor {

    private static final String MH_ACCEPT = "§6/manhunt accept §7(/y)";
    private static final String MH_CFG = "§7/manhunt cfg <parameter>";
    private static final String MH_HELP = "§6/manhunt help";
    private static final String MH_JOIN_REQUEST = "§6/manhunt joinrequest";
    private static final String MH_START = "§7/manhunt start";
    private static final String MH_SUGGEST = "§6/manhunt suggest §7(/mh s)";
    private static final String MH_VOTE = "§6/manhunt votestart §7(/mh v)";

    public static final String COMMAND_MANHUNT = "manhunt";
    private static final String COMMAND_VOTE = "votestart";
    private static final String COMMAND_VOTE_ALIAS = "v";
    private static final String COMMAND_SUGGEST = "suggest";
    private static final String COMMAND_SUGGEST_ALIAS = "s";
    private static final String COMMAND_JOIN_REQUEST = "joinrequest";
    private static final String COMMAND_ACCEPT = "accept";
    public static final String COMMAND_ACCEPT_ALIAS = "y";
    private static final String COMMAND_CFG = "cfg";
    private static final String COMMAND_START = "start";
    private static final String COMMAND_HELP = "help";

    private static final String PERMISSION_START = "classd.force.start";

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        //todo refactor to two methods for outlaw and for y command
        //If command is accept
        if (command.getName().equals(COMMAND_ACCEPT_ALIAS)) {
            if (sender instanceof Player)
                Engine.getInstance().acceptJoinRequest((Player) sender);
            else
                printConsoleInfo(sender);
            return true;
        }

        //if command is manhunt
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
            case COMMAND_SUGGEST_ALIAS:
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
                    //todo available params?
                    sender.sendMessage(MH_CFG);
                } else {
                    sender.sendMessage(Cfg.getValue(args[1]));
                }
                break;

            case COMMAND_START:
                if (!sender.hasPermission(PERMISSION_START)) {
                    sender.sendMessage(Messages.NOT_PERMITTED);
                } else {
                    Engine.getInstance().forceStartGame(sender);
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
        Engine e = Engine.getInstance();
        List<String> major = new LinkedList<>();
        List<String> minor = new LinkedList<>();
        boolean isPlayer = s instanceof Player;
        boolean isGame = e.getGameState() == GameState.GAME;
        boolean cfgAvailable = !isPlayer;
        boolean lobbyAvailable = !isGame && isPlayer;
        boolean acceptAvailable = isGame && e.getOutlaw().getPlayer().equals(s);
        boolean joinRequestAvailable = isGame && isPlayer && ((Player) s).getGameMode() == GameMode.SPECTATOR;
        boolean canForceStart = !isGame && s.hasPermission(PERMISSION_START);
        if (cfgAvailable) major.add(MH_CFG); else minor.add(MH_CFG);
        if (lobbyAvailable) { major.add(MH_VOTE); major.add(MH_SUGGEST); } else { minor.add(MH_VOTE); minor.add(MH_SUGGEST); }
        if (acceptAvailable) major.add(MH_ACCEPT); else minor.add(MH_ACCEPT);
        if (joinRequestAvailable) major.add(MH_JOIN_REQUEST); else minor.add(MH_JOIN_REQUEST);
        if (canForceStart) major.add(MH_START); else minor.add(MH_START);

        s.sendMessage(Messages.COMMANDS_AVAILABLE);
        s.sendMessage(MH_HELP);                     //always send help
        for (String str : major) s.sendMessage(str);
        s.sendMessage(Messages.COMMANDS_UNAVAILABLE);
        for (String str : minor) s.sendMessage(str);
    }

    private void printHelpInfo(CommandSender s) {
        s.sendMessage(Messages.RULES_GAMEMODE);
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
