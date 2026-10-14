package com.wish.client.features;

import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import com.wish.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class FunChatCommands {

    private static final Random RANDOM = new Random();
    private static long lastCommandTime = 0;

    private FunChatCommands() {}

    public static void checkFunCommands(String lower, String text) {
        if (lower == null || text == null) return;
        if (lower.contains("!meow") && ModConfig.INSTANCE.enableMeow) processFunCommand(text, "!meow");
        else if (lower.contains("!wanted") && ModConfig.INSTANCE.enableWanted) processFunCommand(text, "!wanted");
        else if (lower.contains("!kiss") && ModConfig.INSTANCE.enableKiss) processFunCommand(text, "!kiss");
        else if (lower.contains("!feed") && ModConfig.INSTANCE.enableFeed) processFunCommand(text, "!feed");
        else if (lower.contains("!poke") && ModConfig.INSTANCE.enablePoke) processFunCommand(text, "!poke");
        else if (lower.contains("!pat") && ModConfig.INSTANCE.enablePat) processFunCommand(text, "!pat");
        else if (lower.contains("!hug") && ModConfig.INSTANCE.enableHug) processFunCommand(text, "!hug");
        else if (lower.contains("!sus") && ModConfig.INSTANCE.enableSus) processFunCommand(text, "!sus");
        else if (lower.contains("!rizz") && ModConfig.INSTANCE.enableRizz) processFunCommand(text, "!rizz");
        else if (lower.contains("!jerry") && ModConfig.INSTANCE.enableJerry) processFunCommand(text, "!jerry");
        else if (lower.contains("!soraka") && ModConfig.INSTANCE.enableSoraka) processFunCommand(text, "!soraka");
        else if (lower.contains("!iq") && ModConfig.INSTANCE.enableIq) processFunCommand(text, "!iq");
        else if (lower.contains("!sleep") && ModConfig.INSTANCE.enableSleep) processFunCommand(text, "!sleep");
        else if (lower.contains("!yuri") && ModConfig.INSTANCE.enableYuri) processFunCommand(text, "!yuri");
    }

    private static void processFunCommand(String text, String command) {
        long now = System.currentTimeMillis();
        if (now - lastCommandTime < 1500) return; 
        lastCommandTime = now;

        String lower = text.replace('\u00A0', ' ').toLowerCase().trim();
        String explicitTarget = null;
        int cmdIdx = text.toLowerCase().indexOf(command);
        if (cmdIdx != -1 && text.length() > cmdIdx + command.length()) {
            String afterCmd = text.substring(cmdIdx + command.length()).replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").trim();
            if (!afterCmd.isEmpty()) {
                String[] words = afterCmd.split("\\s+");
                String arg = words[0];
                if (arg.matches("^[a-zA-Z0-9_]{2,16}$")) {
                    explicitTarget = arg;
                    try {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null && mc.player.connection != null) {
                            for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                                if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                                    String pName = info.getProfile().name();
                                    if (pName.toLowerCase().startsWith(arg.toLowerCase())) {
                                        explicitTarget = pName;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        String senderName = ChatUtils.extractNameFromChat(text);
        Minecraft mc = Minecraft.getInstance();
        String myName = mc.player != null ? mc.player.getScoreboardName() : "Me";

        String target = explicitTarget != null ? explicitTarget : (senderName != null ? senderName : myName);
        String actor = senderName != null ? senderName : myName;
        boolean isSelfTarget = actor.equalsIgnoreCase(target);

        String msg = "";
        switch (command) {
            case "!meow" -> {
                int pct = RANDOM.nextInt(101);
                msg = target + " is " + pct + "% cat... " + (pct > 80 ? "nyaa :3" : (pct < 20 ? "closet barker." : "mrp?"));
            }
            case "!wanted" -> msg = "WANTED: " + target + " for ninjaing Necron's Handle (Bounty: 1 coin and a stick).";
            case "!kiss" -> msg = isSelfTarget ? actor + " kissed their reflection in the mirror... Narcissist." : actor + " gave " + target + " a little kiss <3";
            case "!feed" -> msg = isSelfTarget ? actor + " ate a pack of cookies in secret." : actor + " fed " + target + " a cookie. Eat and be quiet.";
            case "!poke" -> msg = isSelfTarget ? actor + " pinched themself. Yeah, you're awake." : actor + " poked " + target + "! Hey, wake up!";
            case "!pat" -> msg = isSelfTarget ? actor + " patted their own head. Good job champ." : actor + " gently patted " + target + "'s head. Good boy/girl.";
            case "!hug" -> msg = isSelfTarget ? actor + " is hugging a body pillow while crying." : actor + " gave " + target + " a big warm hug!";
            case "!sus" -> {
                int pct = RANDOM.nextInt(101);
                msg = target + " is " + pct + "% sus " + (pct > 75 ? "(vote them out right now)" : (pct < 25 ? "(clean)" : "(kinda iffy..)"));
            }
            case "!rizz" -> {
                int rizz = RANDOM.nextInt(101);
                msg = target + " has " + rizz + "% rizz " + (rizz > 85 ? "(W rizz)" : (rizz < 25 ? "(Negative rizz, stop talking)" : ""));
            }
            case "!jerry" -> {
                SorakaModeManager.deactivate();
                JerryModeManager.activate();
                msg = "[Jerry] Jerry has taken full control.";
            }
            case "!soraka" -> {
                JerryModeManager.deactivate();
                SorakaModeManager.activate();
                msg = "[Soraka] Yes, that was a banana. Nobody expects the banana.";
            }
            case "!iq" -> {
                int iq = RANDOM.nextInt(180) + 1;
                msg = target + "'s IQ: " + iq + (iq < 50 ? " (can't find the W key)" : (iq > 140 ? " (galaxy brain)" : " (average Skyblock player)"));
            }
            case "!sleep" -> msg = actor + ": 'Wake up, my bed exploded.'";
            case "!yuri" -> {
                String p1 = actor;
                String p2 = target;
                boolean hasTwo = false;
                if (cmdIdx != -1 && text.length() > cmdIdx + command.length()) {
                    String afterCmd = text.substring(cmdIdx + command.length()).replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").trim();
                    if (!afterCmd.isEmpty()) {
                        String[] words = afterCmd.split("\\s+");
                        if (words.length >= 2) {
                            hasTwo = true;
                            p1 = words[0];
                            p2 = words[1];
                        }
                    }
                }
                if (hasTwo) {
                    msg = p1 + " & " + p2 + ": " + RANDOM.nextInt(101) + "% yuri!";
                } else {
                    msg = target + ": " + RANDOM.nextInt(101) + "% yuri.";
                }
            }
            default -> {}
        }

        String prefix = ChatUtils.getChatPrefix(lower);
        final String finalMsg = msg;
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(300);
            } catch (Exception ignored) {}
            if (prefix.equals("/pc ") && ModConfig.INSTANCE.enablePc) {
                WishClient.sendServerChatMessage("/pc ", finalMsg);
            } else if (prefix.equals("/gc ") && ModConfig.INSTANCE.enableGc) {
                WishClient.sendServerChatMessage("/gc ", finalMsg);
            } else if (prefix.equals("/ac ") && ModConfig.INSTANCE.enableAc) {
                WishClient.sendServerChatMessage("/ac ", finalMsg);
            }
        });
    }
}
