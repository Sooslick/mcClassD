package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class Cfg {

    public static boolean debugMode;
    public static int minVotestarters;
    public static int votestartTimer;
    public static int spawnRadius;
    public static int spawnDistance;
    public static int alertRadius;
    public static int alertTimeout;
    public static boolean enableEscapeGamemode;
    public static int playzoneSize;
    public static int wallThickness;
    public static int spotSize;
    public static int groundSpotDensity;
    public static int airSpotDensity;
    public static int undergroundSpotDensity;
    public static boolean allowBuildWall;

    private static final String SET = "Set game parameter ";

    public static void readConfig(FileConfiguration f) {
        debugMode = f.getBoolean("debugMode", false);
        int temp;
        boolean b;
        temp = f.getInt("minVotestarters", 1);
        if (minVotestarters != temp) {
            Bukkit.broadcastMessage(SET + "minVotestarters = " + temp);
            minVotestarters = temp;
        }
        temp = f.getInt("votestartTimer", 60);
        if (votestartTimer != temp) {
            Bukkit.broadcastMessage(SET + "votestartTimer = " + temp);
            votestartTimer = temp;
        }
        temp = f.getInt("spawnRadius", 300);
        if (spawnRadius != temp) {
            Bukkit.broadcastMessage(SET + "spawnRadius = " + temp);
            spawnRadius = temp;
        }
        temp = f.getInt("spawnDistance", 240);
        if (spawnDistance != temp) {
            Bukkit.broadcastMessage(SET + "spawnDistance = " + temp);
            spawnDistance = temp;
        }
        temp = f.getInt("alertRadius", 50);
        if (alertRadius != temp) {
            Bukkit.broadcastMessage(SET + "alertRadius = " + temp);
            alertRadius = temp;
        }
        temp = f.getInt("alertTimeout", 60);
        if (alertTimeout != temp) {
            Bukkit.broadcastMessage(SET + "alertTimeout = " + temp);
            alertTimeout = temp;
        }
        b = f.getBoolean("enableEscapeGamemode", false);
        if (enableEscapeGamemode != b) {
            Bukkit.broadcastMessage(SET + "enableEscapeGamemode = " + b);
            enableEscapeGamemode = b;
        }
        temp = f.getInt("playzoneSize", 1000);  //todo validate: must be wider than spawn radius+distance
        if (playzoneSize != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "playzoneSize = " + temp);
            playzoneSize = temp;
        }
        temp = f.getInt("wallThickness", 16);
        if (wallThickness != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "wallThickness = " + temp);
            wallThickness = temp;
        }
        temp = f.getInt("spotSize", 10);
        if (spotSize != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "spotSize = " + temp);
            spotSize = temp;
        }
        temp = f.getInt("groundSpotDensity", 3);
        if (groundSpotDensity != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "groundSpotDensity = " + temp);
            groundSpotDensity = temp;
        }
        temp = f.getInt("airSpotDensity", 2);
        if (airSpotDensity != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "airSpotDensity = " + temp);
            airSpotDensity = temp;
        }
        temp = f.getInt("undergroundSpotDensity", 5);
        if (undergroundSpotDensity != temp) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "undergroundSpotDensity = " + temp);
            undergroundSpotDensity = temp;
        }
        b = f.getBoolean("allowBuildWall", false);
        if (allowBuildWall != b) {
            if (enableEscapeGamemode) Bukkit.broadcastMessage(SET + "allowBuildWall = " + b);
            allowBuildWall = b;
        }
    }

    //todo refactor with adequate reflection mthod
}
