package de.erethon.dungeonsxl.sign.passive;

import de.erethon.commons.misc.NumberUtil;
import de.erethon.dungeonsxl.api.DungeonsAPI;
import de.erethon.dungeonsxl.api.Trigger;
import de.erethon.dungeonsxl.api.sign.Passive;
import de.erethon.dungeonsxl.api.world.EditWorld;
import de.erethon.dungeonsxl.api.world.GameWorld;
import de.erethon.dungeonsxl.api.world.InstanceWorld;
import de.erethon.dungeonsxl.player.DPermission;
import de.erethon.dungeonsxl.sign.LocationSign;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.Set;

public class PlayerStartSign extends Passive implements LocationSign {

    private Location location;
    private int groupid = 0;
    private int spawn = 0;

    public PlayerStartSign(DungeonsAPI api, Sign sign, String[] lines, InstanceWorld instance) {
        super(api, sign, lines, instance);
    }

    public int getGroupid() {
        return groupid;
    }

    public int getSpawn() {
        return spawn;
    }

    public void setGroupId(int id) {
        groupid = id;
    }

    public void setSpawnId(int id) {
        spawn = id;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public String getName() {
        return "PlayerStart";
    }

    @Override
    public String getBuildPermission() {
        return DPermission.SIGN.getNode() + ".start";
    }

    @Override
    public boolean isOnDungeonInit() {
        return true;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isSetToAir() {
        return true;
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void initialize() {
        LocationSign.super.initialize();
        spawn = NumberUtil.parseInt(getLine(1));
        groupid = NumberUtil.parseInt(getLine(2), 0);
    }
}
