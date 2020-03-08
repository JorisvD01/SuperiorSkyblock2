package com.bgsoftware.superiorskyblock.upgrades;

import com.bgsoftware.superiorskyblock.api.upgrades.Upgrade;
import com.bgsoftware.superiorskyblock.utils.key.KeyMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public final class SUpgrade implements Upgrade {

    private static SUpgradeLevel NULL_LEVEL = new SUpgradeLevel(0, 0, new ArrayList<>(), "",
            -1, -1, -1, -1, -1, -1, new KeyMap<>(), new KeyMap<>());

    private final String name;

    private SUpgradeLevel[] upgradeLevels = new SUpgradeLevel[0];
    private int slot = -1;

    public SUpgrade(String name){
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SUpgradeLevel getUpgradeLevel(int level) {
        return level <= 0 || level > upgradeLevels.length ? NULL_LEVEL : upgradeLevels[level - 1];
    }

    @Override
    public int getMaxUpgradeLevel() {
        return upgradeLevels.length;
    }

    public int getMenuSlot() {
        return slot;
    }

    public void setMenuSlot(int slot){
        this.slot = slot;
    }

    public void addUpgradeLevel(int level, SUpgradeLevel upgradeLevel){
        if(level > upgradeLevels.length)
            upgradeLevels = Arrays.copyOf(upgradeLevels, level);

        upgradeLevels[level - 1] = upgradeLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SUpgrade upgrade = (SUpgrade) o;
        return name.equals(upgrade.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}