package ru.sooslick.outlaw;

import com.google.common.collect.ImmutableMap;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.jetbrains.annotations.NotNull;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class CommandListener implements CommandExecutor, Listener {

    private static final String MH_ACCEPT = "§6/manhunt accept §7(/y)";
    private static final String MH_CFG = "§7/manhunt cfg <parameter>";
    private static final String MH_EXCLUDE = "§6/manhunt exclude §7(/mh e)";
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
    private static final String COMMAND_EXCLUDE = "exclude";
    private static final String COMMAND_EXCLUDE_ALIAS = "e";
    private static final String COMMAND_JOIN_REQUEST = "joinrequest";
    private static final String COMMAND_ACCEPT = "accept";
    public static final String COMMAND_ACCEPT_ALIAS = "y";
    private static final String COMMAND_CFG = "cfg";
    private static final String COMMAND_START = "start";
    private static final String COMMAND_HELP = "help";

    private static final String PERMISSION_START = "classd.force.start";

    private static final ImmutableMap<String, String> OPTIONAL_COMMANDS = ImmutableMap.copyOf(new HashMap<String, String>() {{
        put(COMMAND_VOTE, MH_VOTE);
        put(COMMAND_SUGGEST, MH_SUGGEST);
        put(COMMAND_EXCLUDE, MH_EXCLUDE);
        put(COMMAND_JOIN_REQUEST, MH_JOIN_REQUEST);
        put(COMMAND_ACCEPT, MH_ACCEPT);
        put(COMMAND_CFG, MH_CFG);
        put(COMMAND_START, MH_START);
    }});

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
                String message = args.length == 1 ? MH_CFG + Cfg.formatAvailableParameters() : Cfg.getValue(args[1]);
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

    private List<String> availableCommands(CommandSender s, boolean includeHelp) {
        Engine e = Engine.getInstance();
        List<String> major = new LinkedList<>();

        //conditions
        boolean isPlayer = s instanceof Player;
        boolean isGame = e.getGameState() == GameState.GAME;
        boolean lobbyAvailable = !isGame && isPlayer;
        boolean acceptAvailable = isGame && e.getOutlaw().getPlayer().equals(s);
        boolean joinRequestAvailable = isGame && isPlayer && ((Player) s).getGameMode() == GameMode.SPECTATOR;
        boolean canForceStart = !isGame && s.hasPermission(PERMISSION_START);

        //filtering
        if (lobbyAvailable) {
            major.add(COMMAND_VOTE);
            major.add(COMMAND_SUGGEST);
            major.add(COMMAND_EXCLUDE);
        }
        if (canForceStart)
            major.add(COMMAND_START);
        if (acceptAvailable)
            major.add(COMMAND_ACCEPT);
        else if (joinRequestAvailable)
            major.add(COMMAND_JOIN_REQUEST);
        if (includeHelp) {
            major.add(COMMAND_CFG);
            major.add(COMMAND_HELP);
        }
        return major;
    }

    private void printInfo(CommandSender s) {
        List<String> major = availableCommands(s, false);
        List<String> minor = new LinkedList<>();

        s.sendMessage(Messages.COMMANDS_AVAILABLE);
        s.sendMessage(MH_HELP);                     //always send help
        OPTIONAL_COMMANDS.forEach((key, value) -> {
            if (major.contains(key)) s.sendMessage(value);
            else minor.add(value);
        });
        s.sendMessage(Messages.COMMANDS_UNAVAILABLE);
        minor.forEach(s::sendMessage);
    }

    private void printHelpInfo(CommandSender s) {
        s.sendMessage(Messages.RULES_GAMEMODE);
        try {
            s.sendMessage(Engine.getInstance().getGameMode().getDescription());
        } catch (Exception e) {
            LoggerUtil.exception(e);
        }
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

    //////////////////////////// tab completion

    @EventHandler
    public void onTabComplete(TabCompleteEvent e) {
        String[] args = e.getBuffer().replaceAll("\\s+", " ").trim().split(" ");
        if (args.length < 1 || args[0].contains(COMMAND_ACCEPT_ALIAS))
            return;

        if (args.length == 1)
            e.setCompletions(availableCommands(e.getSender(), true));
        else if (args.length == 2)
            if (args[1].equals(COMMAND_CFG))
                e.setCompletions(Cfg.availableParameters());
            else
                e.setCompletions(availableCommands(e.getSender(), true).stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList()));
        else if (args.length == 3 && args[1].equals(COMMAND_CFG))
            e.setCompletions(Cfg.availableParameters().stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList()));
    }
}
