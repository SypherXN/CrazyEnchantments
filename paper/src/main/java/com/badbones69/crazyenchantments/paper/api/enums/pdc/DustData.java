package com.badbones69.crazyenchantments.paper.api.enums.pdc;

public class DustData {

    private final String name;
    private final int min;
    private final int max;
    private final int chance;
    /**
     * Whether opening this dust rolls from 0 instead of 1. Only tinkered
     * book rewards set it; older dusts and admin-given dust keep the 1-based
     * roll they were created with.
     */
    private final boolean rollFromZero;

    public DustData(String dustConfigName, int min, int max, int chance) {
        this(dustConfigName, min, max, chance, false);
    }

    public DustData(String dustConfigName, int min, int max, int chance, boolean rollFromZero) {
        this.name = dustConfigName;
        this.min = min;
        this.max = max;
        this.chance = chance;
        this.rollFromZero = rollFromZero;
    }

    /**
     * The lowest potency this dust may roll when opened. Missing from old
     * serialized dust, so it defaults to the legacy 1.
     */
    public int getRollMinimum() {
        return this.rollFromZero ? 0 : 1;
    }

    public int getMax() {
        return this.max;
    }

    public int getMin() {
        return this.min;
    }

    public String getConfigName() {
        return this.name;
    }

    public int getChance() {
        return this.chance;
    }
}