package com.winthier.rules;

import com.winthier.chat.ChatPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RulesPlugin extends JavaPlugin {
    final int salt = new Random(System.currentTimeMillis()).nextInt();
    Permission permission = null;
    final Map<UUID, Password> passwords = new HashMap<>();

    @Override
    public void onEnable() {
        reloadConfig();
        getCommand("rules").setExecutor(new RulesCommand(this));
    }

    public Permission getPermission() {
        if (permission == null) {
            RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            if (permissionProvider != null) permission = permissionProvider.getProvider();
        }
        return permission;
    }

    public Password getPassword(Player player) {
        UUID uuid = player.getUniqueId();
        Password password = passwords.get(uuid);
        if (password == null) {
            password = Password.of(uuid, salt);
            passwords.put(uuid, password);
        }
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
        for (String line: getConfig().getStringList("Rules")) {
            Msg.send(sender, " %s", Msg.format(line));
        }
        if (player != null) {
            player.sendMessage("");
            if (playerInFromGroup(player)) {
                List<Object> json = new ArrayList<>();
                json.add(" Click here: ");
                json.add(Msg.button(ChatColor.GREEN, "[Accept]", "Accept the rules", "/rules accept " + getPassword(player).getPw()));
                json.add(" ");
                json.add(Msg.button(ChatColor.DARK_RED, "[Decline]", "Decline the rules", "/rules decline"));
                Msg.raw(player, json);
            }
            player.sendMessage("");
        }
    }

    void acceptRules(Player player, String password) {
        if (playerInFromGroup(player) && getPassword(player).getPw().equalsIgnoreCase(password)) {
            promotePlayer(player);
            Msg.send(player, "You have been promoted to &a%s&r!", getToGroup());
            ChatPlugin.getInstance().announce("Info", Msg.format("%s has been promoted to %s.", player.getName(), getToGroup()));
        } else {
            showRules(player);
        }
    }

    boolean playerInFromGroup(Player player) {
        return !player.hasPermission("rules.accepted");
    }

    void promotePlayer(Player player) {
        getPermission().playerAddGroup(player, getToGroup());
        getPermission().playerRemoveGroup(player, getFromGroup());
    }
}
