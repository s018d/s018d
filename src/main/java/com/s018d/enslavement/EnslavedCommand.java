package com.s018d.enslavement;

import net.minecraft.network.chat.Component;

/**
 * Every order a tamed/enslaved creature can receive.
 * Orders are chosen from the button menu, never typed.
 */
public enum EnslavedCommand {
    FOLLOW("gui.s018d.ens_follow"),
    STAY("gui.s018d.ens_stay"),
    COME("gui.s018d.ens_come"),
    PROTECT("gui.s018d.ens_protect"),
    GUARD("gui.s018d.ens_guard"),
    GUARD_RANGED("gui.s018d.ens_guard_ranged"),
    GUARD_POST("gui.s018d.ens_guard_post"),
    PATROL("gui.s018d.ens_patrol"),
    RETURN_HOME("gui.s018d.ens_return_home"),
    STOP("gui.s018d.ens_stop"),
    GATHER_WOOD("gui.s018d.ens_gather_wood"),
    MINE("gui.s018d.ens_mine"),
    DIG("gui.s018d.ens_dig"),
    HARVEST("gui.s018d.ens_harvest"),
    PLANT("gui.s018d.ens_plant"),
    TILL("gui.s018d.ens_till"),
    FERTILIZE("gui.s018d.ens_fertilize"),
    FISH("gui.s018d.ens_fish"),
    SHEAR("gui.s018d.ens_shear"),
    GROW("gui.s018d.ens_grow"),
    COLLECT("gui.s018d.ens_collect"),
    BREED("gui.s018d.ens_breed_job"),
    TORCH("gui.s018d.ens_torch"),
    SORT_CHESTS("gui.s018d.ens_sort_chests"),
    DEPOSIT("gui.s018d.ens_deposit"),
    HEAL("gui.s018d.ens_heal"),
    ATTACK("gui.s018d.ens_attack"),
    HUNT("gui.s018d.ens_hunt"),
    SHOOT("gui.s018d.ens_shoot"),
    EXPLODE("gui.s018d.ens_explode"),
    SIT("gui.s018d.ens_sit"),
    DANCE("gui.s018d.ens_dance"),
    BUTLER("gui.s018d.ens_butler"),
    CRAFT("gui.s018d.ens_craft"),
    FETCH("gui.s018d.ens_fetch"),
    RELEASE("gui.s018d.ens_release");

    private final String translationKey;

    EnslavedCommand(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public Component label() {
        return Component.translatable(translationKey);
    }

    public static EnslavedCommand byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return FOLLOW;
        }
    }
}
