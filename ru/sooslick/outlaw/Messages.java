package ru.sooslick.outlaw;

//Utility class with client messages that might be localized
public class Messages {

    public static final String ABOUT = "§e«Class D» Manhunt gamemode\nType §6/manhunt help §efor more info\nSuggest yourself as Victim: §6/manhunt suggest\n§eType §6/manhunt votestart §eor simply §6/mh v §eto begin\n§ePreferred gamemode: §c%s";
    public static final String BY = "by ";
    public static final String COMMANDS_AVAILABLE = "\n§e - Available commands:";
    public static final String COMMANDS_UNAVAILABLE = "\n§e - Other commands:";
    public static final String COMPASS_NAME = "Victim Tracker";
    public static final String COMPASS_UPDATED = "§aCompass is pointing to §c%s";
    public static final String CONFIG_MODIFIED = "§cGame parameter modified: §e%s = %s";
    public static final String CONSOLE_CANNOT_DO_THIS = "Console cannot do this. Try §6/manhunt help";
    public static final String DEATH_BY_ATTACK = "beaten";
    public static final String DEATH_BY_DEFAULT = "became dead";
    public static final String DEATH_BY_EXPLOSION = "destroyed";
    public static final String DEATH_BY_FALL = "jymped and died";                   //я пригнул и умер (с)
    public static final String DEATH_BY_FIRE = "met the inquisition";
    public static final String DEATH_BY_PROJECTILE = "shot";
    public static final String DEATH_COUNTER = "§eDeath counter: %s";
    public static final String GAME_IS_NOT_RUNNING = "§cGame is not running.";
    public static final String GAME_IS_RUNNING = "§cGame is running.";
    public static final String GAME_STARTED = "§eGame started. Run!";
    public static final String HUNTER_JOINED = "§e%s joined the game as §cHunter";
    public static final String HUNTER_REMINDER = "§cYou're one of hunters";
    public static final String HUNTER_RESPAWN = "§eVictim is still §c%s\n§eTime elapsed: §c%s";
    public static final String HUNTERS_NEARBY = "§cHunters nearby";
    public static final String JOIN_REQUEST_ACCEPTED = "§e%s §cjoined the game as Hunter";
    public static final String JOIN_REQUEST_EXISTS = "§cYou have sent the request already";
    public static final String JOIN_REQUEST_EXPIRED = "§cYour request has expired";
    public static final String JOIN_REQUEST_HUNTER = "§cYou are a Hunter";
    public static final String JOIN_REQUEST_LOBBY = "§cGame is not running, use §e/manhunt votestart §cinstead";
    public static final String JOIN_REQUEST_NOT_EXISTS = "§cYou have no join requests";
    public static final String JOIN_REQUEST_NOTIFICATION = "§e%s §cwants to join the game. Type §e/y §cto accept or just ignore them";
    public static final String JOIN_REQUEST_SENT = "§cJoin request has been sent";
    public static final String JOIN_REQUEST_VICTIM = "§cYou are the Victim. Nice try :P";
    public static final String NAMETAG_IS_INVISIBLE = "§eVictim's nametag is §cINVISIBLE";
    public static final String NAMETAG_IS_VISIBLE = "§eVictim's nametag is §cVISIBLE";
    public static final String NOT_PERMITTED = "§cYou have not permission to do that";
    public static final String ONLY_VICTIM_ALLOWED = "§cOnly Victim can use this command";
    public static final String READY_FOR_GAME = "§eReady for the next game";
    public static final String RULES_GAMEMODE = "§6\nMinecraft Manhunt gamemode\n§eIn this game one of players starts as §cVictim§e and others become §cHunters§e.\nVictim has to complete the gamemode's objective avoiding Hunters and loses on death.\nHunters must prevent Victim from reaching their objective by any means. They have unlimited respawns and spawn with compasses that always point to Victim's location.\n";
    public static final String SELECTED_HANDICAP = "§eDistance handicap: %s";
    public static final String SELECTED_OBJECTIVE = "§eVictim's objective: §c%s";
    public static final String SELECTED_VICTIM = "§eChosen Victim: §c%s";
    public static final String START_COUNTDOWN = "§c%s seconds to start";
    public static final String START_FORCED = "§c%s started the game by force";
    public static final String START_VOTE = "§e%s voted to start game";
    public static final String START_VOTE_INGAME = "§cCannot votestart while game is running";
    public static final String START_VOTE_TWICE = "§cCannot votestart twice";
    public static final String START_VOTES_COUNT = "§e%s / %s votes to start";
    public static final String UNPLAYABLE_WORLD_WARNING = "We strongly recommend to generate a new game world, otherwise it may be unplayable";
    public static final String VICTIM_DEAD = "§cVictim died. §eHunters win!";
    public static final String VICTIM_ESCAPED = "§eVictim escaped and won the game!";
    public static final String VICTIM_OFFLINE = "§cVictim left the game, but there is §eVictim Chicken§c. Kill it!";
    public static final String VICTIM_ONLINE = "§cVictim is back to the game!";
    public static final String VICTIM_REMINDER = "§cYou're still a Victim with a goal to: %s";
    public static final String VOLUNTEER_ALREADY_EXCLUDED = "§cYou have already excluded yourself from Victim list";
    public static final String VOLUNTEER_EXCLUDED = "§e%s doesn't want to be a Victim";
    public static final String VOLUNTEER_LEFT = "§c%s left and was removed from Victim suggesters";
    public static final String VOLUNTEER_SUGGEST = "§e%s proposed himself as a Victim";
    public static final String VOLUNTEER_SUGGEST_INGAME = "§cCannot suggest while game is running";
    public static final String VOLUNTEER_SUGGEST_TWICE = "§cCannot suggest twice";

    //deny constructors
    private Messages() {}
}
