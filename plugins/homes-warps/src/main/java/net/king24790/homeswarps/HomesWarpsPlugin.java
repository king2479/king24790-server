package net.king24790.homeswarps;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomesWarpsPlugin extends JavaPlugin implements CommandExecutor {
    private static final String PREFIX = ChatColor.GOLD + "[Homes] " + ChatColor.RESET;

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory");
        }
        dataFile = new File(getDataFolder(), "data.yml");
        data = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                data.load(dataFile);
            } catch (IOException | InvalidConfigurationException exception) {
                throw new IllegalStateException("Could not load homes/warps data from " + dataFile, exception);
            }
        }

        String[] commands = { "sethome", "home", "setwarp", "warp", "warps", "delwarp" };
        for (String commandName : commands) {
            if (getCommand(commandName) == null) {
                throw new IllegalStateException("Missing command declaration: " + commandName);
            }
            getCommand(commandName).setExecutor(this);
        }
        getLogger().info("Personal homes and operator-managed warps are ready.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("sethome".equals(name)) {
            return setHome(sender, args);
        }
        if ("home".equals(name)) {
            return goHome(sender, args);
        }
        if ("setwarp".equals(name)) {
            return setWarp(sender, args);
        }
        if ("warp".equals(name)) {
            return warp(sender, args);
        }
        if ("warps".equals(name)) {
            return listWarps(sender, args);
        }
        if ("delwarp".equals(name)) {
            return deleteWarp(sender, args);
        }
        return false;
    }

    private boolean setHome(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
            return true;
        }
        Player player = (Player) sender;
        String path = "homes." + player.getUniqueId().toString();
        YamlConfiguration updated = copyData();
        updated.set(path, serialize(player.getLocation()));
        if (save(updated)) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "Your home has been set.");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save your home. Please try again.");
        }
        return true;
    }

    private boolean goHome(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
            return true;
        }
        Player player = (Player) sender;
        Location destination = readLocation("homes." + player.getUniqueId().toString());
        teleport(player, destination, "Your home is not set or its world is unavailable. Use /sethome in a loaded world.");
        return true;
    }

    private boolean setWarp(CommandSender sender, String[] args) {
        if (!requireOperator(sender)) {
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (args.length != 1 || !isValidName(args[0])) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /setwarp <name> (letters, numbers, _ or -, up to 32 characters)");
            return true;
        }
        String name = normalizeName(args[0]);
        YamlConfiguration updated = copyData();
        updated.set("warps." + name, serialize(((Player) sender).getLocation()));
        if (save(updated)) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Warp '" + name + "' has been set.");
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not save the warp. Please try again.");
        }
        return true;
    }

    private boolean warp(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (args.length == 0) {
            return listWarps(sender, args);
        }
        if (args.length != 1 || !isValidName(args[0])) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /warp [name]");
            return true;
        }
        String name = normalizeName(args[0]);
        Location destination = readLocation("warps." + name);
        teleport((Player) sender, destination,
                "Warp '" + name + "' does not exist or its world is unavailable. Use /warps to see available warps.");
        return true;
    }

    private boolean listWarps(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /warps");
            return true;
        }
        ConfigurationSection warps = data.getConfigurationSection("warps");
        if (warps == null || warps.getKeys(false).isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No public warps have been set yet.");
            return true;
        }
        List<String> names = new ArrayList<String>(warps.getKeys(false));
        Collections.sort(names);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Warps: " + ChatColor.WHITE + join(names));
        return true;
    }

    private boolean deleteWarp(CommandSender sender, String[] args) {
        if (!requireOperator(sender)) {
            return true;
        }
        if (args.length != 1 || !isValidName(args[0])) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /delwarp <name>");
            return true;
        }
        String name = normalizeName(args[0]);
        String path = "warps." + name;
        if (!data.contains(path)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Warp '" + name + "' does not exist.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set(path, null);
        if (save(updated)) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Warp '" + name + "' has been removed.");
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not remove the warp. Please try again.");
        }
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.RED + "This command can only be used by a player.");
        return false;
    }

    private boolean requireOperator(CommandSender sender) {
        if (sender.isOp()) {
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.RED + "Only server operators can manage public warps.");
        return false;
    }

    private boolean requireNoArguments(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "This command does not take arguments.");
        return false;
    }

    private boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,32}");
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(ChatColor.GRAY).append(", ").append(ChatColor.WHITE);
            }
            result.append(value);
        }
        return result.toString();
    }

    private YamlConfiguration copyData() {
        YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(data.saveToString());
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not copy plugin data before update", exception);
        }
        return copy;
    }

    private boolean save(YamlConfiguration updated) {
        File temporary = new File(dataFile.getParentFile(), "data.yml.tmp");
        try {
            updated.save(temporary);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            data = updated;
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not persist homes/warps data: " + exception.getMessage());
            if (temporary.exists() && !temporary.delete()) {
                getLogger().warning("Could not remove incomplete data file " + temporary.getName());
            }
            return false;
        }
    }

    private Map<String, Object> serialize(Location location) {
        Map<String, Object> serialized = new LinkedHashMap<String, Object>();
        serialized.put("world", location.getWorld().getName());
        serialized.put("x", location.getX());
        serialized.put("y", location.getY());
        serialized.put("z", location.getZ());
        serialized.put("yaw", location.getYaw());
        serialized.put("pitch", location.getPitch());
        return serialized;
    }

    private Location readLocation(String path) {
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null || !section.contains("world") || !section.contains("x")
                || !section.contains("y") || !section.contains("z")) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private void teleport(Player player, Location destination, String missingMessage) {
        if (destination == null) {
            player.sendMessage(PREFIX + ChatColor.RED + missingMessage);
            return;
        }
        if (!player.teleport(destination)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Teleport failed. Please try again.");
            return;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Teleported.");
    }
}
