package com.winthier.rules;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class RulesCommand implements CommandExecutor {
    final RulesPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.showRules(sender);
            return true;
        }
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("accept") && args.length == 2) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (plugin.playerInFromGroup(player)) {
                plugin.acceptRules(player, args[1]);
            } else {
                Msg.warn(player, "You already accepted the rules.");
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (player == null) return false;
            if (plugin.playerInFromGroup(player)) {
                player.kickPlayer("You mush accept the rules.");
            } else {
                Msg.warn(player, "You already accepted the rules.");
            }
        } else if (firstArg.equals("pwof") && args.length == 2) {
            if (!sender.hasPermission("rules.admin")) return false;
            String targetName = args[1];
            Player target = plugin.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("Player not found: " + targetName);
                return true;
            }
            Password password = plugin.getPassword(target);
            Msg.send(sender, "Password of %s: '%s'", target.getName(), password.getPw());
        } else if (firstArg.equals("save") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            plugin.saveDefaultConfig();
            sender.sendMessage("[Rules] Default configuration saved.");
        } else if (firstArg.equals("reload") && args.length == 1) {
            if (!sender.hasPermission("rules.admin")) return false;
            plugin.reloadConfig();
            sender.sendMessage("[Rules] Configuration reloaded.");
        } else {
            return false;
        }
        return true;
    }
}
