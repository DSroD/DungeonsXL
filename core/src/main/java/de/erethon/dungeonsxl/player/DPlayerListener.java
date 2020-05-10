/*
 * Copyright (C) 2012-2020 Frank Baumann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.erethon.dungeonsxl.player;

import de.erethon.caliburn.category.Category;
import de.erethon.caliburn.item.ExItem;
import de.erethon.caliburn.item.VanillaItem;
import de.erethon.commons.chat.MessageUtil;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.api.dungeon.Game;
import de.erethon.dungeonsxl.api.dungeon.GameRule;
import de.erethon.dungeonsxl.api.player.EditPlayer;
import de.erethon.dungeonsxl.api.player.GamePlayer;
import de.erethon.dungeonsxl.api.player.GlobalPlayer;
import de.erethon.dungeonsxl.api.player.InstancePlayer;
import de.erethon.dungeonsxl.api.player.PlayerCache;
import de.erethon.dungeonsxl.api.player.PlayerGroup;
import de.erethon.dungeonsxl.api.world.EditWorld;
import de.erethon.dungeonsxl.api.world.GameWorld;
import de.erethon.dungeonsxl.config.DMessage;
import de.erethon.dungeonsxl.config.MainConfig;
import de.erethon.dungeonsxl.dungeon.DGame;
import de.erethon.dungeonsxl.util.ParsingUtil;
import de.erethon.dungeonsxl.world.DGameWorld;
import de.erethon.dungeonsxl.world.block.LockedDoor;
import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * @author Daniel Saukel, Frank Baumann, Milan Albrecht
 */
public class DPlayerListener implements Listener {

    private DungeonsXL plugin;
    private MainConfig config;
    private PlayerCache dPlayers;

    public static final String ALL = "@all ";

    public DPlayerListener(DungeonsXL plugin) {
        this.plugin = plugin;
        config = plugin.getMainConfig();
        dPlayers = plugin.getPlayerCache();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        World world = event.getEntity().getWorld();
        GameWorld gameWorld = plugin.getGameWorld(world);

        if (gameWorld == null) {
            return;
        }

        // Deny all Damage in Lobby
        if (!gameWorld.isPlaying()) {
            event.setCancelled(true);
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        boolean dead = ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage() <= 0;
        if (!dead) {
            return;
        }
        if (event.getEntity() instanceof Player && !gameWorld.getDungeon().getRules().getState(GameRule.DEATH_SCREEN)) {
            GamePlayer gamePlayer = plugin.getPlayerCache().getGamePlayer((Player) event.getEntity());
            if (gamePlayer == null) {
                return;
            }
            ((DGamePlayer) gamePlayer).onDeath(null);
        }

        if (plugin.getDungeonMob((LivingEntity) event.getEntity()) != null) {
            String killer = null;

            if (event instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) event).getDamager();

                if (damager instanceof Projectile) {
                    if (((Projectile) damager).getShooter() instanceof Player) {
                        damager = (Player) ((Projectile) damager).getShooter();
                    }
                }

                if (damager instanceof Player) {
                    killer = damager.getName();
                }
            }

            ((DGame) gameWorld.getGame()).addKill(killer);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        World world = event.getEntity().getWorld();
        GameWorld gameWorld = plugin.getGameWorld(world);

        if (gameWorld == null) {
            return;
        }

        Game game = gameWorld.getGame();

        if (game == null) {
            return;
        }

        if (!game.hasStarted()) {
            return;
        }

        boolean pvp = game.getRules().getState(GameRule.PLAYER_VERSUS_PLAYER);
        boolean friendlyFire = game.getRules().getState(GameRule.FRIENDLY_FIRE);

        Entity attackerEntity = event.getDamager();
        Entity attackedEntity = event.getEntity();

        if (attackerEntity instanceof Projectile) {
            ProjectileSource source = ((Projectile) attackerEntity).getShooter();
            if (source instanceof Entity) {
                attackerEntity = (Entity) source;
            }
        }

        Player attackerPlayer = null;
        Player attackedPlayer = null;

        PlayerGroup attackerGroup = null;
        PlayerGroup attackedGroup = null;

        if (!(attackerEntity instanceof LivingEntity) || !(attackedEntity instanceof LivingEntity)) {
            return;
        }

        if (attackerEntity instanceof Player && attackedEntity instanceof Player) {
            attackerPlayer = (Player) attackerEntity;
            attackedPlayer = (Player) attackedEntity;
            if (attackedPlayer.hasMetadata("NPC") || attackerPlayer.hasMetadata("NPC")) {
                return;
            }

            attackerGroup = plugin.getPlayerGroup(attackerPlayer);
            attackedGroup = plugin.getPlayerGroup(attackedPlayer);

            if (!pvp) {
                event.setCancelled(true);
                return;
            }

            if (attackerGroup != null && attackedGroup != null) {
                if (!friendlyFire && attackerGroup.equals(attackedGroup)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Check Dogs
        if (attackerEntity instanceof Player || attackedEntity instanceof Player) {
            for (GamePlayer player : dPlayers.getAllGamePlayersIf(p -> p.getGameWorld() == gameWorld)) {
                if (player.getWolf() != null) {
                    if (attackerEntity == player.getWolf() || attackedEntity == player.getWolf()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        for (GamePlayer dPlayer : dPlayers.getAllGamePlayersIf(p -> p.getGameWorld() == gameWorld)) {
            if (dPlayer.getWolf() != null) {
                if (attackerEntity instanceof Player || attackedEntity instanceof Player) {
                    if (attackerEntity == dPlayer.getWolf() || attackedEntity == dPlayer.getWolf()) {
                        event.setCancelled(true);
                        return;
                    }

                } else if (attackerEntity == dPlayer.getWolf() || attackedEntity == dPlayer.getWolf()) {
                    event.setCancelled(false);
                    return;
                }
            }
        }
    }

    // Players don't need to eat in lobbies
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        GameWorld gameWorld = plugin.getGameWorld(event.getEntity().getWorld());
        if (gameWorld != null) {
            if (!gameWorld.isPlaying() || !gameWorld.getDungeon().getRules().getState(GameRule.FOOD_LEVEL)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        GlobalPlayer dPlayer = dPlayers.get(player);
        if (dPlayer == null) {
            return;
        }
        if (!dPlayer.isInGroupChat()) {
            return;
        }

        if (dPlayer instanceof DEditPlayer) {
            event.setCancelled(true);
            ((DInstancePlayer) dPlayer).chat(event.getMessage());
            return;
        }

        PlayerGroup group = plugin.getPlayerGroup(player);
        if (group == null) {
            return;
        }

        boolean game = event.getMessage().startsWith(ALL) && dPlayer instanceof DInstancePlayer;
        event.setCancelled(true);
        if (game) {
            ((DInstancePlayer) dPlayer).chat(event.getMessage().substring(ALL.length()));
        } else {
            group.sendMessage(ParsingUtil.replaceChatPlaceholders(config.getChatFormatGroup(), dPlayer) + event.getMessage());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }

        if (DPermission.hasPermission(player, DPermission.BYPASS)) {
            return;
        }

        if (!(dPlayers.get(player) instanceof DInstancePlayer)) {
            return;
        }
        InstancePlayer dPlayer = dPlayers.getInstancePlayer(player);

        String command = event.getMessage().toLowerCase();
        ArrayList<String> commandWhitelist = new ArrayList<>();

        if (dPlayer instanceof DEditPlayer) {
            if (DPermission.hasPermission(player, DPermission.CMD_EDIT)) {
                return;

            } else {
                commandWhitelist.addAll(config.getEditCommandWhitelist());
            }

        } else {
            commandWhitelist.addAll(dPlayer.getGroup().getDungeon().getRules().getState(GameRule.GAME_COMMAND_WHITELIST));
        }

        commandWhitelist.add("dungeonsxl");
        commandWhitelist.add("dungeon");
        commandWhitelist.add("dxl");

        event.setCancelled(true);

        for (String whitelistEntry : commandWhitelist) {
            if (command.equals('/' + whitelistEntry.toLowerCase()) || command.startsWith('/' + whitelistEntry.toLowerCase() + ' ')) {
                event.setCancelled(false);
            }
        }

        if (event.isCancelled()) {
            MessageUtil.sendMessage(player, DMessage.ERROR_CMD.getMessage());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isCitizensNPC(player)) {
            return;
        }
        DGamePlayer dPlayer = (DGamePlayer) dPlayers.getGamePlayer(player);
        if (dPlayer == null) {
            return;
        }
        dPlayer.onDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }

        GlobalPlayer dPlayer = dPlayers.get(player);
        if (dPlayer == null) {
            return;
        }

        if (dPlayer instanceof EditPlayer && !config.getDropItems() && !DPermission.hasPermission(player, DPermission.INSECURE)) {
            event.setCancelled(true);
        }

        if (!(dPlayer instanceof DGamePlayer)) {
            return;
        }

        DGamePlayer gamePlayer = (DGamePlayer) dPlayer;

        DGroup dGroup = gamePlayer.getGroup();
        if (dGroup == null) {
            return;
        }

        if (!dGroup.isPlaying()) {
            event.setCancelled(true);
            return;
        }

        if (!gamePlayer.isReady()) {
            event.setCancelled(true);
            return;
        }

        for (ExItem item : dGroup.getDungeon().getRules().getState(GameRule.SECURE_OBJECTS)) {
            if (event.getItemDrop().getItemStack().isSimilar(item.toItemStack())) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, DMessage.ERROR_DROP.getMessage());
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.checkPlayer(player)) {
            return;
        }

        DGlobalPlayer dPlayer = new DGlobalPlayer(plugin, player);
        if (dPlayer.getData().wasInGame()) {
            dPlayer.reset(dPlayer.getData().getOldLocation() != null ? dPlayer.getData().getOldLocation() : Bukkit.getWorlds().get(0).getSpawnLocation(),
                    dPlayer.getData().getKeepInventoryAfterLogout());
        }

        if (!dPlayer.getData().hasFinishedTutorial() && config.isTutorialActivated()) {
            if (plugin.getInstanceCache().size() < config.getMaxInstances()) {
                dPlayer.startTutorial();
            } else {
                event.getPlayer().kickPlayer(DMessage.ERROR_TOO_MANY_TUTORIALS.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DGameWorld gameWorld = (DGameWorld) plugin.getGameWorld(player.getWorld());
        DGamePlayer gamePlayer = (DGamePlayer) dPlayers.getGamePlayer(player);
        if (gameWorld != null && gamePlayer != null) {
            if (gamePlayer.getDGroupTag() != null) {
                gamePlayer.getDGroupTag().update();
            }
            if (gamePlayer.isStealingFlag()) {
                DGroup group = gamePlayer.getGroup();
                Location startLocation = gameWorld.getStartLocation(group);

                if (startLocation.distance(player.getLocation()) < 3) {
                    gamePlayer.captureFlag();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GlobalPlayer dPlayer = dPlayers.get(player);
        PlayerGroup dGroup = dPlayer.getGroup();

        if (!(dPlayer instanceof InstancePlayer)) {
            if (dGroup != null) {
                dGroup.removeMember(player);
            }

        } else if (dPlayer instanceof GamePlayer) {
            int timeUntilKickOfflinePlayer = dGroup.getDungeon().getRules().getState(GameRule.TIME_UNTIL_KICK_OFFLINE_PLAYER);

            if (timeUntilKickOfflinePlayer == 0) {
                ((InstancePlayer) dPlayer).leave();

            } else if (timeUntilKickOfflinePlayer > 0) {
                dGroup.sendMessage(DMessage.PLAYER_OFFLINE.getMessage(dPlayer.getName(), String.valueOf(timeUntilKickOfflinePlayer)), player);
                ((GamePlayer) dPlayer).setOfflineTimeMillis(System.currentTimeMillis() + timeUntilKickOfflinePlayer * 1000);

            } else {
                dGroup.sendMessage(DMessage.PLAYER_OFFLINE_NEVER.getMessage(dPlayer.getName()), player);
            }

        } else if (dPlayer instanceof InstancePlayer) {
            ((InstancePlayer) dPlayer).leave();
        }

        dPlayers.remove(dPlayer);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }

        GlobalPlayer dPlayer = dPlayers.get(player);
        if (dPlayer == null) {
            return;
        }

        if (dPlayer instanceof EditPlayer) {
            EditWorld editWorld = ((EditPlayer) dPlayer).getEditWorld();
            if (editWorld == null) {
                return;
            }

            if (editWorld.getLobbyLocation() == null) {
                event.setRespawnLocation(editWorld.getWorld().getSpawnLocation());

            } else {
                event.setRespawnLocation(editWorld.getLobbyLocation());
            }

        } else if (dPlayer instanceof GamePlayer) {
            GamePlayer gamePlayer = (GamePlayer) dPlayer;

            GameWorld gameWorld = gamePlayer.getGameWorld();
            if (gameWorld == null) {
                return;
            }

            PlayerGroup group = dPlayer.getGroup();

            Location respawn = gamePlayer.getLastCheckpoint();

            if (respawn == null) {
                respawn = group.getGameWorld().getStartLocation(group);
            }

            // Because some plugins set another respawn point, DXL teleports a few ticks later.
            new RespawnTask(player, respawn).runTaskLater(plugin, 10L);

            // Don't forget Doge!
            if (gamePlayer.getWolf() != null) {
                gamePlayer.getWolf().teleport(respawn);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        GlobalPlayer dPlayer = dPlayers.get(player);

        World toWorld = event.getTo().getWorld();

        if (dPlayer instanceof DInstancePlayer && ((DInstancePlayer) dPlayer).getWorld() == toWorld) {
            return;
        }

        if (plugin.getInstanceWorld(toWorld) != null) {
            dPlayer.sendMessage(DMessage.ERROR_JOIN_GROUP.getMessage());
            dPlayer.sendMessage(ChatColor.GOLD + DMessage.CMD_ENTER_HELP.getMessage());
            event.setCancelled(true);
        }
    }

    public static boolean isCitizensNPC(LivingEntity entity) {
        return entity.hasMetadata("NPC");
    }

    /* SUBJECT TO CHANGE */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        DGameWorld gameWorld = (DGameWorld) plugin.getGameWorld(player.getWorld());
        if (clickedBlock != null) {
            // Block Enderchests
            if (gameWorld != null || plugin.getEditWorld(player.getWorld()) != null) {
                if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                    if (VanillaItem.ENDER_CHEST.is(clickedBlock)) {
                        if (!DPermission.hasPermission(player, DPermission.BYPASS) && !DPermission.hasPermission(player, DPermission.ENDER_CHEST)) {
                            MessageUtil.sendMessage(player, DMessage.ERROR_ENDERCHEST.getMessage());
                            event.setCancelled(true);
                        }

                    } else if (Category.BEDS.containsBlock(clickedBlock)) {
                        if (!DPermission.hasPermission(player, DPermission.BYPASS) && !DPermission.hasPermission(player, DPermission.BED)) {
                            MessageUtil.sendMessage(player, DMessage.ERROR_BED.getMessage());
                            event.setCancelled(true);
                        }
                    }
                }
            }

            // Block Dispensers
            if (gameWorld != null) {
                if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                    if (VanillaItem.DISPENSER.is(clickedBlock)) {
                        if (!DPermission.hasPermission(player, DPermission.BYPASS) && !DPermission.hasPermission(player, DPermission.DISPENSER)) {
                            MessageUtil.sendMessage(player, DMessage.ERROR_DISPENSER.getMessage());
                            event.setCancelled(true);
                        }
                    }
                }

                for (LockedDoor door : gameWorld.getLockedDoors()) {
                    if (clickedBlock.equals(door.getBlock()) || clickedBlock.equals(door.getAttachedBlock())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Check Portals
        if (event.getItem() != null) {
            ItemStack item = event.getItem();

            // Copy/Paste a Sign and Block-info
            if (plugin.getEditWorld(player.getWorld()) != null) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (VanillaItem.STICK.is(item)) {
                        DEditPlayer editPlayer = (DEditPlayer) dPlayers.getEditPlayer(player);
                        if (editPlayer != null) {
                            editPlayer.poke(clickedBlock);
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

}
