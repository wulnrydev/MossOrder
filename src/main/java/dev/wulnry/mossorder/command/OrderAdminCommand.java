package dev.wulnry.mossorder.command;

import dev.wulnry.mossorder.MossOrder;
import dev.wulnry.mossorder.config.Messages;
import dev.wulnry.mossorder.model.Order;
import dev.wulnry.mossorder.service.OrderService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /siparisadmin komutu: siparişleri zorla kaldırma ve eklentiyi yeniden yükleme.
 */
public final class OrderAdminCommand implements CommandExecutor, TabCompleter {

    private final MossOrder plugin;
    private final OrderService orders;
    private final Messages messages;

    public OrderAdminCommand(MossOrder plugin, OrderService orders, Messages messages) {
        this.plugin = plugin;
        this.orders = orders;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mossorder.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.sendList(sender, "help.admin");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sil", "delete", "remove" -> {
                if (args.length < 2) {
                    messages.sendList(sender, "help.admin");
                    return true;
                }
                Long id = parseLong(args[1]);
                if (id == null) {
                    messages.send(sender, "general.invalid-number", "{input}", args[1]);
                    return true;
                }
                orders.adminDelete(sender, id, () -> {
                });
            }
            case "reload", "yenile" -> {
                plugin.reload();
                messages.send(sender, "general.reloaded");
            }
            default -> messages.sendList(sender, "help.admin");
        }
        return true;
    }

    private Long parseLong(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("mossorder.admin")) {
            return result;
        }
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("sil", "reload")) {
                if (option.startsWith(token)) {
                    result.add(option);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("sil"))) {
            int count = 0;
            for (Order order : orders.cache().snapshot()) {
                result.add(String.valueOf(order.getId()));
                if (++count >= 30) {
                    break;
                }
            }
        }
        return result;
    }
}
