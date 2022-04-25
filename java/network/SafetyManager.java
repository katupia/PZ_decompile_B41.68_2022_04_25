package zombie.network;

import java.util.Iterator;
import java.util.LinkedHashMap;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.logger.LoggerManager;
import zombie.debug.DebugLog;
import zombie.debug.LogSeverity;

public class SafetyManager {
   private static final LinkedHashMap<String, Float> playerCooldown = new LinkedHashMap();
   private static final LinkedHashMap<String, Boolean> playerSafety = new LinkedHashMap();

   public SafetyManager() {
   }

   public static void clear() {
      playerCooldown.clear();
      playerSafety.clear();
   }

   public static void clearSafety(IsoPlayer var0) {
      if (var0 != null) {
         LoggerManager.getLogger("pvp").write(String.format("user \"%s\" clear safety %b %f", var0.getUsername(), var0.isSafety(), var0.getSafetyCooldown()), "INFO");
         playerCooldown.remove(var0.getUsername());
         playerSafety.remove(var0.getUsername());
      } else if (Core.bDebug) {
         DebugLog.Multiplayer.debugln("ClearSafety: player not found");
      }

   }

   public static void storeSafety(IsoPlayer var0) {
      try {
         if (var0 != null && var0.isAlive()) {
            LoggerManager.getLogger("pvp").write(String.format("user \"%s\" store safety %b %f", var0.getUsername(), var0.isSafety(), var0.getSafetyCooldown()), "INFO");
            playerSafety.put(var0.getUsername(), var0.isSafety());
            playerCooldown.put(var0.getUsername(), var0.getSafetyCooldown());
            Iterator var1;
            if (playerCooldown.size() > ServerOptions.instance.MaxPlayers.getValue() * 1000) {
               var1 = playerCooldown.entrySet().iterator();
               if (var1.hasNext()) {
                  var1.next();
                  var1.remove();
               }
            }

            if (playerSafety.size() > ServerOptions.instance.MaxPlayers.getValue() * 1000) {
               var1 = playerSafety.entrySet().iterator();
               if (var1.hasNext()) {
                  var1.next();
                  var1.remove();
               }
            }
         } else if (Core.bDebug) {
            DebugLog.Multiplayer.debugln("StoreSafety: player not found");
         }
      } catch (Exception var2) {
         DebugLog.Multiplayer.printException(var2, "StoreSafety failed", LogSeverity.Error);
      }

   }

   public static void restoreSafety(IsoPlayer var0) {
      try {
         if (var0 != null) {
            if (playerSafety.containsKey(var0.getUsername())) {
               var0.setSafety((Boolean)playerSafety.remove(var0.getUsername()));
            }

            if (playerCooldown.containsKey(var0.getUsername())) {
               var0.setSafetyCooldown((Float)playerCooldown.remove(var0.getUsername()));
            }

            LoggerManager.getLogger("pvp").write(String.format("user \"%s\" restore safety %b %f", var0.getUsername(), var0.isSafety(), var0.getSafetyCooldown()), "INFO");
         } else if (Core.bDebug) {
            DebugLog.Multiplayer.debugln("RestoreSafety: player not found");
         }
      } catch (Exception var2) {
         DebugLog.Multiplayer.printException(var2, "RestoreSafety failed", LogSeverity.Error);
      }

   }
}
