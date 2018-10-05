package com.winthier.rules;

import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class RulesPlugin extends JavaPlugin implements Listener {
    private final long privKey = new Random(System.currentTimeMillis()).nextLong();
    private static final String META_PASSWORD = "rules.password";
    private List<String> rules;
    private ConfigurationSection messagesConfig;

    // -- Plugin overrides

    @Override
    public void onEnable() {
        reloadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showRules(sender);
            return true;
        }
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("accept") && args.length == 2) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (playerInFromGroup(player)) {
                if (getPassword(player).equalsIgnoreCase(args[1])) {
                    promotePlayer(player);
                    player.sendMessage(getMessagesConfig().getString("Promotion"));
                } else {
                    showRules(player);
                }
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player)sender : null;
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
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("messages.yml"))));
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
        Random rnd = new Random((long)player.getUniqueId().hashCode() * privKey);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; ++i) {
            int n = rnd.nextInt(10 + 26);
            int c = (int)'-';
            if (n < 10) {
                c = '0' + n;
            } else {
                c = 'a' + (n - 10);
            }
            sb.append((char)c);
        }
        String password = sb.toString();
        player.setMetadata(META_PASSWORD, new FixedMetadataValue(this, password));
        return password;
    }

    void showRules(CommandSender sender) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player != null) player.sendMessage("");
        for (String line: getRules()) {
            sender.sendMessage(line);
        }
        if (player != null && playerInFromGroup(player)) {
            player.sendMessage("");
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(" Click here: ");
            cb.append("[Accept]").color(ChatColor.GREEN)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Accept the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules accept " + getPassword(player)));
            cb.append(" or ").reset();
            cb.append("[Decline]").color(ChatColor.RED)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Decline the rules")))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules decline"));
            player.spigot().sendMessage(cb.create());
            player.sendMessage("");
        }
    }

    boolean playerInFromGroup(Player player) {
        return !player.hasPermission(getConfig().getString("Permission"));
    }

    void promotePlayer(Player player) {
        for (String cmd : getConfig().getStringList("PromoteCommands")) {
            getServer().dispatchCommand(getServer().getConsoleSender(), String.format(cmd, player.getName()));
        }
    }

    List<String> getRules() {
        if (rules == null) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "rules.yml"));
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("rules.yml"))));
            rules = config.getStringList("Rules");
        }
        return rules;
    }

    void sendWelcomeMessage(Player player) {
        for (List<String> ls: (List<List<String>>)getMessagesConfig().getList("Welcome")) {
            ComponentBuilder cb = new ComponentBuilder(ls.get(1))
                .color(ChatColor.valueOf(ls.get(0)))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("/rules")));
            player.spigot().sendMessage(cb.create());
        }
    }

    // --- Event Handling

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        getServer().getScheduler().runTaskLater(this, () -> sendWelcomeMessage(player), 60L);
    }
}
