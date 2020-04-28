package de.erethon.dungeonsxl.sign.lobby;

import de.erethon.caliburn.item.VanillaItem;
import de.erethon.commons.misc.NumberUtil;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.sign.DSignType;
import de.erethon.dungeonsxl.sign.DSignTypeDefault;
import de.erethon.dungeonsxl.sign.LocationSign;
import de.erethon.dungeonsxl.world.DGameWorld;
import org.bukkit.block.Sign;

public class PlayerStartSign extends LocationSign {

    private int id;

    public PlayerStartSign(DungeonsXL plugin, Sign sign, String[] lines, DGameWorld gameWorld) {
        super(plugin, sign, lines, gameWorld);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public DSignType getType() {
        return DSignTypeDefault.PLAYERSTART;
    }

    @Override
    public void onInit() {
        super.onInit();
        id = NumberUtil.parseInt(lines[1]);
        getSign().getBlock().setType(VanillaItem.AIR.getMaterial());
    }
}
