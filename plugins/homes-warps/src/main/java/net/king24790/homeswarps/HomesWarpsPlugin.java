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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Button;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomesWarpsPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private static final String PREFIX = ChatColor.GOLD + "[Homes] " + ChatColor.RESET;
    private static final String[] APARTMENT_IDS = { "A1", "A2", "B1", "B2" };
    private static final int RENT_IRON_INGOTS = 4;
    private static final long RENT_PERIOD_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final String[] SHOP_ITEMS = {
        "BREAD", "TORCH", "ARROW", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "ENDER_PEARL", "COOKED_BEEF"
    };
    private static final int[] SHOP_BUY_PRICES = { 2, 1, 1, 2, 4, 18, 8, 3 };
    private static final int[] SHOP_SELL_PRICES = { 1, 0, 1, 1, 2, 9, 4, 1 };

    private File dataFile;
    private YamlConfiguration data;
    private World cityWorld;
    private int cityCenterX;
    private int cityCenterZ;
    private int cityFloorY;
    private final List<TemporaryStation> activeSubwayStations = new ArrayList<TemporaryStation>();

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

        cityWorld = getPrimaryWorld();
        if (cityWorld != null) {
            Location spawn = cityWorld.getSpawnLocation();
            cityCenterX = spawn.getBlockX();
            cityCenterZ = spawn.getBlockZ();
            cityFloorY = Math.max(3, spawn.getBlockY() - 2);
            clearCityMonsters(cityWorld);
            refreshApartmentRentSigns(cityWorld);
            buildSubwayStation(cityWorld, cityFloorY, 9, 10);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        String[] commands = { "sethome", "home", "setwarp", "warp", "warps", "delwarp",
            "cityspawn", "citylights", "spawn", "subway", "sub", "psub", "apartments", "rent", "myapartment", "clan",
            "shop", "market", "shopping", "shoppingdistrict" };
        for (String commandName : commands) {
            if (getCommand(commandName) == null) {
                throw new IllegalStateException("Missing command declaration: " + commandName);
            }
            getCommand(commandName).setExecutor(this);
        }
        getLogger().info("Personal homes and operator-managed warps are ready.");
    }

    @Override
    public void onDisable() {
        for (TemporaryStation station : new ArrayList<TemporaryStation>(activeSubwayStations)) {
            Bukkit.getScheduler().cancelTask(station.taskId);
            restoreTemporaryStation(station);
        }
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
        if ("cityspawn".equals(name)) {
            return buildCitySpawn(sender, args);
        }
        if ("citylights".equals(name)) {
            return addCityLights(sender, args);
        }
        if ("spawn".equals(name)) {
            if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
                return true;
            }
            teleportToCitySpawn((Player) sender);
            return true;
        }
        if ("subway".equals(name)) {
            if (args.length == 1 && "station".equalsIgnoreCase(args[0])) {
                if (!requirePlayer(sender)) {
                    return true;
                }
                teleport((Player) sender, new Location(cityWorld, cityCenterX + 9.5,
                        cityFloorY + 3, cityCenterZ + 10.5), "The subway station is not available.");
                return true;
            }
            if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /subway to travel to your clan home or /subway station.");
                return true;
            }
            return subwayToClanHome((Player) sender);
        }
        if ("sub".equals(name)) {
            return summonTemporarySubwayStation(sender, args);
        }
        if ("psub".equals(name)) {
            return permanentSubwayCommand(sender, args);
        }
        if ("clan".equals(name)) {
            return clanCommand(sender, args);
        }
        if ("apartments".equals(name)) {
            return listApartments(sender, args);
        }
        if ("rent".equals(name)) {
            return rentApartment(sender, args);
        }
        if ("myapartment".equals(name)) {
            return myApartment(sender, args);
        }
        if ("shop".equals(name)) {
            return shopCommand(sender, args);
        }
        if ("market".equals(name)) {
            if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
                return true;
            }
            teleport((Player) sender, new Location(cityWorld, cityCenterX + 42.5,
                    cityFloorY + 2, cityCenterZ - 36.5), "The city market is not available.");
            return true;
        }
        if ("shopping".equals(name)) {
            if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
                return true;
            }
            teleport((Player) sender, new Location(cityWorld, cityCenterX + 2.5,
                    cityFloorY + 2, cityCenterZ - 37.5), "The shopping district is not available.");
            return true;
        }
        if ("shoppingdistrict".equals(name)) {
            return buildShoppingDistrict(sender, args);
        }
        return false;
    }

    private boolean buildShoppingDistrict(CommandSender sender, String[] args) {
        if (!requireOperator(sender)) {
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /shoppingdistrict");
            return true;
        }
        if (cityWorld == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "The overworld is not loaded.");
            return true;
        }
        buildShoppingDistrictArea(cityWorld, cityFloorY);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Built the shopping district north of Times Square.");
        return true;
    }

    private boolean summonTemporarySubwayStation(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
            return true;
        }
        if (cityWorld == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "The overworld spawn is not available.");
            return true;
        }

        Player player = (Player) sender;
        Location location = player.getLocation();
        int floorY = location.getBlockY() - 1;
        int minX = location.getBlockX() - 3;
        int maxX = location.getBlockX() + 3;
        int minZ = location.getBlockZ() - 3;
        int maxZ = location.getBlockZ() + 3;
        int minY = floorY;
        int maxY = floorY + 4;
        if (minY < 1 || maxY >= location.getWorld().getMaxHeight()) {
            player.sendMessage(PREFIX + ChatColor.RED + "There is not enough room to build a temporary station here.");
            return true;
        }

        for (TemporaryStation active : activeSubwayStations) {
            if (active.overlaps(location.getWorld(), minX, minY, minZ, maxX, maxY, maxZ)) {
                player.sendMessage(PREFIX + ChatColor.RED + "A temporary subway station is already active nearby.");
                return true;
            }
        }

        TemporaryStation station = new TemporaryStation(location.getWorld(), minX, minY, minZ,
                maxX, maxY, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    station.originalBlocks.add(location.getWorld().getBlockAt(x, y, z).getState());
                }
            }
        }

        buildTemporarySubwayStation(station);
        Location spawn = new Location(cityWorld, cityCenterX + 0.5, cityFloorY + 2,
                cityCenterZ + 0.5, 0.0f, 0.0f);
        if (!player.teleport(spawn)) {
            restoreTemporaryStation(station);
            player.sendMessage(PREFIX + ChatColor.RED + "Teleport to spawn failed; the station was removed.");
            return true;
        }

        activeSubwayStations.add(station);
        station.taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                restoreTemporaryStation(station);
            }
        }, 20L * 120L);
        player.sendMessage(PREFIX + ChatColor.GREEN
                + "Temporary subway station built here. You have been taken to spawn; the station will restore this area in 2 minutes.");
        return true;
    }

    private boolean permanentSubwayCommand(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();
        if (args.length == 0) {
            player.sendMessage(PREFIX + ChatColor.GOLD + "Permanent subway routes:");
            player.sendMessage(ChatColor.YELLOW + "/psub build" + ChatColor.GRAY + " - build a station beside your saved home");
            player.sendMessage(ChatColor.YELLOW + "/psub home" + ChatColor.GRAY + " - your home");
            player.sendMessage(ChatColor.YELLOW + "/psub clan" + ChatColor.GRAY + " - your clan home");
            player.sendMessage(ChatColor.YELLOW + "/psub spawn" + ChatColor.GRAY + " - city spawn");
            player.sendMessage(ChatColor.YELLOW + "/psub market" + ChatColor.GRAY + " - city market");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /psub [build|home|clan|spawn|market]");
            return true;
        }

        String destination = args[0].toLowerCase(Locale.ROOT);
        if ("build".equals(destination)) {
            return buildPersonalSubwayStation(player, playerId);
        }
        if ("home".equals(destination)) {
            teleport(player, readLocation("homes." + playerId),
                    "Your home is not set. Use /sethome first.");
            return true;
        }
        if ("clan".equals(destination)) {
            return subwayToClanHome(player);
        }
        if ("spawn".equals(destination)) {
            if (cityWorld == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "The city spawn is not available.");
                return true;
            }
            teleportToCitySpawn(player);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Travelled to city spawn.");
            return true;
        }
        if ("market".equals(destination)) {
            if (cityWorld == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "The city market is not available.");
                return true;
            }
            teleport(player, new Location(cityWorld, cityCenterX + 42.5,
                    cityFloorY + 2, cityCenterZ - 36.5), "The city market is not available.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /psub [build|home|clan|spawn|market]");
        return true;
    }

    private boolean buildPersonalSubwayStation(Player player, String playerId) {
        Location home = readLocation("homes." + playerId);
        if (home == null) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Set a home first with /sethome.");
            return true;
        }

        String stationPath = "psub.personal." + playerId;
        if (data.contains(stationPath)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Your permanent home subway station is already built.");
            return true;
        }

        int[][] offsets = { { 8, 0 }, { -8, 0 }, { 0, 8 }, { 0, -8 },
            { 8, 8 }, { 8, -8 }, { -8, 8 }, { -8, -8 } };
        int floorY = home.getBlockY() - 1;
        for (int[] offset : offsets) {
            int centerX = home.getBlockX() + offset[0];
            int centerZ = home.getBlockZ() + offset[1];
            if (!canBuildPermanentStation(home.getWorld(), centerX, floorY, centerZ)
                    || isSpawnStationArea(home.getWorld(), centerX, floorY, centerZ)) {
                continue;
            }

            YamlConfiguration updated = copyData();
            updated.set(stationPath, serialize(new Location(home.getWorld(),
                    centerX + 0.5, floorY + 1, centerZ + 0.5)));
            if (!save(updated)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Could not save the subway station location. No blocks were changed.");
                return true;
            }
            buildStationShell(home.getWorld(), centerX, floorY, centerZ);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Permanent subway station built beside your saved home.");
            return true;
        }

        player.sendMessage(PREFIX + ChatColor.RED + "There is no clear space near your home for a station.");
        return true;
    }

    private boolean canBuildPermanentStation(World world, int centerX, int floorY, int centerZ) {
        if (floorY < 1 || floorY + 4 >= world.getMaxHeight()) {
            return false;
        }
        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                if (!world.getBlockAt(x, floorY, z).getType().isSolid()) {
                    return false;
                }
                for (int y = floorY + 1; y <= floorY + 4; y++) {
                    if (!world.getBlockAt(x, y, z).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isSpawnStationArea(World world, int centerX, int floorY, int centerZ) {
        if (world != cityWorld) {
            return false;
        }
        int spawnCenterX = cityCenterX + 9;
        int spawnCenterZ = cityCenterZ + 10;
        int spawnFloorY = cityFloorY + 1;
        return Math.abs(centerX - spawnCenterX) <= 6
                && Math.abs(centerZ - spawnCenterZ) <= 6
                && Math.abs(floorY - spawnFloorY) <= 4;
    }

    private void buildTemporarySubwayStation(TemporaryStation station) {
        buildStationShell(station.world, (station.minX + station.maxX) / 2,
                station.minY, (station.minZ + station.maxZ) / 2);
    }

    private void buildStationShell(World world, int centerX, int floorY, int centerZ) {
        int minX = centerX - 3;
        int maxX = centerX + 3;
        int minZ = centerZ - 3;
        int maxZ = centerZ + 3;
        for (int x = minX; x <= maxX; x++) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    setTemporaryBlock(world.getBlockAt(x, y, z), Material.AIR, (byte) 0);
                }
            }
            for (int z = minZ; z <= maxZ; z++) {
                setTemporaryBlock(world.getBlockAt(x, floorY, z), Material.SMOOTH_BRICK, (byte) 0);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                setTemporaryBlock(world.getBlockAt(minX, y, z), Material.STAINED_CLAY, (byte) 11);
                setTemporaryBlock(world.getBlockAt(maxX, y, z), Material.STAINED_CLAY, (byte) 11);
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                if (x == centerX) {
                    continue;
                }
                setTemporaryBlock(world.getBlockAt(x, y, minZ), Material.STAINED_GLASS, (byte) 3);
                setTemporaryBlock(world.getBlockAt(x, y, maxZ), Material.STAINED_GLASS, (byte) 3);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == centerX && z == centerZ) {
                    setTemporaryBlock(world.getBlockAt(x, floorY + 4, z), Material.SEA_LANTERN, (byte) 0);
                } else if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    setTemporaryBlock(world.getBlockAt(x, floorY + 4, z), Material.QUARTZ_BLOCK, (byte) 0);
                }
            }
        }
    }

    private void buildSpawnSubwayButtons(World world, int centerX, int floorY, int centerZ) {
        String[] routes = { "HOME", "CLAN", "SPAWN", "MARKET" };
        ChatColor[] routeColors = { ChatColor.GREEN, ChatColor.AQUA, ChatColor.YELLOW, ChatColor.LIGHT_PURPLE };
        int[] offsets = { -2, -1, 1, 2 };
        for (int index = 0; index < routes.length; index++) {
            int z = centerZ + offsets[index];
            Block buttonBlock = world.getBlockAt(centerX - 2, floorY + 2, z);
            Button button = new Button(Material.STONE_BUTTON);
            button.setFacingDirection(BlockFace.EAST);
            buttonBlock.setTypeIdAndData(Material.STONE_BUTTON.getId(), button.getData(), false);
            placeSpawnSubwayLabel(world.getBlockAt(centerX + 2, floorY + 2, z), routes[index], routeColors[index]);
        }
    }

    @SuppressWarnings("deprecation")
    private void placeSpawnSubwayLabel(Block block, String route, ChatColor color) {
        org.bukkit.material.Sign material = new org.bukkit.material.Sign(Material.WALL_SIGN);
        material.setFacingDirection(BlockFace.WEST);
        block.setTypeIdAndData(Material.WALL_SIGN.getId(), material.getData(), false);
        Sign sign = (Sign) block.getState();
        sign.setLine(0, ChatColor.GOLD + "MTA SUBWAY");
        sign.setLine(1, color + route);
        sign.setLine(2, ChatColor.YELLOW + "RIGHT CLICK");
        sign.setLine(3, ChatColor.GRAY + "BUTTON");
        sign.update(true, false);
    }

    @EventHandler
    public void onSpawnSubwayButton(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.STONE_BUTTON
                || event.getClickedBlock().getWorld() != cityWorld) {
            return;
        }

        Block button = event.getClickedBlock();
        int centerX = cityCenterX + 9;
        int centerZ = cityCenterZ + 10;
        if (button.getX() != centerX - 2 || button.getY() != cityFloorY + 3) {
            return;
        }

        Player player = event.getPlayer();
        int routeOffset = button.getZ() - centerZ;
        if (routeOffset == -2) {
            teleport(player, readLocation("homes." + player.getUniqueId().toString()),
                    "Your home is not set. Set it with /sethome first.");
        } else if (routeOffset == -1) {
            subwayToClanHome(player);
        } else if (routeOffset == 1) {
            teleportToCitySpawn(player);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Travelled to city spawn.");
        } else if (routeOffset == 2) {
            teleport(player, new Location(cityWorld, cityCenterX + 42.5,
                    cityFloorY + 2, cityCenterZ - 36.5), "The city market is not available.");
        }
    }

    @SuppressWarnings("deprecation")
    private void setTemporaryBlock(Block block, Material material, byte data) {
        block.setTypeIdAndData(material.getId(), data, false);
    }

    private void restoreTemporaryStation(TemporaryStation station) {
        for (int chunkX = station.minX >> 4; chunkX <= station.maxX >> 4; chunkX++) {
            for (int chunkZ = station.minZ >> 4; chunkZ <= station.maxZ >> 4; chunkZ++) {
                station.world.loadChunk(chunkX, chunkZ);
            }
        }
        for (BlockState original : station.originalBlocks) {
            if (!original.update(true, false)) {
                getLogger().warning("Could not restore temporary subway station block at " + original.getLocation() + ".");
            }
        }
        activeSubwayStations.remove(station);
    }

    private static final class TemporaryStation {
        private final World world;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final List<BlockState> originalBlocks = new ArrayList<BlockState>();
        private int taskId;

        private TemporaryStation(World world, int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private boolean overlaps(World otherWorld, int otherMinX, int otherMinY, int otherMinZ,
                int otherMaxX, int otherMaxY, int otherMaxZ) {
            return world.equals(otherWorld)
                    && minX <= otherMaxX && maxX >= otherMinX
                    && minY <= otherMaxY && maxY >= otherMinY
                    && minZ <= otherMaxZ && maxZ >= otherMinZ;
        }
    }

    private boolean shopCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && "list".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(PREFIX + ChatColor.GOLD + "MODERN MARKET - prices per item in emeralds");
            for (int index = 0; index < SHOP_ITEMS.length; index++) {
                sender.sendMessage(ChatColor.YELLOW + SHOP_ITEMS[index].toLowerCase(Locale.ROOT)
                        + ChatColor.GRAY + " | buy " + SHOP_BUY_PRICES[index]
                        + " | sell " + (SHOP_SELL_PRICES[index] == 0 ? "not accepted" : SHOP_SELL_PRICES[index]));
            }
            sender.sendMessage(ChatColor.GRAY + "Use /shop buy <item> <amount> or /shop sell <item> <amount>.");
            return true;
        }
        if (!requirePlayer(sender)) {
            return true;
        }
        if (args.length != 3 || !("buy".equalsIgnoreCase(args[0]) || "sell".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /shop [list] | /shop <buy|sell> <item> <amount>");
            return true;
        }

        Player player = (Player) sender;
        int itemIndex = shopItemIndex(args[1]);
        if (itemIndex < 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "That item is not sold here. Use /shop list.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Amount must be a number from 1 to 64.");
            return true;
        }
        if (amount < 1 || amount > 64) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Amount must be between 1 and 64 per trade.");
            return true;
        }

        boolean buying = "buy".equalsIgnoreCase(args[0]);
        int unitPrice = buying ? SHOP_BUY_PRICES[itemIndex] : SHOP_SELL_PRICES[itemIndex];
        if (unitPrice == 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "The market does not buy that item.");
            return true;
        }
        int totalPrice = unitPrice * amount;
        Material material = Material.matchMaterial(SHOP_ITEMS[itemIndex]);
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        if (buying) {
            if (!removeItems(inventory, Material.EMERALD, totalPrice)) {
                player.sendMessage(PREFIX + ChatColor.RED + "That costs " + totalPrice + " emeralds.");
                return true;
            }
            int delivered = giveItems(inventory, material, amount);
            if (delivered != amount) {
                removeItems(inventory, material, delivered);
                giveItems(inventory, Material.EMERALD, totalPrice);
                player.sendMessage(PREFIX + ChatColor.RED + "Make room in your inventory; emeralds were refunded.");
                return true;
            }
            player.sendMessage(PREFIX + ChatColor.GREEN + "Bought " + amount + " "
                    + material.name().toLowerCase(Locale.ROOT) + " for " + totalPrice + " emeralds.");
            return true;
        }

        if (!removeItems(inventory, material, amount)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You need " + amount + " "
                    + material.name().toLowerCase(Locale.ROOT) + " to sell.");
            return true;
        }
        int paid = giveItems(inventory, Material.EMERALD, totalPrice);
        if (paid != totalPrice) {
            removeItems(inventory, Material.EMERALD, paid);
            giveItems(inventory, material, amount);
            player.sendMessage(PREFIX + ChatColor.RED + "Make room in your inventory; your items were returned.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Sold " + amount + " "
                + material.name().toLowerCase(Locale.ROOT) + " for " + totalPrice + " emeralds.");
        return true;
    }

    private int shopItemIndex(String input) {
        String itemName = input.toUpperCase(Locale.ROOT).replace('-', '_');
        for (int index = 0; index < SHOP_ITEMS.length; index++) {
            if (SHOP_ITEMS[index].equals(itemName)) {
                return index;
            }
        }
        return -1;
    }

    private boolean removeItems(org.bukkit.inventory.PlayerInventory inventory, Material material, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!inventory.containsAtLeast(new ItemStack(material), amount)) {
            return false;
        }
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            inventory.removeItem(new ItemStack(material, stackSize));
            remaining -= stackSize;
        }
        return true;
    }

    private int giveItems(org.bukkit.inventory.PlayerInventory inventory, Material material, int amount) {
        int delivered = 0;
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            Map<Integer, ItemStack> overflow = inventory.addItem(new ItemStack(material, stackSize));
            int undelivered = 0;
            for (ItemStack stack : overflow.values()) {
                undelivered += stack.getAmount();
            }
            delivered += stackSize - undelivered;
            if (undelivered > 0) {
                break;
            }
            remaining -= stackSize;
        }
        return delivered;
    }

    private boolean clanCommand(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Clan commands: create, invite, join, leave, info, sethome, disband.");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();
        if ("create".equals(action)) {
            return createClan(player, playerId, args);
        }
        if ("join".equals(action)) {
            return joinClan(player, playerId, args);
        }
        String clanId = findClan(playerId);
        if (clanId == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "You are not in a clan.");
            return true;
        }
        if ("invite".equals(action)) {
            return inviteToClan(player, playerId, clanId, args);
        }
        if ("leave".equals(action)) {
            return leaveClan(player, playerId, clanId, args);
        }
        if ("info".equals(action)) {
            if (args.length != 1) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan info");
                return true;
            }
            player.sendMessage(PREFIX + ChatColor.GOLD + data.getString("clans." + clanId + ".name", clanId)
                    + ChatColor.YELLOW + " | " + clanMemberCount(clanId) + " members | home "
                    + (data.contains("clans." + clanId + ".home") ? "set" : "not set"));
            return true;
        }
        if ("sethome".equals(action)) {
            return setClanHome(player, playerId, clanId, args);
        }
        if ("disband".equals(action)) {
            return disbandClan(player, playerId, clanId, args);
        }
        player.sendMessage(PREFIX + ChatColor.YELLOW
                + "Clan commands: create, invite, join, leave, info, sethome, disband.");
        return true;
    }

    private boolean createClan(Player player, String playerId, String[] args) {
        if (args.length != 2 || !args[1].matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan create <3-16 letters, numbers, or _>");
            return true;
        }
        if (findClan(playerId) != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current clan before creating another.");
            return true;
        }
        String clanId = args[1].toLowerCase(Locale.ROOT);
        if (data.contains("clans." + clanId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "That clan name is already taken.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId + ".name", args[1]);
        updated.set("clans." + clanId + ".owner", playerId);
        updated.set("clans." + clanId + ".members." + playerId, player.getName());
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save the clan. Try again.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Clan " + args[1] + " created. Use /clan invite <online-player>.");
        return true;
    }

    private boolean inviteToClan(Player player, String playerId, String clanId, String[] args) {
        if (args.length != 2) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan invite <online-player>");
            return true;
        }
        if (!playerId.equals(data.getString("clans." + clanId + ".owner", ""))) {
            player.sendMessage(PREFIX + ChatColor.RED + "Only the clan owner can invite players.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "That player must be online to receive an invite.");
            return true;
        }
        String targetId = target.getUniqueId().toString();
        if (findClan(targetId) != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "That player is already in a clan.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId + ".invites." + targetId, target.getName());
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save the invite. Try again.");
            return true;
        }
        target.sendMessage(PREFIX + ChatColor.GREEN + "You were invited to "
                + data.getString("clans." + clanId + ".name", clanId) + ". Join with /clan join " + clanId + ".");
        player.sendMessage(PREFIX + ChatColor.GREEN + "Invite sent to " + target.getName() + ".");
        return true;
    }

    private boolean joinClan(Player player, String playerId, String[] args) {
        if (args.length != 2 || !args[1].matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan join <name>");
            return true;
        }
        if (findClan(playerId) != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current clan before joining another.");
            return true;
        }
        String clanId = args[1].toLowerCase(Locale.ROOT);
        String invitePath = "clans." + clanId + ".invites." + playerId;
        if (!data.contains("clans." + clanId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "That clan does not exist.");
            return true;
        }
        if (!data.contains(invitePath)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You need an invite before joining that clan.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId + ".members." + playerId, player.getName());
        updated.set(invitePath, null);
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save your membership. Try again.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "You joined "
                + data.getString("clans." + clanId + ".name", clanId) + ".");
        return true;
    }

    private boolean leaveClan(Player player, String playerId, String clanId, String[] args) {
        if (args.length != 1) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan leave");
            return true;
        }
        if (playerId.equals(data.getString("clans." + clanId + ".owner", ""))) {
            player.sendMessage(PREFIX + ChatColor.RED + "The owner cannot leave. Use /clan disband instead.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId + ".members." + playerId, null);
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save your clan change. Try again.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "You left the clan.");
        return true;
    }

    private boolean setClanHome(Player player, String playerId, String clanId, String[] args) {
        if (args.length != 1) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan sethome");
            return true;
        }
        if (clanMemberCount(clanId) <= 5) {
            player.sendMessage(PREFIX + ChatColor.RED + "Your clan needs at least 6 members to set a clan home.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId + ".home", serialize(player.getLocation()));
        updated.set("clans." + clanId + ".homeSetBy", playerId);
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save the clan home. Try again.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Clan home set.");
        return true;
    }

    private boolean subwayToClanHome(Player player) {
        String clanId = findClan(player.getUniqueId().toString());
        if (clanId == null) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Join a clan first. Use /clan create <name> or /clan join <name>.");
            return true;
        }
        if (clanMemberCount(clanId) <= 5) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Your clan needs 6 members before setting or using its subway home.");
            return true;
        }
        Location home = readLocation("clans." + clanId + ".home");
        if (home == null) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Your clan has no home yet. Use /clan sethome with 6 members.");
            return true;
        }
        teleport(player, home, "Your clan home is unavailable.");
        return true;
    }

    private boolean disbandClan(Player player, String playerId, String clanId, String[] args) {
        if (args.length != 1) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /clan disband");
            return true;
        }
        if (!playerId.equals(data.getString("clans." + clanId + ".owner", ""))) {
            player.sendMessage(PREFIX + ChatColor.RED + "Only the clan owner can disband the clan.");
            return true;
        }
        YamlConfiguration updated = copyData();
        updated.set("clans." + clanId, null);
        if (!save(updated)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not disband the clan. Try again.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Clan disbanded.");
        return true;
    }

    private String findClan(String playerId) {
        ConfigurationSection clans = data.getConfigurationSection("clans");
        if (clans == null) {
            return null;
        }
        for (String clanId : clans.getKeys(false)) {
            if (data.contains("clans." + clanId + ".members." + playerId)) {
                return clanId;
            }
        }
        return null;
    }

    private int clanMemberCount(String clanId) {
        ConfigurationSection members = data.getConfigurationSection("clans." + clanId + ".members");
        return members == null ? 0 : members.getKeys(false).size();
    }

    private boolean listApartments(CommandSender sender, String[] args) {
        if (!requireNoArguments(sender, args)) {
            return true;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(PREFIX + ChatColor.GOLD + "City apartments: rent for "
                + RENT_IRON_INGOTS + " iron ingots / 7 days.");
        for (String id : APARTMENT_IDS) {
            String path = "leases." + id;
            String owner = data.getString(path + ".owner", "");
            long expiresAt = data.getLong(path + ".expiresAt", 0L);
            if (!owner.isEmpty() && expiresAt > now) {
                sender.sendMessage(ChatColor.GRAY + id + " - rented");
            } else {
                sender.sendMessage(ChatColor.GREEN + id + " - available");
            }
        }
        sender.sendMessage(ChatColor.YELLOW + "Use /rent <id>, then /myapartment.");
        return true;
    }

    private boolean rentApartment(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /rent <A1|A2|B1|B2>");
            return true;
        }
        Player player = (Player) sender;
        String id = args[0].toUpperCase(Locale.ROOT);
        if (!isApartmentId(id)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Apartments: A1, A2, B1, B2. Use /apartments.");
            return true;
        }

        long now = System.currentTimeMillis();
        String path = "leases." + id;
        String owner = data.getString(path + ".owner", "");
        long currentExpiry = data.getLong(path + ".expiresAt", 0L);
        String playerId = player.getUniqueId().toString();
        if (!owner.isEmpty() && currentExpiry > now && !owner.equals(playerId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "That apartment is already rented.");
            return true;
        }
        String otherLease = findActiveLease(playerId, now);
        if (otherLease != null && !otherLease.equals(id)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You already rent " + otherLease + ". Use /myapartment.");
            return true;
        }

        ItemStack rentCost = new ItemStack(Material.IRON_INGOT, RENT_IRON_INGOTS);
        if (!player.getInventory().containsAtLeast(rentCost, RENT_IRON_INGOTS)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Rent costs " + RENT_IRON_INGOTS + " iron ingots for 7 days.");
            return true;
        }
        player.getInventory().removeItem(rentCost);

        YamlConfiguration updated = copyData();
        long newExpiry = Math.max(now, currentExpiry) + RENT_PERIOD_MILLIS;
        updated.set(path + ".owner", playerId);
        updated.set(path + ".name", player.getName());
        updated.set(path + ".expiresAt", newExpiry);
        if (!save(updated)) {
            player.getInventory().addItem(rentCost);
            player.sendMessage(PREFIX + ChatColor.RED + "Could not save the lease; your iron was returned.");
            return true;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "You rented " + id + " for 7 days. Use /myapartment.");
        teleportToApartment(player, id);
        return true;
    }

    private boolean myApartment(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requireNoArguments(sender, args)) {
            return true;
        }
        String id = findActiveLease(((Player) sender).getUniqueId().toString(), System.currentTimeMillis());
        if (id == null) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "You do not rent an apartment. Use /apartments.");
            return true;
        }
        teleportToApartment((Player) sender, id);
        return true;
    }

    private void teleportToApartment(Player player, String id) {
        int x = "A1".equals(id) || "B1".equals(id) ? -41 : -35;
        int y = "A1".equals(id) || "A2".equals(id) ? cityFloorY + 8 : cityFloorY + 14;
        teleport(player, new Location(cityWorld, cityCenterX + x + 0.5, y,
                cityCenterZ + 6.5, 0.0f, 0.0f), "Your apartment is not available.");
    }

    private void refreshApartmentRentSigns(World world) {
        placeSign(world, -41, cityFloorY + 10, 10, "APT A1", "4 IRON", "7 DAYS", "RENT");
        placeSign(world, -35, cityFloorY + 10, 10, "APT A2", "4 IRON", "7 DAYS", "RENT");
        placeSign(world, -41, cityFloorY + 16, 10, "APT B1", "4 IRON", "7 DAYS", "RENT");
        placeSign(world, -35, cityFloorY + 16, 10, "APT B2", "4 IRON", "7 DAYS", "RENT");
    }

    private boolean isApartmentId(String id) {
        for (String apartmentId : APARTMENT_IDS) {
            if (apartmentId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private String findActiveLease(String playerId, long now) {
        for (String id : APARTMENT_IDS) {
            String path = "leases." + id;
            if (playerId.equals(data.getString(path + ".owner", ""))
                    && data.getLong(path + ".expiresAt", 0L) > now) {
                return id;
            }
        }
        return null;
    }

    private boolean buildCitySpawn(CommandSender sender, String[] args) {
        if (!requireOperator(sender)) {
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /cityspawn");
            return true;
        }

        World world = getPrimaryWorld();
        if (world == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "The overworld is not loaded.");
            return true;
        }

        Location spawn = world.getSpawnLocation();
        cityWorld = world;
        cityCenterX = spawn.getBlockX();
        cityCenterZ = spawn.getBlockZ();
        int minChunkX = Math.floorDiv(cityCenterX - 64, 16);
        int maxChunkX = Math.floorDiv(cityCenterX + 64, 16);
        int minChunkZ = Math.floorDiv(cityCenterZ - 64, 16);
        int maxChunkZ = Math.floorDiv(cityCenterZ + 64, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.loadChunk(chunkX, chunkZ, true)) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Could not load the city area.");
                    return true;
                }
            }
        }

        int floorY = Math.max(3, spawn.getBlockY() - 2);
        clearCityMonsters(world);
        clearBox(world, -64, floorY + 1, -64, 64, floorY + 60, 64);
        clearBox(world, 5, floorY + 1, 4, 14, floorY + 12, 18);
        buildCity(world, floorY);
        cityFloorY = floorY;
        buildSubwayStation(world, floorY, 9, 10);
        world.setSpawnLocation(cityCenterX, floorY + 2, cityCenterZ);
        sender.sendMessage(PREFIX + ChatColor.GREEN
                + "Manhattan city spawn built at the overworld spawn. Hostile mobs are disabled inside the city only.");
        return true;
    }

    private boolean addCityLights(CommandSender sender, String[] args) {
        if (!requireOperator(sender)) {
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /citylights");
            return true;
        }
        if (cityWorld == null) {
            cityWorld = getPrimaryWorld();
            if (cityWorld == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "The overworld city is not available.");
                return true;
            }
            Location spawn = cityWorld.getSpawnLocation();
            cityCenterX = spawn.getBlockX();
            cityCenterZ = spawn.getBlockZ();
            cityFloorY = Math.max(3, spawn.getBlockY() - 2);
        }

        int placed = 0;
        int skipped = 0;
        // Only decorate known buildings from the bundled city layout, and only where
        // the original facade is intact and the space in front of it is empty.
        int[][] towers = {
            { -25, -53, -13, -41, 40, Material.SMOOTH_BRICK.getId() },
            { 15, -53, 27, -41, 32, Material.QUARTZ_BLOCK.getId() },
            { -44, -33, -32, -21, 23, Material.BRICK.getId() },
            { -25, -33, -13, -21, 22, Material.SANDSTONE.getId() },
            { -4, -33, 8, -21, 25, Material.SMOOTH_BRICK.getId() },
            { 15, -33, 27, -21, 24, Material.BRICK.getId() },
            { 55, -51, 63, -39, 23, Material.QUARTZ_BLOCK.getId() },
            { -4, 39, 8, 51, 30, Material.BRICK.getId() },
            { 15, 39, 27, 51, 26, Material.SMOOTH_BRICK.getId() },
            { 55, 21, 63, 33, 20, Material.SANDSTONE.getId() },
            { 55, -15, 63, -3, 22, Material.BRICK.getId() }
        };
        int[] lightColors = { 14, 11, 2, 4, 3, 1 };
        int[] lightLevels = { 5, 10, 15, 20, 25, 30 };
        for (int[] tower : towers) {
            int x1 = tower[0];
            int z2 = tower[3];
            Material facade = Material.getMaterial(tower[5]);
            if (!isIntactNeonTower(x1, z2, tower[2], facade)) {
                skipped++;
                continue;
            }

            for (int level = 0; level < lightLevels.length; level++) {
                if (lightLevels[level] + 4 > tower[4]) {
                    continue;
                }
                int y = cityFloorY + lightLevels[level] + 1;
                int[] panelStarts = { x1 + 2, x1 + 7 };
                for (int panelX : panelStarts) {
                    int[] result = addNeonPanel(panelX, y, z2, facade, lightColors[level]);
                    placed += result[0];
                    skipped += result[1];
                }
            }

            int signX = x1 + 6;
            int signY = cityFloorY + 6;
            int signZ = z2;
            if (placeNeonWallSign(signX, signY, signZ, facade, placed % 2 == 0)) {
                placed++;
            } else {
                skipped++;
            }
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "City lights complete: placed " + placed
                + " neon blocks/signs; left " + skipped
                + " occupied or unverified locations unchanged. Run /citylights again safely.");
        return true;
    }

    private boolean isIntactNeonTower(int x1, int z2, int x2, Material facade) {
        int centerX = (x1 + x2) / 2;
        int centerZ = z2 - 6;
        if (cityWorld.getBlockAt(cityCenterX + centerX, cityFloorY + 1,
                cityCenterZ + centerZ).getType() != Material.SMOOTH_BRICK) {
            return false;
        }
        int facadeY = cityFloorY + 4;
        return cityWorld.getBlockAt(cityCenterX + x1 + 1, facadeY,
                    cityCenterZ + z2).getType() == facade
                && cityWorld.getBlockAt(cityCenterX + x2 - 1, facadeY,
                    cityCenterZ + z2).getType() == facade;
    }

    private int[] addNeonPanel(int x, int y, int z, Material facade, int glassColor) {
        int placed = 0;
        int skipped = 0;
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                Block target = cityWorld.getBlockAt(cityCenterX + x + dx, y + dy, cityCenterZ + z);
                Material targetType = target.getType();
                if (targetType != facade && targetType != Material.STAINED_GLASS) {
                    skipped++;
                    continue;
                }

                boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                if (frame) {
                    target.setType(Material.GLOWSTONE, false);
                } else {
                    target.setTypeIdAndData(Material.STAINED_GLASS.getId(), (byte) glassColor, false);
                }
                placed++;
            }
        }
        return new int[] { placed, skipped };
    }

    @SuppressWarnings("deprecation")
    private boolean placeNeonWallSign(int x, int y, int z, Material facade, boolean broadwaySide) {
        Block target = cityWorld.getBlockAt(cityCenterX + x, y, cityCenterZ + z);
        if (target.getType() != facade && target.getType() != Material.STAINED_GLASS) {
            return false;
        }
        target.setTypeIdAndData(Material.WALL_SIGN.getId(), (byte) 3, false);
        Sign sign = (Sign) target.getState();
        sign.setLine(0, ChatColor.GOLD + "NEW YORK");
        sign.setLine(1, ChatColor.AQUA + (broadwaySide ? "BROADWAY" : "42ND STREET"));
        sign.setLine(2, ChatColor.LIGHT_PURPLE + (broadwaySide ? "TIMES SQ" : "MANHATTAN"));
        sign.setLine(3, ChatColor.YELLOW + "NIGHT");
        return sign.update(true);
    }

    private World getPrimaryWorld() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && isInsideCity(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && cityWorld != null) {
                    teleportToCitySpawn(player);
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onApartmentMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        String apartmentId = apartmentAt(event.getTo());
        if (apartmentId != null && !apartmentId.equals(findActiveLease(
                event.getPlayer().getUniqueId().toString(), System.currentTimeMillis()))) {
            event.setTo(event.getFrom());
            if (!apartmentId.equals(apartmentAt(event.getFrom()))) {
                event.getPlayer().sendMessage(PREFIX + ChatColor.RED
                        + "That apartment is private. Rent it with /rent " + apartmentId + ".");
            }
        }
    }

    private String apartmentAt(Location location) {
        if (location.getWorld() != cityWorld) {
            return null;
        }
        int x = location.getBlockX() - cityCenterX;
        int z = location.getBlockZ() - cityCenterZ;
        int y = location.getBlockY();
        if (z < 4 || z > 9) {
            return null;
        }
        boolean lower = y >= cityFloorY + 8 && y <= cityFloorY + 12;
        boolean upper = y >= cityFloorY + 14 && y <= cityFloorY + 18;
        if (!lower && !upper) {
            return null;
        }
        if (x >= -43 && x <= -39) {
            return lower ? "A1" : "B1";
        }
        if (x >= -37 && x <= -33) {
            return lower ? "A2" : "B2";
        }
        return null;
    }

    private void teleportToCitySpawn(Player player) {
        Location spawn = new Location(cityWorld, cityCenterX + 0.5, cityFloorY + 2,
                cityCenterZ + 0.5, 0.0f, 0.0f);
        player.teleport(spawn);
    }

    private boolean isInsideCity(Location location) {
        return location.getWorld() == cityWorld
                && Math.abs(location.getBlockX() - cityCenterX) <= 64
                && Math.abs(location.getBlockZ() - cityCenterZ) <= 64;
    }

    private void clearCityMonsters(World world) {
        for (Monster monster : new ArrayList<Monster>(world.getEntitiesByClass(Monster.class))) {
            if (isInsideCity(monster.getLocation())) {
                monster.remove();
            }
        }
    }

    private void clearBox(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    setBlock(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void buildCity(World world, int floorY) {
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                setBlock(world, x, floorY - 2, z, Material.STONE);
                setBlock(world, x, floorY - 1, z, Material.DIRT);
                setBlock(world, x, floorY, z, Material.GRASS);
            }
        }

        paintRect(world, -64, -64, -55, 64, floorY + 1, Material.STATIONARY_WATER, 0);
        for (int z = -64; z <= 64; z++) {
            if (Math.abs(z + 36) > 4 && Math.abs(z) > 4 && Math.abs(z - 36) > 4) {
                setBlock(world, -55, floorY + 1, z, Material.SMOOTH_BRICK);
                setBlock(world, -55, floorY + 2, z, Material.SMOOTH_BRICK);
            }
        }

        int[] avenues = { -48, -28, -8, 12, 32, 52 };
        for (int avenue : avenues) {
            paintRect(world, avenue - 2, -64, avenue + 2, 64, floorY + 1, Material.STAINED_CLAY, 7);
            paintRect(world, avenue - 3, -64, avenue - 3, 64, floorY + 1, Material.STAINED_CLAY, 8);
            paintRect(world, avenue + 3, -64, avenue + 3, 64, floorY + 1, Material.STAINED_CLAY, 8);
            for (int z = -62; z <= 62; z += 8) {
                paintRect(world, avenue, z, avenue, Math.min(z + 3, 64), floorY + 1, Material.WOOL, 4);
            }
        }

        int[] crossStreets = { -54, -36, -18, 0, 18, 36, 54 };
        for (int street : crossStreets) {
            paintRect(world, -64, street - 1, 64, street + 1, floorY + 1, Material.STAINED_CLAY, 7);
            paintRect(world, -64, street - 2, 64, street - 2, floorY + 1, Material.STAINED_CLAY, 8);
            paintRect(world, -64, street + 2, 64, street + 2, floorY + 1, Material.STAINED_CLAY, 8);
            for (int x = -62; x <= 62; x += 10) {
                paintRect(world, x, street, Math.min(x + 4, 64), street, floorY + 1, Material.WOOL, 4);
            }
        }

        for (int z = -62; z <= 62; z++) {
            int broadwayX = z / 2;
            paintRect(world, broadwayX - 1, z, broadwayX + 1, z, floorY + 1, Material.STAINED_CLAY, 7);
            if (z % 8 >= 0 && z % 8 < 4) {
                setBlock(world, broadwayX, floorY + 1, z, Material.WOOL, 4);
            }
        }

        paintRect(world, -17, -17, 17, 17, floorY + 1, Material.STAINED_CLAY, 15);
        paintRect(world, -14, -14, 14, 14, floorY + 1, Material.SMOOTH_BRICK, 0);
        paintRect(world, -11, -11, 11, 11, floorY + 1, Material.STAINED_CLAY, 15);
        paintRect(world, -10, -1, 10, 1, floorY + 1, Material.WOOL, 4);
        paintRect(world, -1, -10, 1, 10, floorY + 1, Material.WOOL, 4);

        buildSkyscraper(world, floorY, -25, -53, -13, -41, 40, Material.SMOOTH_BRICK, 3);
        buildSkyscraper(world, floorY, 15, -53, 27, -41, 32, Material.QUARTZ_BLOCK, 3);
        buildSkyscraper(world, floorY, -44, -33, -32, -21, 23, Material.BRICK, 11);
        buildSkyscraper(world, floorY, -25, -33, -13, -21, 22, Material.SANDSTONE, 3);
        buildSkyscraper(world, floorY, -4, -33, 8, -21, 25, Material.SMOOTH_BRICK, 11);
        buildSkyscraper(world, floorY, 15, -33, 27, -21, 24, Material.BRICK, 3);
        buildSkyscraper(world, floorY, 55, -51, 63, -39, 23, Material.QUARTZ_BLOCK, 11);
        buildApartmentTower(world, floorY);
        buildSkyscraper(world, floorY, -25, 21, -13, 33, 21, Material.QUARTZ_BLOCK, 11);
        buildSkyscraper(world, floorY, -4, 39, 8, 51, 30, Material.BRICK, 3);
        buildSkyscraper(world, floorY, 15, 39, 27, 51, 26, Material.SMOOTH_BRICK, 11);
        buildSkyscraper(world, floorY, 55, 21, 63, 33, 20, Material.SANDSTONE, 3);
        buildSkyscraper(world, floorY, 55, -15, 63, -3, 22, Material.BRICK, 11);
        buildFlatiron(world, floorY, -25, -33, -12, -20, 18);

        buildBrownstone(world, floorY, -63, 22, 5, 10, 8, 12);
        buildBrownstone(world, floorY, -44, 22, 5, 10, 8, 12);
        buildBrownstone(world, floorY, -25, 39, 5, 10, 9, 1);
        buildBrownstone(world, floorY, -4, 22, 5, 10, 8, 14);
        buildBrownstone(world, floorY, 15, 22, 5, 10, 8, 3);
        buildBrownstone(world, floorY, 55, 39, 5, 10, 8, 12);

        buildCentralPark(world, floorY);
        buildBridge(world, floorY, -36);
        buildBridge(world, floorY, 36);
        buildStatueOfLiberty(world, floorY, -60, 11);
        buildBillboard(world, floorY, -15, -17, 11, 7, 14);
        buildBillboard(world, floorY, 5, -17, 11, 7, 4);
        buildTaxi(world, floorY, -9, 9);
        placeSign(world, -2, floorY + 8, -16, "TIMES", "SQUARE", "NEW YORK", "");
        buildModernMarket(world, floorY);
        buildStreetGridBuildings(world, floorY);
        buildShoppingDistrictArea(world, floorY);
    }

    private void buildShoppingDistrictArea(World world, int floorY) {
        clearBox(world, -4, floorY + 1, -51, 8, floorY + 36, -39);
        paintRect(world, -4, -51, 8, -39, floorY + 1, Material.SMOOTH_BRICK, 0);
        buildStorefront(world, floorY, -4, -1, -51, -39, 10, 11,
                "FIFTH AVE", "FASHION", "STYLE");
        buildStorefront(world, floorY, 0, 3, -51, -39, 12, 3,
                "TECH HUB", "ELECTRONICS", "GADGETS");
        buildStorefront(world, floorY, 4, 8, -51, -39, 9, 4,
                "CITY CAFE", "COFFEE", "DELI");
        buildMcDonalds(world, floorY, 1, 5, -50, -40);
    }

    private void buildMcDonalds(World world, int floorY, int x1, int x2, int z1, int z2) {
        int centerX = (x1 + x2) / 2;
        int centerZ = (z1 + z2) / 2;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                setBlock(world, x, floorY + 1, z, Material.SMOOTH_BRICK);
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int y = floorY + 2; y <= floorY + 8; y++) {
                setBlock(world, x, y, z1, Material.QUARTZ_BLOCK);
                setBlock(world, x, y, z2, Material.QUARTZ_BLOCK);
            }
        }
        for (int z = z1; z <= z2; z++) {
            for (int y = floorY + 2; y <= floorY + 8; y++) {
                setBlock(world, x1, y, z, Material.QUARTZ_BLOCK);
                setBlock(world, x2, y, z, Material.QUARTZ_BLOCK);
            }
        }
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                setBlock(world, x, floorY + 2, z, Material.STAINED_GLASS, 0);
            }
        }
        for (int x = x1 + 1; x < x2; x++) {
            setBlock(world, x, floorY + 9, z1 + 1, Material.GLOWSTONE);
            setBlock(world, x, floorY + 9, z2 - 1, Material.GLOWSTONE);
        }
        setBlock(world, centerX, floorY + 3, z1, Material.REDSTONE_LAMP_ON);
        setBlock(world, centerX - 2, floorY + 3, z1, Material.WOOL, 14);
        setBlock(world, centerX + 2, floorY + 3, z1, Material.WOOL, 14);
        placeSign(world, centerX - 2, floorY + 4, z1, "MCDONALD'S", "FAST FOOD", "BURGER", "FRIES");
        placeSign(world, centerX + 2, floorY + 4, z1 + 1, "NEW YORK", "CITY", "STYLE", "");
    }

    private void buildStorefront(World world, int floorY, int x1, int x2, int z1, int z2,
            int height, int accent, String sign1, String sign2, String sign3) {
        int frontZ = z2;
        for (int y = floorY + 2; y <= floorY + height; y++) {
            for (int x = x1; x <= x2; x++) {
                setBlock(world, x, y, z1, Material.QUARTZ_BLOCK);
                setBlock(world, x, y, frontZ, y <= floorY + 4 ? Material.STAINED_GLASS : Material.GLASS,
                        y <= floorY + 4 ? accent : 0);
            }
            setBlock(world, x1, y, z1, Material.QUARTZ_BLOCK);
            setBlock(world, x1, y, frontZ, Material.QUARTZ_BLOCK);
            setBlock(world, x2, y, z1, Material.QUARTZ_BLOCK);
            setBlock(world, x2, y, frontZ, Material.QUARTZ_BLOCK);
            for (int z = z1 + 1; z < frontZ; z++) {
                setBlock(world, x1, y, z, Material.QUARTZ_BLOCK);
                setBlock(world, x2, y, z, Material.QUARTZ_BLOCK);
            }
        }
        int doorX = (x1 + x2) / 2;
        for (int y = floorY + 2; y <= floorY + 4; y++) {
            setBlock(world, doorX, y, frontZ, Material.AIR);
        }
        paintRect(world, x1, z1, x2, frontZ, floorY + height + 1, Material.QUARTZ_BLOCK, 0);
        paintRect(world, x1, frontZ, x2, frontZ, floorY + 5, Material.STAINED_CLAY, accent);
        for (int x = x1; x <= x2; x++) {
            setBlock(world, x, floorY + height + 2, z1, Material.SEA_LANTERN);
        }
        placeSign(world, doorX, floorY + 7, frontZ + 1, sign1, sign2, sign3, "/SHOP");
    }

    private void buildModernMarket(World world, int floorY) {
        paintRect(world, 35, -53, 49, -38, floorY + 1, Material.SMOOTH_BRICK, 0);
        for (int y = floorY + 2; y <= floorY + 10; y++) {
            for (int x = 35; x <= 49; x++) {
                setBlock(world, x, y, -53, Material.STAINED_GLASS, 3);
                setBlock(world, x, y, -38, Material.STAINED_GLASS, 3);
            }
            for (int z = -52; z < -38; z++) {
                setBlock(world, 35, y, z, Material.STAINED_GLASS, 3);
                setBlock(world, 49, y, z, Material.STAINED_GLASS, 3);
            }
        }
        for (int x : new int[] { 35, 42, 49 }) {
            for (int y = floorY + 2; y <= floorY + 11; y++) {
                setBlock(world, x, y, -53, Material.QUARTZ_BLOCK);
                setBlock(world, x, y, -38, Material.QUARTZ_BLOCK);
            }
        }
        for (int z : new int[] { -53, -46, -38 }) {
            for (int y = floorY + 2; y <= floorY + 11; y++) {
                setBlock(world, 35, y, z, Material.QUARTZ_BLOCK);
                setBlock(world, 49, y, z, Material.QUARTZ_BLOCK);
            }
        }
        paintRect(world, 35, -53, 49, -38, floorY + 12, Material.QUARTZ_BLOCK, 0);
        paintRect(world, 36, -52, 48, -39, floorY + 1, Material.QUARTZ_BLOCK, 0);
        paintRect(world, 39, -51, 45, -49, floorY + 2, Material.SMOOTH_BRICK, 0);
        paintRect(world, 39, -42, 45, -40, floorY + 2, Material.SMOOTH_BRICK, 0);
        for (int y = floorY + 2; y <= floorY + 4; y++) {
            setBlock(world, 42, y, -38, Material.AIR);
            setBlock(world, 43, y, -38, Material.AIR);
        }
        paintRect(world, 37, -48, 47, -47, floorY + 5, Material.SEA_LANTERN, 0);
        paintRect(world, 37, -44, 47, -43, floorY + 5, Material.SEA_LANTERN, 0);
        setBlock(world, 39, floorY + 2, -45, Material.CHEST);
        setBlock(world, 42, floorY + 2, -45, Material.WORKBENCH);
        setBlock(world, 45, floorY + 2, -45, Material.CHEST);
        placeSign(world, 43, floorY + 6, -37, "CITY", "MARKET", "/SHOP", "BUY + SELL");
    }

    private void buildStreetGridBuildings(World world, int floorY) {
        int[] avenueEdges = { -64, -48, -28, -8, 12, 32, 52, 64 };
        int[] streetEdges = { -64, -54, -36, -18, 0, 18, 36, 54, 64 };
        Material[] facades = { Material.QUARTZ_BLOCK, Material.STAINED_CLAY, Material.SMOOTH_BRICK, Material.GLASS };
        int[] windowColors = { 3, 11, 3, 11 };

        for (int avenueIndex = 0; avenueIndex < avenueEdges.length - 1; avenueIndex++) {
            int x1 = avenueEdges[avenueIndex] + 4;
            int x2 = avenueEdges[avenueIndex + 1] - 4;
            if (x2 - x1 < 7) {
                continue;
            }
            for (int streetIndex = 0; streetIndex < streetEdges.length - 1; streetIndex++) {
                int z1 = streetEdges[streetIndex] + 3;
                int z2 = streetEdges[streetIndex + 1] - 3;
                if (z2 - z1 < 7 || overlapsBlock(x1, z1, x2, z2, -18, -18, 18, 18)
                        || overlapsBlock(x1, z1, x2, z2, 34, -15, 50, 36)
                    || overlapsBlock(x1, z1, x2, z2, 35, -53, 49, -38)
                        || x1 <= -55) {
                    continue;
                }
                if (hasBuilding(world, floorY, x1, z1, x2, z2)) {
                    continue;
                }

                int style = (avenueIndex * 7 + streetIndex * 11) % facades.length;
                int height = 16 + (avenueIndex * 5 + streetIndex * 7) % 18;
                if ((avenueIndex + streetIndex) % 4 == 0) {
                    buildBrownstone(world, floorY, x1, z1, x2 - x1 + 1, z2 - z1 + 1,
                            Math.min(height, 12), windowColors[style]);
                } else {
                    buildSkyscraper(world, floorY, x1, z1, x2, z2, height,
                            facades[style], windowColors[style]);
                }
            }
        }
    }

    private void buildApartmentTower(World world, int floorY) {
        paintRect(world, -44, 3, -32, 15, floorY + 1, Material.SMOOTH_BRICK, 0);
        for (int y = floorY + 2; y <= floorY + 23; y++) {
            for (int x = -44; x <= -32; x++) {
                setBlock(world, x, y, 3, Material.BRICK);
                setBlock(world, x, y, 15, Material.BRICK);
            }
            for (int z = 4; z < 15; z++) {
                setBlock(world, -44, y, z, Material.BRICK);
                setBlock(world, -32, y, z, Material.BRICK);
            }
            if (y >= floorY + 4 && (y - floorY) % 4 == 0) {
                setBlock(world, -44, y, 7, Material.STAINED_GLASS, 3);
                setBlock(world, -44, y, 11, Material.STAINED_GLASS, 3);
                setBlock(world, -32, y, 7, Material.STAINED_GLASS, 3);
                setBlock(world, -32, y, 11, Material.STAINED_GLASS, 3);
            }
        }
        for (int floor : new int[] { floorY + 7, floorY + 13 }) {
            paintRect(world, -43, 4, -33, 14, floor, Material.SMOOTH_BRICK, 0);
            paintRect(world, -43, 10, -33, 14, floor, Material.SANDSTONE, 0);
        }

        buildApartmentUnit(world, floorY + 7, -43, -39, 4, 9, "A1", 14);
        buildApartmentUnit(world, floorY + 7, -37, -33, 4, 9, "A2", 11);
        buildApartmentUnit(world, floorY + 13, -43, -39, 4, 9, "B1", 14);
        buildApartmentUnit(world, floorY + 13, -37, -33, 4, 9, "B2", 11);

        for (int y = floorY + 2; y <= floorY + 4; y++) {
            setBlock(world, -39, y, 15, Material.AIR);
            setBlock(world, -38, y, 15, Material.AIR);
        }
        for (int step = 0; step < 12; step++) {
            setBlock(world, -42 + step / 3, floorY + 2 + step, 12 - step / 3,
                    Material.SMOOTH_STAIRS, 0);
        }
        placeSign(world, -39, floorY + 5, 16, "NYC", "APTS", "RENT", "IRON");
        placeSign(world, -38, floorY + 4, 11, "FLOORS", "2 + 3", "A1 A2", "B1 B2");
    }

    private void buildApartmentUnit(World world, int floorY, int x1, int x2, int z1, int z2,
            String id, int color) {
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            for (int x = x1; x <= x2; x++) {
                setBlock(world, x, y, z1, Material.BRICK);
                setBlock(world, x, y, z2, Material.BRICK);
            }
            for (int z = z1 + 1; z < z2; z++) {
                setBlock(world, x1, y, z, Material.BRICK);
                setBlock(world, x2, y, z, Material.BRICK);
            }
        }
        setBlock(world, x1 + 1, floorY + 2, z1, Material.STAINED_GLASS, 3);
        setBlock(world, x2 - 1, floorY + 2, z1, Material.STAINED_GLASS, 3);
        int doorX = (x1 + x2) / 2;
        setBlock(world, doorX, floorY + 1, z2, Material.AIR);
        setBlock(world, doorX, floorY + 2, z2, Material.AIR);
        setBlock(world, x1 + 1, floorY + 1, z1 + 1, Material.WOOL, color);
        setBlock(world, x1 + 2, floorY + 1, z1 + 1, Material.WOOL, color);
        setBlock(world, x2 - 1, floorY + 1, z1 + 1, Material.CHEST);
        setBlock(world, x1 + 1, floorY + 1, z2 - 1, Material.BOOKSHELF);
        setBlock(world, x2 - 1, floorY + 1, z2 - 1, Material.WORKBENCH);
        placeSign(world, doorX, floorY + 3, z2 + 1, "APT " + id, "4 IRON", "7 DAYS", "RENT");
    }

    private boolean overlapsBlock(int x1, int z1, int x2, int z2,
            int areaX1, int areaZ1, int areaX2, int areaZ2) {
        return x1 <= areaX2 && x2 >= areaX1 && z1 <= areaZ2 && z2 >= areaZ1;
    }

    private boolean hasBuilding(World world, int floorY, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (world.getBlockAt(cityCenterX + x, floorY + 2, cityCenterZ + z).getType() != Material.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    private void buildSkyscraper(World world, int floorY, int x1, int z1, int x2, int z2,
            int height, Material facade, int glassData) {
        paintRect(world, x1, z1, x2, z2, floorY + 1, Material.SMOOTH_BRICK, 0);
        int centerX = (x1 + x2) / 2;
        int centerZ = (z1 + z2) / 2;
        for (int level = 1; level <= height; level++) {
            int inset = level > height * 4 / 5 ? 2 : level > height * 2 / 3 ? 1 : 0;
            int left = x1 + inset;
            int right = x2 - inset;
            int north = z1 + inset;
            int south = z2 - inset;
            int y = floorY + level + 1;
            boolean windowLevel = level >= 4 && level % 5 >= 2 && level % 5 <= 3;
            for (int x = left; x <= right; x++) {
                setFacadeBlock(world, x, y, north, facade, glassData, windowLevel,
                        x != left && x != right && (x - left) % 4 != 0);
                setFacadeBlock(world, x, y, south, facade, glassData, windowLevel,
                        x != left && x != right && (x - left) % 4 != 0);
            }
            for (int z = north + 1; z < south; z++) {
                setFacadeBlock(world, left, y, z, facade, glassData, windowLevel, (z - north) % 4 != 0);
                setFacadeBlock(world, right, y, z, facade, glassData, windowLevel, (z - north) % 4 != 0);
            }
            if (level <= 3) {
                setBlock(world, centerX, y, south, Material.AIR);
                if (right - left >= 5) {
                    setBlock(world, centerX + 1, y, south, Material.AIR);
                }
            }
        }

        int roofY = floorY + height + 2;
        paintRect(world, x1, z1, x2, z2, roofY, Material.SMOOTH_BRICK, 0);
        for (int offset = -2; offset <= 2; offset++) {
            setBlock(world, centerX + offset, roofY + 1, centerZ, Material.QUARTZ_BLOCK);
        }
        if (height >= 28) {
            for (int y = roofY + 1; y <= roofY + 8; y++) {
                setBlock(world, centerX, y, centerZ, Material.IRON_FENCE);
            }
            setBlock(world, centerX, roofY + 9, centerZ, Material.GLOWSTONE);
        }
    }

    private void setFacadeBlock(World world, int x, int y, int z, Material facade,
            int glassData, boolean windowLevel, boolean windowColumn) {
        if (windowLevel && windowColumn) {
            setBlock(world, x, y, z, Material.STAINED_GLASS, glassData);
        } else {
            setBlock(world, x, y, z, facade);
        }
    }

    private void buildFlatiron(World world, int floorY, int x1, int z1, int x2, int z2, int height) {
        paintRect(world, x1, z1, x2, z2, floorY + 1, Material.SMOOTH_BRICK, 0);
        for (int z = z1; z <= z2; z++) {
            int taper = Math.min((z - z1) / 3, (x2 - x1) / 2 - 1);
            int left = x1 + taper;
            int right = x2 - taper;
            for (int level = 1; level <= height; level++) {
                int y = floorY + level + 1;
                Material block = level >= 4 && level % 4 == 0 ? Material.STAINED_GLASS : Material.SMOOTH_BRICK;
                setBlock(world, left, y, z, block);
                if (right != left) {
                    setBlock(world, right, y, z, block);
                }
            }
        }
        paintRect(world, x1 + 2, z1 + 2, x2 - 2, z1 + 4, floorY + height + 2, Material.QUARTZ_BLOCK, 0);
    }

    private void buildBrownstone(World world, int floorY, int x, int z, int width, int depth,
            int height, int glassData) {
        paintRect(world, x, z, x + width - 1, z + depth - 1, floorY + 1, Material.SMOOTH_BRICK, 0);
        int frontZ = z + depth - 1;
        int doorX = x + width / 2;
        for (int level = 1; level <= height; level++) {
            int y = floorY + level + 1;
            for (int offset = 0; offset < width; offset++) {
                Material facade = level >= 3 && level % 4 >= 2 && offset == 1
                        ? Material.STAINED_GLASS : Material.BRICK;
                setBlock(world, x + offset, y, z, facade, facade == Material.STAINED_GLASS ? glassData : 0);
                setBlock(world, x + offset, y, frontZ, facade, facade == Material.STAINED_GLASS ? glassData : 0);
            }
            for (int offset = 1; offset < depth - 1; offset++) {
                setBlock(world, x, y, z + offset, Material.BRICK);
                setBlock(world, x + width - 1, y, z + offset, Material.BRICK);
            }
            if (level <= 3) {
                setBlock(world, doorX, y, frontZ, Material.AIR);
            }
        }
        paintRect(world, x, z, x + width - 1, z + depth - 1, floorY + height + 2, Material.SMOOTH_BRICK, 0);
        paintRect(world, doorX - 1, frontZ + 1, doorX + 1, frontZ + 2, floorY + 1, Material.SANDSTONE, 0);
    }

    private void buildCentralPark(World world, int floorY) {
        paintRect(world, 34, -15, 50, 36, floorY + 1, Material.SMOOTH_BRICK, 0);
        paintRect(world, 36, -13, 48, 34, floorY + 1, Material.GRASS, 0);
        paintRect(world, 27, -4, 32, 3, floorY + 1, Material.STAINED_CLAY, 11);
        for (int x = 28; x <= 31; x++) {
            for (int z = -3; z <= 2; z++) {
                setBlock(world, x, floorY + 1, z, Material.STATIONARY_WATER);
            }
        }
        paintRect(world, 40, -10, 43, 31, floorY + 1, Material.SANDSTONE, 0);
        int[][] trees = { { 38, -9 }, { 45, -8 }, { 37, 2 }, { 46, 7 },
            { 38, 15 }, { 46, 18 }, { 39, 28 }, { 46, 29 } };
        for (int[] tree : trees) {
            buildParkTree(world, floorY, tree[0], tree[1]);
        }
        for (int z = -8; z <= 29; z += 6) {
            buildStreetLamp(world, floorY, 42, z);
        }
    }

    private void buildParkTree(World world, int floorY, int x, int z) {
        for (int y = floorY + 1; y <= floorY + 5; y++) {
            setBlock(world, x, y, z, Material.LOG);
        }
        for (int y = floorY + 4; y <= floorY + 6; y++) {
            int radius = y == floorY + 6 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + 1 && (dx != 0 || dz != 0 || y == floorY + 6)) {
                        setBlock(world, x + dx, y, z + dz, Material.LEAVES);
                    }
                }
            }
        }
    }

    private void buildBridge(World world, int floorY, int centerZ) {
        paintRect(world, -64, centerZ - 2, -52, centerZ + 2, floorY + 1, Material.STAINED_CLAY, 7);
        for (int x : new int[] { -63, -55 }) {
            for (int zOffset : new int[] { -2, 2 }) {
                for (int y = floorY + 2; y <= floorY + 10; y++) {
                    setBlock(world, x, y, centerZ + zOffset, Material.SMOOTH_BRICK);
                }
            }
            paintRect(world, x, centerZ - 2, x, centerZ + 2, floorY + 11, Material.QUARTZ_BLOCK, 0);
        }
        for (int x = -62; x <= -55; x++) {
            setBlock(world, x, floorY + 8, centerZ - 2, Material.IRON_FENCE);
            setBlock(world, x, floorY + 8, centerZ + 2, Material.IRON_FENCE);
        }
    }

    private void buildStatueOfLiberty(World world, int floorY, int x, int z) {
        paintRect(world, x - 3, z - 3, x + 3, z + 3, floorY + 1, Material.SANDSTONE, 0);
        paintRect(world, x - 2, z - 2, x + 2, z + 2, floorY + 2, Material.STAINED_CLAY, 13);
        for (int y = floorY + 3; y <= floorY + 12; y++) {
            setBlock(world, x, y, z, Material.WOOL, 13);
        }
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(world, x + dx, floorY + 8, z, Material.WOOL, 13);
        }
        for (int y = floorY + 10; y <= floorY + 14; y++) {
            setBlock(world, x + 2, y, z, Material.WOOL, 13);
        }
        setBlock(world, x + 2, floorY + 15, z, Material.GLOWSTONE);
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(world, x + dx, floorY + 13, z, Material.WOOL, 13);
        }
    }

    private void buildBillboard(World world, int floorY, int x, int z, int width, int height, int color) {
        int bottom = floorY + 8;
        int top = bottom + height;
        for (int dx = 0; dx < width; dx++) {
            setBlock(world, x + dx, bottom, z, Material.GLOWSTONE);
            setBlock(world, x + dx, top, z, Material.GLOWSTONE);
        }
        for (int y = bottom; y <= top; y++) {
            setBlock(world, x, y, z, Material.GLOWSTONE);
            setBlock(world, x + width - 1, y, z, Material.GLOWSTONE);
            for (int dx = 1; dx < width - 1; dx++) {
                setBlock(world, x + dx, y, z, Material.STAINED_GLASS, color);
            }
        }
    }

    private void buildTaxi(World world, int floorY, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(world, x + dx, floorY + 1, z - 1, Material.STAINED_CLAY, 15);
            setBlock(world, x + dx, floorY + 1, z + 1, Material.STAINED_CLAY, 15);
            setBlock(world, x + dx, floorY + 2, z, Material.WOOL, 4);
            if (dx >= -1 && dx <= 1) {
                setBlock(world, x + dx, floorY + 3, z, Material.WOOL, 4);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            setBlock(world, x + dx, floorY + 3, z, Material.STAINED_GLASS, 3);
        }
        setBlock(world, x, floorY + 4, z, Material.WOOL, 4);
    }

    private void buildSubwayStation(World world, int floorY, int entranceX, int entranceZ) {
        int centerX = cityCenterX + entranceX;
        int centerY = floorY + 1;
        int centerZ = cityCenterZ + entranceZ;
        clearBox(world, 6, floorY + 1, 11, 12, floorY + 3, 15);
        paintRect(world, 5, 8, 13, 18, floorY + 1, Material.SMOOTH_BRICK, 0);
        for (int x = 6; x <= 12; x++) {
            setBlock(world, x, floorY + 2, 17, Material.AIR);
            setBlock(world, x, floorY + 2, 16, Material.AIR);
        }
        setBlock(world, 9, floorY + 1, 15, Material.AIR);
        setBlock(world, 9, floorY + 2, 15, Material.AIR);
        buildStationShell(world, centerX, centerY, centerZ);
        buildSpawnSubwayButtons(world, centerX, centerY, centerZ);
        placeSign(world, entranceX, floorY + 3, entranceZ + 4,
            "MTA SUBWAY", "RIGHT CLICK", "HOME / CLAN", "SPAWN / MARKET");
    }

    private void buildFountain(World world, int floorY, int centerX, int centerZ) {
        for (int x = centerX - 5; x <= centerX + 5; x++) {
            for (int z = centerZ - 5; z <= centerZ + 5; z++) {
                boolean edge = Math.abs(x - centerX) == 5 || Math.abs(z - centerZ) == 5;
                setBlock(world, x, floorY + 2, z, edge ? Material.QUARTZ_BLOCK : Material.STAINED_CLAY);
                if (!edge) {
                    setBlock(world, x, floorY + 1, z, Material.WATER);
                }
            }
        }
        for (int y = floorY + 1; y <= floorY + 5; y++) {
            setBlock(world, centerX, y, centerZ, Material.PRISMARINE);
        }
        setBlock(world, centerX, floorY + 6, centerZ, Material.SEA_LANTERN);
    }

    private void buildStreetLamp(World world, int floorY, int x, int z) {
        for (int y = floorY + 1; y <= floorY + 5; y++) {
            setBlock(world, x, y, z, Material.FENCE);
        }
        setBlock(world, x, floorY + 6, z, Material.GLOWSTONE);
        for (int offset = -1; offset <= 1; offset++) {
            setBlock(world, x + offset, floorY + 6, z, Material.GLOWSTONE);
            setBlock(world, x, floorY + 6, z + offset, Material.GLOWSTONE);
        }
    }

    private void placeSign(World world, int x, int y, int z, String first, String second,
            String third, String fourth) {
        setBlock(world, x, y - 1, z, Material.FENCE);
        Block block = world.getBlockAt(cityCenterX + x, y, cityCenterZ + z);
        block.setType(Material.SIGN_POST, false);
        Sign sign = (Sign) block.getState();
        sign.setLine(0, first);
        sign.setLine(1, second);
        sign.setLine(2, third);
        sign.setLine(3, fourth);
        sign.update(true);
    }

    private void paintRect(World world, int x1, int z1, int x2, int z2, int y, Material material, int data) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                setBlock(world, x, y, z, material, data);
            }
        }
    }

    private void setBlock(World world, int x, int y, int z, Material material) {
        world.getBlockAt(cityCenterX + x, y, cityCenterZ + z).setType(material, false);
    }

    @SuppressWarnings("deprecation")
    private void setBlock(World world, int x, int y, int z, Material material, int data) {
        Block block = world.getBlockAt(cityCenterX + x, y, cityCenterZ + z);
        block.setTypeIdAndData(material.getId(), (byte) data, false);
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
