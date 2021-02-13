package com.winthier.rules;

import com.winthier.chat.ChatPlugin;
import java.io.File;
import java.io.InputStreamReader;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Random;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class RulesPlugin extends JavaPlugin implements Listener {
    private Random random;
    private long privKey;
    private static final String META_PASSWORD = "rules.password";
    private static final String META_RULES = "rules.rules"; // Did type /rules
    private List<String> rules;
    private ConfigurationSection messagesConfig;
    private ConfigurationSection rulesConfig;
    private String permission = "group.friendly";

    // -- Plugin overrides

    @Override
    public void onEnable() {
        random = new Random(System.nanoTime());
        privKey = random.nextLong();
        reloadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player player : getServer().getOnlinePlayers()) {
            player.removeMetadata(META_RULES, this);
            checkForWelcomeMessage(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.openBook(makeRuleBook(player));
            } else {
                showRules(sender);
            }
            return true;
        }
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("accept") && args.length == 2) {
            Player player = sender instanceof Player ? (Player) sender : null;
            if (playerInFromGroup(player)) {
                if (getPassword(player).equalsIgnoreCase(args[1])) {
                    promotePlayer(player);
                    player.sendMessage(getMessagesConfig().getString("Promotion"));
                } else {
                    showRules(player);
                }
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player == null) return false;
            player.kickPlayer(getMessagesConfig().getString("Decline"));
        } else if (firstArg.equals("pwof") && args.length == 2) {
            if (!sender.hasPermission("rules.admin")) return false;
            String targetName = args[1];
            Player target = getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("Player not found: " + targetName);
                return true;
            }
            String password = getPassword(target);
            sender.sendMessage("Password of " + target.getName() + ": '" + password + "'");
        } else if (firstArg.equals("save") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            saveDefaultConfig();
            saveResource("rules.yml", false);
            saveResource("messages.yml", false);
            sender.sendMessage("[Rules] Default configurations saved.");
        } else if (firstArg.equals("reload") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            reloadConfig();
            rules = null;
            rulesConfig = null;
            messagesConfig = null;
            sender.sendMessage("[Rules] Configuration reloaded.");
        } else if (firstArg.equals("welcome") && args.length == 2) {
            if (!sender.hasPermission("rules.admin")) return false;
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Player not found: " + args[1]);
                return true;
            }
            sendWelcomeMessage(target);
            sender.sendMessage("Welcome message sent to " + target.getName());
        } else {
            return false;
        }
        return true;
    }

    ConfigurationSection getMessagesConfig() {
        if (messagesConfig == null) {
            File file = new File(getDataFolder(), "messages.yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            InputStreamReader inp = new InputStreamReader(getResource("messages.yml"));
            cfg.setDefaults(YamlConfiguration.loadConfiguration(inp));
            messagesConfig = cfg;
        }
        return messagesConfig;
    }

    // --- Utility

    public String getPassword(Player player) {
        for (MetadataValue meta: player.getMetadata(META_PASSWORD)) {
            if (meta.getOwningPlugin() == this) {
                return meta.asString();
            }
        }
        Random rnd = new Random((long) player.getUniqueId().hashCode() * privKey);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; ++i) {
            int n = rnd.nextInt(10 + 26);
            int c = (int) '-';
            if (n < 10) {
                c = '0' + n;
            } else {
                c = 'a' + (n - 10);
            }
            sb.append((char) c);
        }
        String password = sb.toString();
        player.setMetadata(META_PASSWORD, new FixedMetadataValue(this, password));
        return password;
    }

    void showRules(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player != null) {
            player.setMetadata(META_RULES, new FixedMetadataValue(this, true));
        }
        if (player != null) player.sendMessage("");
        for (String line: getRules()) {
            sender.sendMessage(line);
        }
        if (player != null && playerInFromGroup(player)) {
            player.sendMessage("");
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(" Click here: ");
            cb.append("[Accept]").color(ChatColor.GREEN)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                      TextComponent.fromLegacyText("Accept the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                      "/rules accept " + getPassword(player)));
            cb.append(" or ").reset();
            cb.append("[Decline]").color(ChatColor.RED)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                      TextComponent.fromLegacyText("Decline the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules decline"));
            player.spigot().sendMessage(cb.create());
        }
        if (player != null) player.sendMessage("");
    }

    boolean playerInFromGroup(Player player) {
        return !player.isOp() && !player.hasPermission(permission);
    }

    void promotePlayer(Player player) {
        for (String cmd : getConfig().getStringList("PromoteCommands")) {
            getServer().dispatchCommand(getServer().getConsoleSender(),
                                        String.format(cmd, player.getName()));
        }
        announcePromotion(player.getName());
    }

    void announcePromotion(String name) {
        List<String> anns = getConfig().getStringList("PromoteAnnouncements");
        if (!anns.isEmpty()) {
            String ann = anns.get(random.nextInt(anns.size()));
            try {
                ann = String.format(ann, name);
            } catch (IllegalFormatException ife) {
                ife.printStackTrace();
            }
            ChatPlugin.getInstance().announce("info", ann);
        }
    }

    List<String> getRules() {
        if (rules == null) {
            File file = new File(getDataFolder(), "rules.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            InputStreamReader inp = new InputStreamReader(getResource("rules.yml"));
            config.setDefaults(YamlConfiguration.loadConfiguration(inp));
            rulesConfig = config;
            rules = config.getStringList("Rules");
        }
        return rules;
    }

    void sendWelcomeMessage(Player player) {
        for (List<String> ls: (List<List<String>>) getMessagesConfig().getList("Welcome")) {
            ComponentBuilder cb = new ComponentBuilder(ls.get(1))
                .color(ChatColor.valueOf(ls.get(0)))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                      TextComponent.fromLegacyText("/rules")));
            player.spigot().sendMessage(cb.create());
        }
    }

    // --- Event Handling

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.removeMetadata(META_RULES, this);
        checkForWelcomeMessage(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
        sendWelcomeMessage(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
        sendWelcomeMessage(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
        sendWelcomeMessage(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerAttemptPickupItem(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        event.setCancelled(true);
    }

    void checkForWelcomeMessage(Player player) {
        if (!playerInFromGroup(player)) return;
        BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isValid()
                        || !playerInFromGroup(player)
                        || player.hasMetadata(META_RULES)) {
                        cancel();
                        return;
                    }
                    sendWelcomeMessage(player);
                }
            };
        task.runTaskTimer(this, 60L, 400L);
    }

    ItemStack makeRuleBook(Player player) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        getRules();
        for (String page : rulesConfig.getStringList("Book.Pages")) {
            page = ChatColor.translateAlternateColorCodes('&', page);
            meta.spigot().addPage(TextComponent.fromLegacyText(page));
        }
        if (true) {
            ComponentBuilder cb = new ComponentBuilder("Do you accept the rules? Click here: ").color(ChatColor.BLACK);
            cb.append("[Accept]").color(ChatColor.DARK_BLUE)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                      TextComponent.fromLegacyText("Accept the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                      "/rules accept " + getPassword(player)));
            cb.append(" or ").reset();
            cb.append("[Decline]").color(ChatColor.DARK_RED)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                      TextComponent.fromLegacyText("Decline the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules decline"));
            meta.spigot().addPage(cb.create());
        }
        meta.setAuthor("Cavetale");
        meta.setTitle("Rules");
        meta.setGeneration(BookMeta.Generation.TATTERED);
        item.setItemMeta(meta);
        return item;
    }
}
