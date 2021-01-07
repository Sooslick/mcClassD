package ru.sooslick.outlaw;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class CommandListener implements CommandExecutor {

    private static final String MH_ACCEPT = "§6/manhunt accept §7(/y)";
    private static final String MH_CFG = "§7/manhunt cfg <parameter>";
    private static final String MH_EXCLUDE = "§6/manhunt exclude §7(/mh e)";
    private static final String MH_HELP = "§6/manhunt help";
    private static final String MH_JOIN_REQUEST = "§6/manhunt joinrequest";
    private static final String MH_START = "§7/manhunt start";
    private static final String MH_SUGGEST = "§6/manhunt suggest §7(/mh s)";
    private static final String MH_VOTE = "§6/manhunt votestart §7(/mh v)";

    public  static final String COMMAND_MANHUNT = "manhunt";
    private static final String COMMAND_VOTE = "votestart";
    private static final String COMMAND_VOTE_ALIAS = "v";
    private static final String COMMAND_SUGGEST = "suggest";
    private static final String COMMAND_SUGGEST_ALIAS = "s";
    private static final String COMMAND_EXCLUDE = "exclude";
    private static final String COMMAND_EXCLUDE_ALIAS = "e";
    private static final String COMMAND_JOIN_REQUEST = "joinrequest";
    private static final String COMMAND_ACCEPT = "accept";
    public  static final String COMMAND_ACCEPT_ALIAS = "y";
    private static final String COMMAND_CFG = "cfg";
    private static final String COMMAND_START = "start";
    private static final String COMMAND_HELP = "help";

    private static final String PERMISSION_START = "classd.force.start";

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        //If command is accept (/y)
        //only one action assigned to /y command, just execute
        if (command.getName().equals(COMMAND_ACCEPT_ALIAS)) {
            return executePlayerCommand(sender, Engine.getInstance()::acceptJoinRequest);
        }

        //if command is manhunt
        //send help if no subcommands passed
        if (args.length == 0) {
            printInfo(sender);
            return true;
        }
        //determine subcommand
        switch (args[0].toLowerCase()) {
            case COMMAND_VOTE:
            case COMMAND_VOTE_ALIAS:
                return executePlayerCommand(sender, Engine.getInstance()::voteStart);

            case COMMAND_SUGGEST:
            case COMMAND_SUGGEST_ALIAS:
                return executePlayerCommand(sender, Engine.getInstance()::suggest);

            case COMMAND_EXCLUDE:
            case COMMAND_EXCLUDE_ALIAS:
                return executePlayerCommand(sender, Engine.getInstance()::exclude);

            case COMMAND_JOIN_REQUEST:
                return executePlayerCommand(sender, Engine.getInstance()::joinRequest);

            case COMMAND_ACCEPT:
                return executePlayerCommand(sender, Engine.getInstance()::acceptJoinRequest);

            case COMMAND_CFG:
                String message = args.length == 1 ? MH_CFG + Cfg.availableParameters() : Cfg.getValue(args[1]);
                sender.sendMessage(message);
                return true;

            case COMMAND_START:
                if (!sender.hasPermission(PERMISSION_START))
                    sender.sendMessage(Messages.NOT_PERMITTED);
                else
                    Engine.getInstance().forceStartGame(sender);
                return true;

            case COMMAND_HELP:
                printHelpInfo(sender);
                //no break, print default info after rules
            default:
                printInfo(sender);
        }
        return true;
    }

    ////////////////////// helper

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
        if (lobbyAvailable) { major.add(MH_VOTE); major.add(MH_SUGGEST); major.add(MH_EXCLUDE); } else { minor.add(MH_VOTE); minor.add(MH_SUGGEST); minor.add(MH_EXCLUDE); }
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
        s.sendMessage(Engine.getInstance().getGameMode().getDescription());
    }

    /////////////////////////// checks & executors

    private boolean executePlayerCommand(CommandSender sender, Consumer<Player> action) {
        if (isPlayerSenderDenyConsole(sender))
            action.accept((Player) sender);
        return true;
    }

    private boolean isPlayerSenderDenyConsole(CommandSender sender) {
        if (sender instanceof Player)
            return true;
        sender.sendMessage(Messages.CONSOLE_CANNOT_DO_THIS);
        return false;
    }
}
