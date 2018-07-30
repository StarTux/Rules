package com.winthier.rules;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class RulesPlugin extends JavaPlugin {
    private final long privKey = new Random(System.currentTimeMillis()).nextLong();
    private static final String META_PASSWORD = "rules.password";
    private List<String> rules;

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
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
                    Msg.send(player, "You have been promoted to &a%s&r!", getToGroup());
                } else {
                    showRules(player);
                }
            } else {
                Msg.warn(player, "You already accepted the rules.");
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (player == null) return false;
            if (playerInFromGroup(player)) {
                player.kickPlayer("You mush accept the rules.");
            } else {
                Msg.warn(player, "You already accepted the rules.");
            }
        } else if (firstArg.equals("pwof") && args.length == 2) {
            if (!sender.hasPermission("rules.admin")) return false;
            String targetName = args[1];
            Player target = getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("Player not found: " + targetName);
                return true;
            }
            String password = getPassword(target);
            Msg.send(sender, "Password of %s: '%s'", target.getName(), password);
        } else if (firstArg.equals("save") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            saveDefaultConfig();
            sender.sendMessage("[Rules] Default configuration saved.");
        } else if (firstArg.equals("reload") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            reloadConfig();
            rules = null;
            sender.sendMessage("[Rules] Configuration reloaded.");
        } else {
            return false;
        }
        return true;
    }

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

    String getFromGroup() {
        return getConfig().getString("FromGroup", "Guest");
    }

    String getToGroup() {
        return getConfig().getString("ToGroup", "Friendly");
    }

    void showRules(CommandSender sender) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player != null) player.sendMessage("");
        Msg.send(sender, "&a&lRules");
        for (String line: getRules()) {
            Msg.send(sender, " %s", Msg.format(line));
        }
        if (player != null) {
            player.sendMessage("");
            if (playerInFromGroup(player)) {
                List<Object> json = new ArrayList<>();
                json.add(" Click here: ");
                json.add(Msg.button(ChatColor.GREEN, "[Accept]", "Accept the rules", "/rules accept " + getPassword(player)));
                json.add(" ");
                json.add(Msg.button(ChatColor.DARK_RED, "[Decline]", "Decline the rules", "/rules decline"));
                Msg.raw(player, json);
            }
        }
    }

    boolean playerInFromGroup(Player player) {
        return !player.hasPermission(getConfig().getString("Permisson"));
    }

    void promotePlayer(Player player) {
        for (String cmd : getConfig().getStringList("PromoteCommands")) {
            getServer().dispatchCommand(getServer().getConsoleSender(), String.format(cmd, player.getName()));
        }
    }

    List<String> getRules() {
        if (rules == null) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "rules.yml"));
            rules = config.getStringList("Rules");
        }
        return rules;
    }
}
