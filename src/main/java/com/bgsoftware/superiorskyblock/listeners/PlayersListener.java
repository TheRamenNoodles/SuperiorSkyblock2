package com.bgsoftware.superiorskyblock.listeners;

import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterProtectedEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveProtectedEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPermission;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.island.SpawnIsland;
import com.bgsoftware.superiorskyblock.utils.StringUtils;
import com.bgsoftware.superiorskyblock.utils.items.ItemUtils;
import com.bgsoftware.superiorskyblock.utils.legacy.Materials;
import com.bgsoftware.superiorskyblock.utils.threads.Executor;
import com.bgsoftware.superiorskyblock.wrappers.SSuperiorPlayer;
import com.bgsoftware.superiorskyblock.wrappers.SBlockPosition;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
public final class PlayersListener implements Listener {

    private SuperiorSkyblockPlugin plugin;

    public PlayersListener(SuperiorSkyblockPlugin plugin){
        this.plugin = plugin;
        new PlayerArrowPickup();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        superiorPlayer.updateLastTimeStatus();

        if(!superiorPlayer.getName().equals(e.getPlayer().getName())){
            superiorPlayer.updateName();
        }
        plugin.getNMSAdapter().setSkinTexture(superiorPlayer);

        Island island = superiorPlayer.getIsland();

        if(island != null && !Locale.PLAYER_JOIN_ANNOUNCEMENT.isEmpty())
            island.sendMessage(Locale.PLAYER_JOIN_ANNOUNCEMENT.getMessage(superiorPlayer.getName()), superiorPlayer.getUniqueId());

        Executor.async(() -> {
            if(!Locale.GOT_INVITE.isEmpty()){
                for(Island _island : plugin.getGrid().getIslands()){
                    if(_island.isInvited(superiorPlayer)){
                        TextComponent textComponent = new TextComponent(Locale.GOT_INVITE.getMessage(_island.getOwner().getName()));
                        if(!Locale.GOT_INVITE_TOOLTIP.isEmpty())
                            textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] {new TextComponent(Locale.GOT_INVITE_TOOLTIP.getMessage())}));
                        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is accept " + _island.getOwner().getName()));
                        superiorPlayer.asPlayer().spigot().sendMessage(textComponent);
                    }
                }
            }
        }, 40L);

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        superiorPlayer.updateLastTimeStatus();

        Island island = superiorPlayer.getIsland();

        if(island != null && !Locale.PLAYER_QUIT_ANNOUNCEMENT.isEmpty())
            island.sendMessage(Locale.PLAYER_QUIT_ANNOUNCEMENT.getMessage(superiorPlayer.getName()), superiorPlayer.getUniqueId());

        for(Island _island : plugin.getGrid().getIslands()){
            if(_island.isCoop(superiorPlayer)) {
                _island.removeCoop(superiorPlayer);
                if(!Locale.UNCOOP_LEFT_ANNOUNCEMENT.isEmpty())
                    _island.sendMessage(Locale.UNCOOP_LEFT_ANNOUNCEMENT.getMessage(superiorPlayer.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIslandEnter(IslandEnterEvent e){
        if(!e.getPlayer().hasBypassModeEnabled() && e.getIsland().isBanned(e.getPlayer())) {
            e.setCancelled(true);
            Locale.BANNED_FROM_ISLAND.send(e.getPlayer());
            if(e.getCause() == IslandEnterEvent.EnterCause.PLAYER_JOIN)
                e.setCancelTeleport(plugin.getGrid().getSpawnIsland().getCenter());
            return;
        }

        if(e.getIsland().isLocked() && !e.getIsland().hasPermission(e.getPlayer(), IslandPermission.CLOSE_BYPASS)){
            e.setCancelled(true);
            Locale.NO_CLOSE_BYPASS.send(e.getPlayer());
            if(e.getCause() == IslandEnterEvent.EnterCause.PLAYER_JOIN)
                e.setCancelTeleport(plugin.getGrid().getSpawnIsland().getCenter());
            return;
        }

        if(e.getIsland().equals(e.getPlayer().getIsland()) && e.getPlayer().hasIslandFlyEnabled()){
            Player player = e.getPlayer().asPlayer();
            player.setAllowFlight(true);
            player.setFlying(true);
            Locale.ISLAND_FLY_ENABLED.send(player);
        }

        IslandEnterProtectedEvent islandEnterProtectedEvent = new IslandEnterProtectedEvent(e.getPlayer(), e.getIsland(), e.getCause());
        Bukkit.getPluginManager().callEvent(islandEnterProtectedEvent);
        if(islandEnterProtectedEvent.isCancelled()) {
            e.setCancelled(true);
            if(islandEnterProtectedEvent.getCancelTeleport() != null)
                e.setCancelTeleport(islandEnterProtectedEvent.getCancelTeleport());
        }
    }

    @EventHandler
    public void onIslandEnterProtected(IslandEnterProtectedEvent e){
        Executor.sync(() -> {
            try {
                plugin.getNMSAdapter().setWorldBorder(e.getPlayer(), plugin.getGrid().getIslandAt(e.getPlayer().getLocation()));
            } catch (NullPointerException ignored) { }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIslandLeave(IslandLeaveEvent e){
        if(e.getIsland().equals(e.getPlayer().getIsland()) && e.getPlayer().hasIslandFlyEnabled()){
            Player player = e.getPlayer().asPlayer();
            if(player.getGameMode() != GameMode.CREATIVE) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            Locale.ISLAND_FLY_DISABLED.send(player);
        }

        IslandLeaveProtectedEvent islandLeaveProtectedEvent = new IslandLeaveProtectedEvent(e.getPlayer(), e.getIsland(), e.getCause());
        Bukkit.getPluginManager().callEvent(islandLeaveProtectedEvent);
        if(islandLeaveProtectedEvent.isCancelled())
            e.setCancelled(true);
    }

    @EventHandler
    public void onIslandLeaveProtected(IslandLeaveProtectedEvent e){
        Executor.sync(() -> {
            if(e.getPlayer().isOnline())
                plugin.getNMSAdapter().setWorldBorder(e.getPlayer(), plugin.getGrid().getIslandAt(e.getPlayer().getLocation()));
        }, 5L);
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player))
            return;

        SuperiorPlayer targetPlayer = SSuperiorPlayer.of((Player) e.getEntity());
        Island island = plugin.getGrid().getIslandAt(e.getEntity().getLocation());

        if(island == null || (plugin.getSettings().spawnPvp && island instanceof SpawnIsland))
            return;

        SuperiorPlayer damagerPlayer;

        if(e.getDamager() instanceof Player){
            damagerPlayer = SSuperiorPlayer.of((Player) e.getDamager());
        }

        else if(e.getDamager() instanceof Projectile){
            ProjectileSource shooter = ((Projectile) e.getDamager()).getShooter();
            if(shooter instanceof Player)
                damagerPlayer = SSuperiorPlayer.of((Player) ((Projectile) e.getDamager()).getShooter());
            else return;
        }

        else return;

        if(damagerPlayer.equals(targetPlayer))
            return;

        e.setCancelled(true);

        //Disable flame
        if(e.getDamager() instanceof Arrow && targetPlayer.asPlayer().getFireTicks() > 0)
            targetPlayer.asPlayer().setFireTicks(0);

        Locale.HIT_PLAYER_IN_ISLAND.send(damagerPlayer);
    }

    @EventHandler
    public void onPoisonAttack(ProjectileHitEvent e){
        if(e.getEntityType().name().equals("SPLASH_POTION") || !(e.getEntity().getShooter() instanceof Player))
            return;

        SuperiorPlayer damagerPlayer = SSuperiorPlayer.of((Player) e.getEntity().getShooter());
        Island island = plugin.getGrid().getIslandAt(e.getEntity().getLocation());

        if(island == null || (plugin.getSettings().spawnPvp && island instanceof SpawnIsland))
            return;

        for(Entity entity : e.getEntity().getNearbyEntities(2, 2, 2)){
            if(entity instanceof Player){
                SuperiorPlayer targetPlayer = SSuperiorPlayer.of((Player) entity);

                if(damagerPlayer.equals(targetPlayer))
                    continue;

                targetPlayer.asPlayer().removePotionEffect(PotionEffectType.POISON);
            }
        }
    }

    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Painting || e.getEntity() instanceof ItemFrame || e.getEntity() instanceof Player)
            return;

        Player damager = null;

        if(e.getDamager() instanceof Player){
            damager = (Player) e.getDamager();
        }
        else if(e.getDamager() instanceof Projectile){
            Projectile projectile = (Projectile) e.getDamager();
            if(projectile.getShooter() instanceof Player)
                damager = (Player) projectile.getShooter();
        }

        if(damager == null)
            return;

        SuperiorPlayer damagerPlayer = SSuperiorPlayer.of(damager);
        Island island = plugin.getGrid().getIslandAt(e.getEntity().getLocation());

        IslandPermission islandPermission = e.getEntity() instanceof ArmorStand ? IslandPermission.BREAK : e.getEntity() instanceof Animals ? IslandPermission.ANIMAL_DAMAGE : IslandPermission.MONSTER_DAMAGE;

        if(island != null && !island.hasPermission(damagerPlayer, islandPermission)){
            e.setCancelled(true);
            Locale.sendProtectionMessage(damagerPlayer);
        }
    }

    @EventHandler
    public void onEntityPlace(PlayerInteractEvent e){
        if(e.getAction() != Action.RIGHT_CLICK_BLOCK || !e.hasItem())
            return;

        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        Island island = plugin.getGrid().getIslandAt(e.getClickedBlock().getLocation());

        EntityType spawnType = ItemUtils.getEntityType(e.getItem());

        if(spawnType == EntityType.UNKNOWN)
            return;

        IslandPermission islandPermission = e.getItem().getType() == Material.ARMOR_STAND ? IslandPermission.BUILD : Animals.class.isAssignableFrom(spawnType.getEntityClass()) ? IslandPermission.ANIMAL_SPAWN : IslandPermission.MONSTER_SPAWN;

        if(island != null && !island.hasPermission(superiorPlayer, islandPermission)){
            e.setCancelled(true);
            Locale.sendProtectionMessage(superiorPlayer);
        }
    }

    @EventHandler
    public void onVisitorDamage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player))
            return;

        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of((Player) e.getEntity());
        Island island = plugin.getGrid().getIslandAt(e.getEntity().getLocation());

        if(island != null && !island.isMember(superiorPlayer) && !plugin.getSettings().visitorsDamage)
            e.setCancelled(true);
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent e){
        if(e.getRightClicked() instanceof Painting || e.getRightClicked() instanceof ItemFrame)
            return;

        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        Island island = plugin.getGrid().getIslandAt(e.getRightClicked().getLocation());

        if(island != null && !island.hasPermission(superiorPlayer, e.getRightClicked() instanceof ArmorStand ? IslandPermission.INTERACT : IslandPermission.ANIMAL_BREED)){
            e.setCancelled(true);
            Locale.sendProtectionMessage(superiorPlayer);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAsyncChat(AsyncPlayerChatEvent e){
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        Island island = superiorPlayer.getIsland();

        if(superiorPlayer.hasTeamChatEnabled()){
            if (superiorPlayer.getIsland() == null)
                superiorPlayer.toggleTeamChat();
            else {
                e.setCancelled(true);
                island.sendMessage(Locale.TEAM_CHAT_FORMAT.getMessage(superiorPlayer.getPlayerRole(), superiorPlayer.getName(), e.getMessage()));
                Locale.SPY_TEAM_CHAT_FORMAT.send(Bukkit.getConsoleSender(), superiorPlayer.getPlayerRole(), superiorPlayer.getName(), e.getMessage());
                for(Player _onlinePlayer : Bukkit.getOnlinePlayers()){
                    SuperiorPlayer onlinePlayer = SSuperiorPlayer.of(_onlinePlayer);
                    if(onlinePlayer.hasAdminSpyEnabled())
                        Locale.SPY_TEAM_CHAT_FORMAT.send(onlinePlayer, superiorPlayer.getPlayerRole(), superiorPlayer.getName(), e.getMessage());
                }
                return;
            }
        }

        String islandNameFormat = Locale.NAME_CHAT_FORMAT.getMessage(island == null ? "" :
                plugin.getSettings().islandNamesColorSupport ? ChatColor.translateAlternateColorCodes('&', island.getName()) : island.getName());

        e.setFormat(e.getFormat()
                .replace("{island-level}", String.valueOf(island == null ? 0 : island.getIslandLevelAsBigDecimal()))
                .replace("{island-level-format}", String.valueOf(island == null ? 0 : StringUtils.fancyFormat(island.getIslandLevelAsBigDecimal())))
                .replace("{island-worth}", String.valueOf(island == null ? 0 : island.getWorthAsBigDecimal()))
                .replace("{island-worth-format}", String.valueOf(island == null ? 0 : StringUtils.fancyFormat(island.getWorthAsBigDecimal())))
                .replace("{island-name}", islandNameFormat == null ? "" : islandNameFormat)
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e){
        if(e.getItem() == null || e.getItem().getType() != Materials.GOLDEN_AXE.toBukkitType() ||
                !(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK))
            return;

        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());

        if(!superiorPlayer.hasSchematicModeEnabled())
            return;

        e.setCancelled(true);

        if(e.getAction().name().contains("RIGHT")){
            Locale.SCHEMATIC_RIGHT_SELECT.send(superiorPlayer, SBlockPosition.of(e.getClickedBlock().getLocation()));
            superiorPlayer.setSchematicPos1(e.getClickedBlock());
        }
        else{
            Locale.SCHEMATIC_LEFT_SELECT.send(superiorPlayer, SBlockPosition.of(e.getClickedBlock().getLocation()));
            superiorPlayer.setSchematicPos2(e.getClickedBlock());
        }

        if(superiorPlayer.getSchematicPos1() != null && superiorPlayer.getSchematicPos2() != null)
            Locale.SCHEMATIC_READY_TO_CREATE.send(superiorPlayer);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e){
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        Island island = plugin.getGrid().getIslandAt(superiorPlayer.getLocation());

        if(island != null && !island.hasPermission(superiorPlayer, IslandPermission.DROP_ITEMS)){
            e.setCancelled(true);
            Locale.sendProtectionMessage(superiorPlayer);
        }
    }

    @EventHandler
    public void onPlayerItemPickup(PlayerPickupItemEvent e){
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
        Island island = plugin.getGrid().getIslandAt(superiorPlayer.getLocation());

        if(island != null && !island.hasPermission(superiorPlayer, IslandPermission.PICKUP_DROPS)){
            e.setCancelled(true);
            Locale.sendProtectionMessage(superiorPlayer);
        }
    }

    private Set<UUID> noFallDamage = new HashSet<>();

    @EventHandler
    public void onPlayerFall(PlayerMoveEvent e){
        if(!plugin.getSettings().voidTeleport)
            return;

        if (!e.getPlayer().getWorld().equals(plugin.getGrid().getIslandsWorld()))
            return;

        Location from = e.getFrom(), to = e.getTo();

        if(from.getBlockY() == to.getBlockY() || to.getBlockY() > -5)
            return;

        Island island = plugin.getGrid().getIslandAt(e.getPlayer().getLocation());

        if(island == null)
            island = plugin.getGrid().getSpawnIsland();

        noFallDamage.add(e.getPlayer().getUniqueId());
        e.getPlayer().teleport(island.getTeleportLocation().add(0, 1, 0));
        Executor.sync(() -> noFallDamage.remove(e.getPlayer().getUniqueId()), 20L);
    }

    @EventHandler
    public void onPlayerFall(EntityDamageEvent e){
        if(e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL && noFallDamage.contains(e.getEntity().getUniqueId()))
            e.setCancelled(true);
    }

    class PlayerArrowPickup implements Listener{

        PlayerArrowPickup(){
            if(load())
                plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }

        boolean load(){
            try{
                Class.forName("org.bukkit.event.player.PlayerPickupArrowEvent");
                return true;
            }catch(ClassNotFoundException ex){
                return false;
            }
        }

        @EventHandler
        public void onPlayerArrowPickup(PlayerPickupArrowEvent e){
            SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(e.getPlayer());
            Island island = plugin.getGrid().getIslandAt(superiorPlayer.getLocation());

            if(island != null && !island.hasPermission(superiorPlayer, IslandPermission.PICKUP_DROPS)){
                e.setCancelled(true);
                Locale.sendProtectionMessage(superiorPlayer);
            }
        }

    }

}
