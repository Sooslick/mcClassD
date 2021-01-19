package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import ru.sooslick.outlaw.roles.Hunter;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class StatsCollector {
    private static final String DF = "0.#";
    private static final String LF = "\n§6";
    private static final String SEMICOLON = ":§e ";
    private static final String SEPARATOR = "________________\n";

    private final DecimalFormat df;

    private final HashMap<Hunter, StatValue> damageByOutlaw;
    private final HashMap<Hunter, StatValue> damageToOutlaw;
    private final HashMap<Hunter, StatValue> damageTotal;
    private final HashMap<Hunter, StatValue> deaths;

    private double outlawDamage;
    private int chickens;

    public StatsCollector() {
        df = new DecimalFormat(DF);
        damageByOutlaw = new HashMap<>();
        damageToOutlaw = new HashMap<>();
        damageTotal = new HashMap<>();
        deaths = new HashMap<>();
        outlawDamage = 0;
        chickens = 0;
    }

    public void countVictimDamage(Hunter hunter, double damage) {
        outlawDamage += damage;
        addHunterStat(damageToOutlaw, hunter, damage);
    }

    public void countHunterDamage(Hunter hunter, double damage, boolean byOutlaw) {
        addHunterStat(damageTotal, hunter, damage);
        if (byOutlaw)
            addHunterStat(damageByOutlaw, hunter, damage);
    }

    public void countDeath(Hunter hunter) {
        addHunterStat(deaths, hunter, 1);
        Bukkit.broadcastMessage(String.format(Messages.STATS_DEATH_COUNTER, df.format(calcTotal(deaths))));
    }

    public void countChicken() {
        chickens++;
    }

    public void scheduleBroadcast() {
        BukkitScheduler sch = Bukkit.getScheduler();
        // DAMAGE TO OUTLAW
        long baseDelay = 80;
        long delay = 280;
        if (damageToOutlaw.size() > 0) {
            sch.scheduleSyncDelayedTask(Engine.getInstance(), () -> {
                StringBuilder sb = new StringBuilder(SEPARATOR)
                        .append(Messages.STATS_DAMAGE_TO_OUTLAW);
                damageToOutlaw.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(e -> sb
                                .append(LF)
                                .append(e.getKey().getName())
                                .append(SEMICOLON)
                                .append(df.format(e.getValue().get())));
                sb.append(LF).append(Messages.STATS_TOTAL_DAMAGE).append(df.format(outlawDamage));
                Bukkit.broadcastMessage(sb.toString());
            }, baseDelay);
            baseDelay += delay;
        }
        // DAMAGE BY OUTLAW
        if (damageByOutlaw.size() > 0) {
            sch.scheduleSyncDelayedTask(Engine.getInstance(), () -> {
                StringBuilder sb = new StringBuilder(SEPARATOR)
                        .append(Messages.STATS_DAMAGE_BY_OUTLAW);
                damageByOutlaw.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(e -> sb
                                .append(LF)
                                .append(e.getKey().getName())
                                .append(SEMICOLON)
                                .append(df.format(e.getValue().get())));
                sb.append(LF).append(Messages.STATS_TOTAL_DAMAGE).append(df.format(calcTotal(damageByOutlaw)));
                Bukkit.broadcastMessage(sb.toString());
            }, baseDelay);
            baseDelay += delay;
        }
        // DAMAGE OVERALL
        if (damageTotal.size() > 0) {
            sch.scheduleSyncDelayedTask(Engine.getInstance(), () -> {
                StringBuilder sb = new StringBuilder(SEPARATOR)
                        .append(Messages.STATS_DAMAGE_OVERALL);
                damageTotal.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(e -> sb
                                .append(LF)
                                .append(e.getKey().getName())
                                .append(SEMICOLON)
                                .append(df.format(e.getValue().get())));
                sb.append(LF).append(Messages.STATS_TOTAL_DAMAGE).append(df.format(calcTotal(damageTotal)));
                Bukkit.broadcastMessage(sb.toString());
            }, baseDelay);
            baseDelay += delay;
        }
        // DAMAGE DEATHS
        if (deaths.size() > 0) {
            sch.scheduleSyncDelayedTask(Engine.getInstance(), () -> {
                StringBuilder sb = new StringBuilder(SEPARATOR)
                        .append(Messages.STATS_DEATH_HUNTERS);
                deaths.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(e -> sb
                                .append(LF)
                                .append(e.getKey().getName())
                                .append(SEMICOLON)
                                .append(df.format(e.getValue().get())));
                sb.append(LF).append(String.format(Messages.STATS_DEATH_COUNTER, df.format(calcTotal(deaths))));
                Bukkit.broadcastMessage(sb.toString());
            }, baseDelay);
            baseDelay += delay / 2;
        }
        //CHICKEN
        sch.scheduleSyncDelayedTask(Engine.getInstance(), () -> {
            if (chickens > 1)
                Bukkit.broadcastMessage(String.format(Messages.STATS_CHICKEN, chickens));
        }, baseDelay);
    }

    private void addHunterStat(HashMap<Hunter, StatValue> stat, Hunter hunter, double addVal) {
        if (hunter != null) {
            StatValue currVal = stat.get(hunter);
            if (currVal == null)
                stat.put(hunter, new StatValue(addVal));
            else
                currVal.add(addVal);
        }
    }

    private double calcTotal(HashMap<Hunter, StatValue> stat) {
        return stat.values().stream().mapToDouble(StatValue::get).sum();
    }

    private static class StatValue implements Comparable<StatValue> {
        double value;

        private StatValue(double initValue) {
            value = initValue;
        }

        private void add(double value) {
            this.value += value;
        }

        private double get() {
            return value;
        }

        @Override
        public int compareTo(@NotNull StatsCollector.StatValue other) {
            return Double.compare(value, other.get());
        }
    }
}
