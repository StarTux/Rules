package com.winthier.rules;

import com.cavetale.core.font.DefaultFont;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.winthier.chat.ChatPlugin;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
                sender.sendMessage("[rules:rules] player expected");
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
                }
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player == null) return false;
            player.kick(Component.text("You must accept the rules!", NamedTextColor.RED));
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
        if (ChatPlugin.getInstance().containsBadWord(name)) {
            getLogger().warning("Promoted player name contains bad word: " + name);
            Bukkit.broadcast(Component.text("[Rules] Promoted player name contains bad word: " + name, NamedTextColor.RED),
                             "rules.admin");
            return;
        }
        List<String> anns = getConfig().getStringList("PromoteAnnouncements");
        if (!anns.isEmpty()) {
            String ann = anns.get(random.nextInt(anns.size()));
            try {
                ann = String.format(ann, name);
            } catch (IllegalFormatException ife) {
                ife.printStackTrace();
            }
            ChatPlugin.getInstance().announce("info", Component.text(ann, NamedTextColor.GREEN, TextDecoration.BOLD));
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

    @SuppressWarnings("unchecked")
    void sendWelcomeMessage(Player player) {
        List<ComponentLike> lines = new ArrayList<>();
        for (List<String> ls: (List<List<String>>) getMessagesConfig().getList("Welcome")) {
            lines.add(Component.text()
                      .content(ls.get(1))
                      .color(NamedTextColor.NAMES.value(ls.get(0)))
                      .clickEvent(ClickEvent.runCommand("/rules"))
                      .hoverEvent(HoverEvent.showText(Component.text("/rules", NamedTextColor.YELLOW))));
        }
        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
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
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerAdvancementCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
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
        getRules();
        List<Component> pages = new ArrayList<>();
        for (String page : rulesConfig.getStringList("Book.Pages")) {
            pages.add(Component.text(ChatColor.translateAlternateColorCodes('&', page)));
        }
        pages.add(Component.join(JoinConfiguration.noSeparators(), new ComponentLike[] {
                    Component.text("Do you accept the rules? Click here: "),
                    Component.newline(),
                    Component.newline(),
                    DefaultFont.ACCEPT_BUTTON.component
                    .hoverEvent(HoverEvent.showText(Component.text("Accept the rules", NamedTextColor.GREEN)))
                    .clickEvent(ClickEvent.runCommand("/rules accept " + getPassword(player))),
                    Component.text(" or "),
                    DefaultFont.DECLINE_BUTTON.component
                    .hoverEvent(HoverEvent.showText(Component.text("Decline the rules", NamedTextColor.RED)))
                    .clickEvent(ClickEvent.runCommand("/rules decline")),
                }));
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        item.editMeta(m -> {
                BookMeta meta = (BookMeta) m;
                meta.setAuthor("Cavetale");
                meta.title(Component.text("Rules"));
                meta.pages(pages);
                meta.setGeneration(BookMeta.Generation.TATTERED);
            });
        return item;
    }
}
