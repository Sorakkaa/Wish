package com.wish.client.features.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class RealPlayerFilter {
    private static final int MAX_MINECRAFT_USERNAME_LENGTH = 16;

    private RealPlayerFilter() {
    }

    public static boolean isRealPlayer(Avatar entity, boolean isLocalPlayer) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (isLocalPlayer) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return false;
        }

        UUID uuid = player.getUUID();
        PlayerInfo info = connection.getPlayerInfo(uuid);
        String profileName = (info != null && info.getProfile() != null) ? info.getProfile().name() : null;
        return isValidRealPlayerIdentity(uuid, profileName, player.getScoreboardName(), info != null);
    }

    public static boolean isValidRealPlayerIdentity(UUID uuid, String profileName, String entityName, boolean hasLivePlayerInfo) {
        if (!hasLivePlayerInfo || uuid == null) {
            return false;
        }
        int version = uuid.version();
        if (version != 4 && version != 3) {
            return false;
        }
        if (!isValidMinecraftUsername(profileName)) {
            return false;
        }
        return entityName == null || isValidMinecraftUsername(entityName);
    }

    public static boolean isValidMinecraftUsername(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_MINECRAFT_USERNAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}
