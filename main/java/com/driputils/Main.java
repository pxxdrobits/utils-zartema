package com.driputils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin {
    
    private Configuration config;
    private Map<String, Long> reportCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        loadConfig();
        
        getLogger().info("DripUtils ativado com sucesso!");
        
        // Registra os comandos
        getProxy().getPluginManager().registerCommand(this, new ReportCommand());
        getProxy().getPluginManager().registerCommand(this, new GoCommand());
    }

    @Override
    public void onDisable() {
        getLogger().info("DripUtils desativado!");
    }
    
    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private String getMessage(String path, String defaultValue) {
        if (config != null && config.contains(path)) {
            return ChatColor.translateAlternateColorCodes('&', config.getString(path));
        }
        return ChatColor.translateAlternateColorCodes('&', defaultValue);
    }
    
    private int getInt(String path, int defaultValue) {
        if (config != null && config.contains(path)) {
            return config.getInt(path);
        }
        return defaultValue;
    }


    public class ReportCommand extends Command implements TabExecutor {
        
        public ReportCommand() {
            super("report", "drip.report", "reportar");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Este comando só pode ser usado por jogadores!"));
                return;
            }

            if (args.length < 2) {
                sender.sendMessage(new TextComponent(getMessage("report.messages.usage", "&cUso: /report <jogador> <motivo>")));
                return;
            }

            ProxiedPlayer reporter = (ProxiedPlayer) sender;
            String targetName = args[0];
            String reason = String.join(" ", args).substring(args[0].length()).trim();

            int cooldown = getInt("report.cooldown", 30);
            if (cooldown > 0) {
                String reporterName = reporter.getName().toLowerCase();
                long currentTime = System.currentTimeMillis();
                if (reportCooldowns.containsKey(reporterName)) {
                    long lastReport = reportCooldowns.get(reporterName);
                    long timeLeft = (lastReport + (cooldown * 1000)) - currentTime;
                    if (timeLeft > 0) {
                        String cooldownMsg = getMessage("report.messages.cooldown", "&cAguarde {time} segundos antes de fazer outro report!")
                                .replace("{time}", String.valueOf(timeLeft / 1000));
                        sender.sendMessage(new TextComponent(cooldownMsg));
                        return;
                    }
                }
                reportCooldowns.put(reporterName, currentTime);
            }

            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(new TextComponent(getMessage("report.messages.player-not-found", "&cJogador não encontrado!")));
                return;
            }

            if (target.equals(reporter)) {
                sender.sendMessage(new TextComponent(getMessage("report.messages.self-report", "&cVocê não pode se reportar!")));
                return;
            }

            String reportMessage = ChatColor.RED + "[REPORT] " + ChatColor.YELLOW + reporter.getName() + 
                                 ChatColor.RED + " reportou " + ChatColor.YELLOW + target.getName() + 
                                 ChatColor.RED + " por: " + ChatColor.WHITE + reason;

            for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                if (player.hasPermission("drip.report.notify")) {
                    player.sendMessage(new TextComponent(reportMessage));
                }
            }

            sender.sendMessage(new TextComponent(getMessage("report.messages.success", "&aReport enviado com sucesso para a equipe!")));
           
            if (getInt("general.console-logging", 1) == 1) {
                ProxyServer.getInstance().getLogger().info("[REPORT] " + reporter.getName() + " reportou " + target.getName() + " por: " + reason);
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) {
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                String partialName = args[0].toLowerCase();
                for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                    if (player.getName().toLowerCase().startsWith(partialName)) {
                        completions.add(player.getName());
                    }
                }
            }
            
            return completions;
        }
    }

    // Comando de Go (Teleporte)
    public class GoCommand extends Command implements TabExecutor {
        
        public GoCommand() {
            super("go", "drip.go", "ir");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Este comando só pode ser usado por jogadores!"));
                return;
            }

            if (args.length != 1) {
                sender.sendMessage(new TextComponent(getMessage("go.messages.usage", "&cUso: /go <jogador>")));
                return;
            }

            ProxiedPlayer teleporter = (ProxiedPlayer) sender;
            String targetName = args[0];

            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(new TextComponent(getMessage("go.messages.player-not-found", "&cJogador não encontrado!")));
                return;
            }

            if (target.equals(teleporter)) {
                sender.sendMessage(new TextComponent(getMessage("go.messages.already-connected", "&cVocê já está conectado!")));
                return;
            }

            ServerInfo targetServer = target.getServer().getInfo();
            
            teleporter.connect(targetServer);
            
            int delay = getInt("go.teleport-delay", 1);
            ProxyServer.getInstance().getScheduler().schedule(Main.this, () -> {
                if (teleporter.isConnected() && target.isConnected()) {
                    // Envia comando de teleporte para o servidor
                    targetServer.ping((result, error) -> {
                        if (error == null) {
                            target.getServer().sendData("DripUtils", ("tp " + teleporter.getName() + " " + target.getName()).getBytes());
                        }
                    });
                }
            }, delay, TimeUnit.SECONDS);

            String teleportMsg = getMessage("go.messages.teleporting", "&aTeleportando para {player}...")
                    .replace("{player}", target.getName());
            sender.sendMessage(new TextComponent(teleportMsg));
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) {
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                String partialName = args[0].toLowerCase();
                for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                    if (player.getName().toLowerCase().startsWith(partialName)) {
                        completions.add(player.getName());
                    }
                }
            }
            
            return completions;
        }
    }
}
