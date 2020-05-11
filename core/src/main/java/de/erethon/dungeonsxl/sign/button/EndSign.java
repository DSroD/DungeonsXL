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
package de.erethon.dungeonsxl.sign.button;

import de.erethon.dungeonsxl.api.DungeonsAPI;
import de.erethon.dungeonsxl.api.dungeon.Dungeon;
import de.erethon.dungeonsxl.api.sign.Button;
import de.erethon.dungeonsxl.api.world.InstanceWorld;
import de.erethon.dungeonsxl.api.world.ResourceWorld;
import de.erethon.dungeonsxl.config.DMessage;
import de.erethon.dungeonsxl.player.DGamePlayer;
import de.erethon.dungeonsxl.player.DPermission;
import de.erethon.dungeonsxl.trigger.InteractTrigger;
import de.erethon.dungeonsxl.world.DGameWorld;
import de.erethon.dungeonsxl.world.DResourceWorld;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 * @author Frank Baumann, Milan Albrecht, Daniel Saukel
 */
public class EndSign extends Button {

    private ResourceWorld floor;

    public EndSign(DungeonsAPI api, Sign sign, String[] lines, InstanceWorld instance) {
        super(api, sign, lines, instance);
    }

    public ResourceWorld getFloor() {
        return floor;
    }

    public void setFloor(ResourceWorld floor) {
        this.floor = floor;
    }

    @Override
    public String getName() {
        return "End";
    }

    @Override
    public String getBuildPermission() {
        return DPermission.SIGN.getNode() + ".end";
    }

    @Override
    public boolean isOnDungeonInit() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return true;
    }

    @Override
    public boolean isSetToAir() {
        return false;
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void initialize() {
        if (!getLine(1).isEmpty()) {
            floor = api.getMapRegistry().get(getLine(1));
        }

        if (!getTriggers().isEmpty()) {
            setToAir();
            return;
        }

        InteractTrigger trigger = InteractTrigger.getOrCreate(0, getSign().getBlock(), (DGameWorld) getGameWorld());
        if (trigger != null) {
            trigger.addListener(this);
            addTrigger(trigger);
        }

        getSign().setLine(0, ChatColor.DARK_BLUE + "############");
        Dungeon dungeon = getGame().getDungeon();
        if (dungeon.isMultiFloor() && !getGame().getUnplayedFloors().isEmpty() && getGameWorld().getResource() != dungeon.getEndFloor()) {
            getSign().setLine(1, DMessage.SIGN_FLOOR_1.getMessage());
            if (floor == null) {
                getSign().setLine(2, DMessage.SIGN_FLOOR_2.getMessage());
            } else {
                getSign().setLine(2, ChatColor.GREEN + floor.getName().replace("_", " "));
            }
        } else {
            getSign().setLine(1, DMessage.SIGN_END.getMessage());
        }
        getSign().setLine(3, ChatColor.DARK_BLUE + "############");
        getSign().update();
    }

    @Override
    public boolean push(Player player) {
        DGamePlayer dPlayer = (DGamePlayer) api.getPlayerCache().getGamePlayer(player);
        if (dPlayer == null) {
            return true;
        }

        if (dPlayer.isFinished()) {
            return true;
        }

        dPlayer.finishFloor((DResourceWorld) floor);
        return true;
    }

}