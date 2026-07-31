package com.hibiscusmc.hmccosmetics.packets;

final class CreativeInventoryPolicy {

    private CreativeInventoryPolicy() {
    }

    static Action evaluate(boolean creativeMode, boolean virtualTarget, boolean virtualPayload) {
        if (!creativeMode) return Action.PASS;
        return virtualTarget || virtualPayload ? Action.CANCEL_AND_EDIT : Action.PASS;
    }

    enum Action {
        PASS,
        CANCEL_AND_EDIT
    }
}
