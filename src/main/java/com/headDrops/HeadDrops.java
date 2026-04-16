package com.headDrops;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HeadDrops extends JavaPlugin implements Listener, CommandExecutor {

    private final NamespacedKey guillotineKey = new NamespacedKey(this, "guillotine_lvl");
    private final Random random = new Random();

    /**
     * Инициализация плагина: создание конфига, регистрация событий и команд.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("giveguillotine") != null) getCommand("giveguillotine").setExecutor(this);
        if (getCommand("headdrops") != null) getCommand("headdrops").setExecutor(this);

        getLogger().info("HeadDrops by _MrLisick_ успешно запущен!");
    }

    /**
     * Обработка команд /headdrops reload и /giveguillotine.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Логика перезагрузки конфига
        if (command.getName().equalsIgnoreCase("headdrops")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("headdrops.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "[HeadDrops] Конфигурация обновлена!");
                return true;
            }
        }

        // Логика выдачи книги игроку
        if (command.getName().equalsIgnoreCase("giveguillotine")) {
            if (!(sender instanceof Player player)) return true;
            if (!player.hasPermission("headdrops.admin")) return true;

            try {
                int level = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
                level = Math.max(1, Math.min(3, level)); // Ограничение 1-3
                player.getInventory().addItem(createGuillotineBook(level));
                player.sendMessage(ChatColor.GREEN + "Вы получили книгу Гильотина " + level);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Используйте: /giveguillotine <1-3>");
            }
            return true;
        }
        return false;
    }

    /**
     * Создает предмет зачарованной книги с кастомными данными (PDC).
     */
    private ItemStack createGuillotineBook(int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Зачарованная книга: Гильотина " + level);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Применяется к: " + ChatColor.YELLOW + "Топор");
            lore.add(ChatColor.GRAY + "Шанс головы: " + ChatColor.GREEN + getConfig().getDouble("chances.level_" + level) + "%");
            meta.setLore(lore);
            // Сохраняем уровень чара прямо в предмет
            meta.getPersistentDataContainer().set(guillotineKey, PersistentDataType.INTEGER, level);
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * Добавляет шанс получения чара при использовании стола зачарований.
     */
    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (!event.getItem().getType().name().contains("_AXE")) return;

        double roll = random.nextDouble() * 100;
        int level = 0;
        if (roll <= getConfig().getDouble("enchant-table.level_3")) level = 3;
        else if (roll <= getConfig().getDouble("enchant-table.level_2")) level = 2;
        else if (roll <= getConfig().getDouble("enchant-table.level_1")) level = 1;

        if (level > 0) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add(ChatColor.GRAY + "Гильотина " + level);
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(guillotineKey, PersistentDataType.INTEGER, level);
                event.getItem().setItemMeta(meta);
            }
        }
    }

    /**
     * Добавляет книгу Гильотины в списки лута сундуков при генерации мира.
     */
    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (random.nextDouble() * 100 <= getConfig().getDouble("dungeon-loot-chance")) {
            event.getLoot().add(createGuillotineBook(random.nextInt(3) + 1));
        }
    }

    /**
     * Обрабатывает объединение топора и книги на наковальне.
     */
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack baseItem = event.getInventory().getItem(0);
        ItemStack enchantBook = event.getInventory().getItem(1);

        if (baseItem == null || enchantBook == null) return;
        if (!baseItem.getType().name().contains("_AXE") || enchantBook.getType() != Material.ENCHANTED_BOOK) return;

        ItemMeta bookMeta = enchantBook.getItemMeta();
        if (bookMeta == null || !bookMeta.getPersistentDataContainer().has(guillotineKey, PersistentDataType.INTEGER)) return;

        int level = bookMeta.getPersistentDataContainer().get(guillotineKey, PersistentDataType.INTEGER);
        ItemStack result = baseItem.clone();
        ItemMeta resultMeta = result.getItemMeta();

        if (resultMeta != null) {
            List<String> lore = resultMeta.hasLore() ? resultMeta.getLore() : new ArrayList<>();
            lore.removeIf(line -> line.contains("Гильотина")); // Удаляем старый уровень, если был
            lore.add(ChatColor.GRAY + "Гильотина " + level);
            resultMeta.setLore(lore);
            resultMeta.getPersistentDataContainer().set(guillotineKey, PersistentDataType.INTEGER, level);
            result.setItemMeta(resultMeta);
        }

        event.setResult(result);

        // Установка цены опыта (в планировщике, чтобы избежать багов GUI)
        Bukkit.getScheduler().runTask(this, () -> {
            if (event.getInventory() != null) event.getInventory().setRepairCost(5 * level);
        });
    }

    /**
     * Основная механика: шанс выпадения головы игрока при смерти от топора с Гильотиной.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR || !weapon.hasItemMeta()) return;

        ItemMeta wMeta = weapon.getItemMeta();
        if (wMeta == null || !wMeta.getPersistentDataContainer().has(guillotineKey, PersistentDataType.INTEGER)) return;

        int level = wMeta.getPersistentDataContainer().get(guillotineKey, PersistentDataType.INTEGER);

        if (random.nextDouble() * 100 <= getConfig().getDouble("chances.level_" + level)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            if (headMeta != null) {
                headMeta.setOwningPlayer(victim);
                headMeta.setDisplayName(ChatColor.RED + "Голова " + victim.getName());

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GRAY + "Убийца: " + ChatColor.YELLOW + killer.getName());
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "⚡ Plugin by " + ChatColor.ITALIC + "_MrLisick_");

                headMeta.setLore(lore);
                head.setItemMeta(headMeta);
            }
            event.getDrops().add(head);
        }
    }
}