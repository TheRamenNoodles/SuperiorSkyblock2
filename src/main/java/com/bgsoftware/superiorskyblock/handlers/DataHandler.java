package com.bgsoftware.superiorskyblock.handlers;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.utils.database.CachedResultSet;
import com.bgsoftware.superiorskyblock.utils.database.SQLHelper;
import com.bgsoftware.superiorskyblock.island.SIsland;
import com.bgsoftware.superiorskyblock.island.SPlayerRole;
import com.bgsoftware.superiorskyblock.utils.exceptions.HandlerLoadException;
import com.bgsoftware.superiorskyblock.utils.threads.Executor;
import com.bgsoftware.superiorskyblock.wrappers.SSuperiorPlayer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("WeakerAccess")
public final class DataHandler {

    private final SuperiorSkyblockPlugin plugin;
    private final DatabaseType database;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public DataHandler(SuperiorSkyblockPlugin plugin) throws HandlerLoadException {
        this.plugin = plugin;
        this.database = DatabaseType.fromName(plugin.getSettings().databaseType);

        if(database == DatabaseType.SQLite){
            try {
                File file = new File(plugin.getDataFolder(), "database.db");
                if (!file.exists()) {
                    try {
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return;
                    }
                }
            }catch(Exception ex){
                throw new HandlerLoadException(ex, HandlerLoadException.ErrorLevel.SERVER_SHUTDOWN);
            }
        }

        if(!SQLHelper.createConnection(plugin)){
            throw new HandlerLoadException("Couldn't connect to the database.\nMake sure all information is correct.", HandlerLoadException.ErrorLevel.SERVER_SHUTDOWN);
        }

        loadDatabase();
    }

    public void saveDatabase(boolean async) {
        if (async && Bukkit.isPrimaryThread()) {
            Executor.async(() -> saveDatabase(false));
            return;
        }

        try{
            //Saving grid
            SQLHelper.executeUpdate("DELETE FROM grid;");
            plugin.getGrid().executeGridInsertStatement(false);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void loadDatabase(){
        //Creating default islands table
        SQLHelper.executeUpdate("CREATE TABLE IF NOT EXISTS islands (" +
                "owner VARCHAR(36) PRIMARY KEY, " +
                "center TEXT, " +
                "teleportLocation TEXT, " +
                "members TEXT, " +
                "banned TEXT, " +
                "permissionNodes TEXT, " +
                "upgrades TEXT, " +
                "warps TEXT, " +
                "islandBank TEXT, " +
                "islandSize INTEGER, " +
                "blockLimits TEXT, " +
                "teamLimit INTEGER, " +
                "cropGrowth DECIMAL, " +
                "spawnerRates DECIMAL," +
                "mobDrops DECIMAL, " +
                "discord TEXT, " +
                "paypal TEXT, " +
                "warpsLimit INTEGER, " +
                "bonusWorth TEXT, " +
                "locked BOOLEAN, " +
                "blockCounts TEXT, " +
                "name TEXT, " +
                "visitorsLocation TEXT, " +
                "description TEXT, " +
                "ratings TEXT, " +
                "missions TEXT, " +
                "settings TEXT, " +
                "ignored BOOLEAN, " +
                "generator TEXT, " +
                "generatedSchematics TEXT, " +
                "schemName TEXT, " +
                "uniqueVisitors TEXT, " +
                "unlockedWorlds TEXT" +
                ");");

        //Creating default players table
        SQLHelper.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
                "player VARCHAR(36) PRIMARY KEY, " +
                "teamLeader VARCHAR(36), " +
                "name TEXT, " +
                "islandRole TEXT, " +
                "textureValue TEXT, " +
                "disbands INTEGER, " +
                "toggledPanel BOOLEAN," +
                "islandFly BOOLEAN," +
                "borderColor TEXT," +
                "lastTimeStatus TEXT," +
                "missions TEXT," +
                "language TEXT," +
                "toggledBorder BOOLEAN" +
                ");");

        //Creating default grid table
        SQLHelper.executeUpdate("CREATE TABLE IF NOT EXISTS grid (" +
                "lastIsland TEXT, " +
                "stackedBlocks TEXT, " +
                "maxIslandSize INTEGER, " +
                "world TEXT" +
                ");");

        if(!containsGrid())
            plugin.getGrid().executeGridInsertStatement(false);

        //Creating default stacked-blocks table
        SQLHelper.executeUpdate("CREATE TABLE IF NOT EXISTS stackedBlocks (" +
                "world TEXT, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER, " +
                "amount TEXT" +
                ");");

        addColumnIfNotExists("bonusWorth", "islands", "'0'", "TEXT");
        addColumnIfNotExists("warpsLimit", "islands", String.valueOf(plugin.getSettings().defaultWarpsLimit), "INTEGER");
        addColumnIfNotExists("disbands", "players", String.valueOf(plugin.getSettings().disbandCount), "INTEGER");
        addColumnIfNotExists("locked", "islands", "0", "BOOLEAN");
        addColumnIfNotExists("blockCounts", "islands", "''", "TEXT");
        addColumnIfNotExists("toggledPanel", "players", "0", "BOOLEAN");
        addColumnIfNotExists("islandFly", "players", "0", "BOOLEAN");
        addColumnIfNotExists("name", "islands", "''", "TEXT");
        addColumnIfNotExists("borderColor", "players", "'BLUE'", "TEXT");
        addColumnIfNotExists("lastTimeStatus", "players", "'-1'", "TEXT");
        addColumnIfNotExists("visitorsLocation", "islands", "''", "TEXT");
        addColumnIfNotExists("description", "islands", "''", "TEXT");
        addColumnIfNotExists("ratings", "islands", "''", "TEXT");
        addColumnIfNotExists("missions", "islands", "''", "TEXT");
        addColumnIfNotExists("missions", "players", "''", "TEXT");
        addColumnIfNotExists("settings", "islands", "'" + getDefaultSettings() + "'", "TEXT");
        addColumnIfNotExists("ignored", "islands", "0", "BOOLEAN");
        addColumnIfNotExists("generator", "islands", "'" + getDefaultGenerator() + "'", "TEXT");
        addColumnIfNotExists("generatedSchematics", "islands", "'normal'", "TEXT");
        addColumnIfNotExists("schemName", "islands", "''", "TEXT");
        addColumnIfNotExists("language", "players", "'en-US'", "TEXT");
        addColumnIfNotExists("uniqueVisitors", "islands", "''", "TEXT");
        addColumnIfNotExists("unlockedWorlds", "islands", "''", "TEXT");
        addColumnIfNotExists("toggledBorder", "players", "0", "BOOLEAN");

        SuperiorSkyblockPlugin.log("Starting to load players...");

        SQLHelper.executeQuery("SELECT * FROM players;", resultSet -> {
            ExecutorService executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder().setNameFormat("SuperiorSkyblock Players Loader #%d").build());

            while (resultSet.next()) {
                CachedResultSet cachedResultSet = new CachedResultSet(resultSet);
                executor.execute(() -> plugin.getPlayers().loadPlayer(cachedResultSet));
            }

            try {
                executor.shutdown();
                if(!executor.awaitTermination(3, TimeUnit.MINUTES)){
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    throw new RuntimeException("Loading players timed out.");
                }
            }catch(InterruptedException ex){
                ex.printStackTrace();
            }
        });

        SuperiorSkyblockPlugin.log("Finished players!");
        SuperiorSkyblockPlugin.log("Starting to load islands...");

        SQLHelper.executeQuery("SELECT * FROM islands;", resultSet -> {
            ExecutorService executor = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder().setNameFormat("SuperiorSkyblock Islands Loader #%d").build());

            while (resultSet.next()) {
                CachedResultSet cachedResultSet = new CachedResultSet(resultSet);
                executor.execute(() -> plugin.getGrid().createIsland(cachedResultSet));
            }

            try {
                executor.shutdown();
                if(!executor.awaitTermination(3, TimeUnit.MINUTES)){
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    throw new RuntimeException("Loading islands timed out.");
                }
            }catch(InterruptedException ex){
                ex.printStackTrace();
            }
        });

        SuperiorSkyblockPlugin.log("Finished islands!");
        SuperiorSkyblockPlugin.log("Starting to load grid...");

        SQLHelper.executeQuery("SELECT * FROM grid;", resultSet -> {
            if (resultSet.next()) {
                plugin.getGrid().loadGrid(resultSet);
            }
        });

        SuperiorSkyblockPlugin.log("Finished grid!");
        SuperiorSkyblockPlugin.log("Starting to load stacked blocks...");

        SQLHelper.executeQuery("SELECT * FROM stackedBlocks;", resultSet -> {
            while (resultSet.next()) {
                plugin.getGrid().loadStackedBlocks(resultSet);
            }
        });

        SuperiorSkyblockPlugin.log("Finished stacked blocks!");


        /*
         *  Because of a bug caused leaders to be guests, I am looping through all the players and trying to fix it here.
         */

        for(SuperiorPlayer superiorPlayer : plugin.getPlayers().getAllPlayers()){
            if(superiorPlayer.getIslandLeader().getUniqueId().equals(superiorPlayer.getUniqueId()) && superiorPlayer.getIsland() != null && !superiorPlayer.getPlayerRole().isLastRole()){
                SuperiorSkyblockPlugin.log("[WARN] Seems like " + superiorPlayer.getName() + " is an island leader, but have a guest role - fixing it...");
                superiorPlayer.setPlayerRole(SPlayerRole.lastRole());
            }
        }

    }

    private String getDefaultSettings() {
        StringBuilder stringBuilder = new StringBuilder();
        plugin.getSettings().defaultSettings.forEach(setting -> stringBuilder.append(";").append(setting));
        return stringBuilder.length() == 0 ? stringBuilder.toString() : stringBuilder.substring(1);
    }

    private String getDefaultGenerator() {
        StringBuilder stringBuilder = new StringBuilder();
        plugin.getSettings().defaultGenerator.forEach((key, value) -> stringBuilder.append(",").append(key).append("=").append(value));
        return stringBuilder.length() == 0 ? stringBuilder.toString() : stringBuilder.substring(1);
    }

    public void closeConnection(){
        SQLHelper.close();
    }

    public void insertIsland(Island island){
        Executor.async(() -> {
            if(!containsIsland(island)){
                ((SIsland) island).executeInsertStatement(true);
            }else {
                ((SIsland) island).executeUpdateStatement(true);
            }
        });
    }

    private boolean containsIsland(Island island){
        return SQLHelper.doesConditionExist(String.format("SELECT * FROM islands WHERE owner = '%s';", island.getOwner().getUniqueId()));
    }

    public void deleteIsland(Island island){
        Executor.async(() -> SQLHelper.executeUpdate("DELETE FROM islands WHERE owner = '" + island.getOwner().getUniqueId() + "';"));
    }

    public void insertPlayer(SuperiorPlayer player){
        if(!containsPlayer(player)) {
            ((SSuperiorPlayer) player).executeInsertStatement(true);
        }else{
            ((SSuperiorPlayer) player).executeUpdateStatement(true);
        }
    }

    private boolean containsPlayer(SuperiorPlayer player){
        return SQLHelper.doesConditionExist(String.format("SELECT * FROM players WHERE player = '%s';", player.getUniqueId()));
    }

    private boolean containsGrid(){
        return SQLHelper.doesConditionExist("SELECT * FROM grid;");
    }

    private void addColumnIfNotExists(String column, String table, String def, String type) {
        String defaultSection = " DEFAULT " + def;

        if(database == DatabaseType.MySQL) {
            column = "COLUMN " + column;
            if(type.equals("TEXT"))
                defaultSection = "";
        }

        String statementStr = "ALTER TABLE " + table + " ADD " + column + " " + type + defaultSection + ";";

        try(PreparedStatement statement = SQLHelper.buildStatement(statementStr)){
            statement.executeUpdate();
        }catch(SQLException ex){
            if(!ex.getMessage().toLowerCase().contains("duplicate")) {
                System.out.println("Statement: " + statementStr);
                ex.printStackTrace();
            }
        }
    }

    private enum DatabaseType{

        MySQL,
        SQLite;

        private static DatabaseType fromName(String name){
            return name.equalsIgnoreCase("MySQL") ? MySQL : SQLite;
        }

    }

}
