package com.winthier.rules;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import java.io.File;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Random;

public final class RulesPlugin extends PluginBase implements Listener {
    private Random random;
    private long privKey;
    private static final String META_PASSWORD = "rules.password";
    private List<String> rules;
    private Config messagesConfig;

    // -- Plugin overrides

    @Override
    public void onEnable() {
        random = new Random(System.nanoTime());
        privKey = random.nextLong();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showRules(sender);
            return true;
        }
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("accept") && args.length == 1) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (playerInFromGroup(player)) {
                promotePlayer(player);
                player.sendMessage(getMessagesConfig().getString("Promotion"));
            }
        } else if (firstArg.equals("decline")) {
            Player player = sender instanceof Player ? (Player)sender : null;
            if (player == null) return false;
            player.kick(getMessagesConfig().getString("Decline"));
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

    Config getMessagesConfig() {
        if (this.messagesConfig == null) {
            Config cfg = new Config(new File(getDataFolder(), "messages.yml"));
            Config dfl = new Config();
            dfl.load(getResource("messages.yml"));
            cfg.setDefault(dfl.getRootSection());
            this.messagesConfig = cfg;
        }
        return this.messagesConfig;
    }

    // --- Utility

    public String getPassword(Player player) {
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
            player.sendMessage(TextFormat.GRAY + "To accept, type " + TextFormat.GREEN + "/rules accept");
            player.sendMessage(TextFormat.GRAY + "To decline, type " + TextFormat.RED + "/rules decline");
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
        List<String> anns = getConfig().getStringList("PromoteAnnouncements");
        if (!anns.isEmpty()) {
            String ann = anns.get(random.nextInt(anns.size()));
            try {
                ann = String.format(ann, player.getName());
            } catch (IllegalFormatException ife) {
                ife.printStackTrace();
            }
            for (Player target: getServer().getOnlinePlayers().values()) {
                target.sendMessage("");
                target.sendMessage(ann);
                target.sendMessage("");
            }
        }
    }

    List<String> getRules() {
        if (rules == null) {
            Config cfg = new Config(new File(getDataFolder(), "rules.yml"));
            Config dfl = new Config();
            dfl.load(getResource("rules.yml"));
            cfg.setDefault(dfl.getRootSection());
            rules = cfg.getStringList("Rules");
        }
        return rules;
    }

    void sendWelcomeMessage(Player player) {
        player.sendMessage(getMessagesConfig().getString("Welcome"));
    }

    // --- Event Handling

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerInFromGroup(player)) return;
        getServer().getScheduler().scheduleDelayedTask(this, () -> sendWelcomeMessage(player), 60);
    }
}
