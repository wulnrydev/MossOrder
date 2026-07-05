package dev.wulnry.mossorder.command;

import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.menu.MainMenu;
import dev.wulnry.mossorder.menu.MenuContext;
import dev.wulnry.mossorder.menu.WarehouseMenu;
import dev.wulnry.mossorder.util.Numbers;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * /siparis komutu: ana menüyü açar ve hızlı sipariş oluşturmayı sağlar.
 */
public final class OrderCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CREATE_ALIASES = List.of("oluştur", "olustur", "create", "yeni");
    private static final List<String> WAREHOUSE_ALIASES = List.of("depo", "warehouse");

    private final MenuContext ctx;
    private final Messages messages;

    public OrderCommand(MenuContext ctx) {
        this.ctx = ctx;
        this.messages = ctx.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("mossorder.use")) {
            messages.send(player, "general.no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            new MainMenu(ctx, player).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("yardım") || sub.equals("yardim") || sub.equals("help")) {
            sendHelp(player);
            return true;
        }
        if (isCreateAlias(sub)) {
            handleCreate(player, args);
            return true;
        }
        if (isWarehouseAlias(sub)) {
            new WarehouseMenu(ctx, player).open();
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("mossorder.create")) {
            messages.send(player, "general.no-permission");
            return;
        }
        if (args.length < 4) {
            messages.sendList(player, "help.user");
            return;
        }
        Material material = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.matchMaterial(args[1]);
        }
        if (material == null) {
            messages.send(player, "order.invalid-item", "{input}", args[1]);
            return;
        }
        OptionalInt amount = Numbers.parsePositiveInt(args[2]);
        if (amount.isEmpty()) {
            messages.send(player, "general.invalid-number", "{input}", args[2]);
            return;
        }
        OptionalDouble price = Numbers.parsePositiveDouble(args[3]);
        if (price.isEmpty()) {
            messages.send(player, "general.invalid-number", "{input}", args[3]);
            return;
        }
        ctx.orders().createOrder(player, material, amount.getAsInt(), price.getAsDouble(), success -> {
        });
    }

    private void sendHelp(Player player) {
        messages.send(player, "help.header");
        messages.sendList(player, "help.user");
        if (player.hasPermission("mossorder.admin")) {
            messages.sendList(player, "help.admin");
        }
    }

    private boolean isCreateAlias(String value) {
        for (String alias : CREATE_ALIASES) {
            if (alias.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWarehouseAlias(String value) {
        for (String alias : WAREHOUSE_ALIASES) {
            if (alias.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            addIfMatches(result, args[0], "oluştur", "menu", "yardım", "depo");
            return result;
        }
        if (args.length >= 2 && isCreateAlias(args[0].toLowerCase(Locale.ROOT))) {
            switch (args.length) {
                case 2 -> {
                    String token = args[1].toLowerCase(Locale.ROOT);
                    int count = 0;
                    for (Material material : ctx.catalog().all()) {
                        String key = material.name().toLowerCase(Locale.ROOT);
                        if (key.startsWith(token)) {
                            result.add(key);
                            if (++count >= 40) {
                                break;
                            }
                        }
                    }
                }
                case 3 -> addIfMatches(result, args[2], "1", "16", "32", "64");
                case 4 -> addIfMatches(result, args[3], "10", "50", "100", "1000");
                default -> {
                }
            }
        }
        return result;
    }

    private void addIfMatches(List<String> result, String token, String... options) {
        String lower = token.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
    }
}
