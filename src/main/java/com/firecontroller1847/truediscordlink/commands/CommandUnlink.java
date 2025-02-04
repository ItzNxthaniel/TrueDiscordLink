package com.firecontroller1847.truediscordlink.commands;

import com.firecontroller1847.truediscordlink.DatabaseManager;
import com.firecontroller1847.truediscordlink.FireCommand;
import com.firecontroller1847.truediscordlink.FirePlugin;
import com.firecontroller1847.truediscordlink.TrueDiscordLink;
import com.firecontroller1847.truediscordlink.database.DbPlayer;
import com.visualfiredev.javabase.Database;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CommandUnlink extends FireCommand {

    public CommandUnlink(FirePlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Check if linking is enabled
        if (!plugin.getConfig().getBoolean("bot.linking.enabled")) {
            sender.sendMessage(plugin.getTranslation("linking.disabled"));
            return true;
        }

        // Check against console
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(plugin.getTranslation("no_console_usage"));
            return true;
        }

        // Fetch database & player
        Player player = (Player) sender;
        DatabaseManager databaseManager = ((TrueDiscordLink) plugin).getDatabaseManager();
        Database database = databaseManager.getDatabase();

        // Validate database connection
        databaseManager.validateConnection();

        try {
            ArrayList<DbPlayer> results;
            results = database.select(DbPlayer.getTableSchema(database), DbPlayer.class, "minecraft_uuid = ?", player.getUniqueId().toString());
            if (results.size() == 0) {
                player.sendMessage(plugin.getTranslation("linking.does_not_exist"));
                return true;
            }

            // Get user from Discord
            String userId = results.get(0).getDiscordId();
            if (userId != null) {
                // Search for roles
                for (String guildAndRole : plugin.getConfig().getStringList("bot.linking.roles")) {
                    String[] parts = guildAndRole.split(":");
                    String guildId = parts[0];
                    String roleId = parts[1];

                    // Search for guild
                    ((TrueDiscordLink) plugin).getDiscordManager().getApi().getServerById(guildId).ifPresent(server -> {
                        // Search for role
                        server.getRoleById(roleId).ifPresent(role -> {
                            // Remove role from user
                            server.getMemberById(userId).ifPresent(serverUser -> {
                                try {
                                    serverUser.removeRole(role);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        });
                    });
                }
            }

            // Delete From Database
            database.delete(DbPlayer.getTableSchema(database), "minecraft_uuid = '" + player.getUniqueId().toString() + "'");

            // Unlink
            player.sendMessage(plugin.getTranslation("linking.unlink"));

            // Notify channel on successful account unlinking.
            if (plugin.getConfig().getBoolean("bot.linking.notify.unlink.enabled")) {
                TextChannel channel = ((TrueDiscordLink) plugin).getDiscordManager().getApi().getChannelById(plugin.getConfig().getString("bot.linking.notify.unlink.channel")).orElseThrow(() -> new Exception("Unlink Notification Channel cannot be null!")).asTextChannel().orElseThrow(() -> new Exception("Unlink Notification Channel must be a text channel"));
                channel.sendMessage(plugin.getTranslation("linking.discord.notify.unlink",
                        new String[] { "%username%", player.getName() },
                        new String[] { "%tag%", ((TrueDiscordLink) plugin).getDiscordManager().getApi().getUserById(userId).get().getDiscriminatedName() },
                        new String[] { "%mention%", "<@" + userId + ">"}
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(plugin.getTranslation("linking.failure"));
        }

        // The command always works
        return true;
    }

}
