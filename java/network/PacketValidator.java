package zombie.network;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import zombie.characters.Faction;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.commands.PlayerType;
import zombie.core.Core;
import zombie.core.logger.LoggerManager;
import zombie.core.raknet.UdpConnection;
import zombie.core.znet.SteamUtils;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoUtils;
import zombie.iso.areas.NonPvpZone;
import zombie.network.packets.hit.Character;
import zombie.network.packets.hit.Hit;
import zombie.network.packets.hit.IMovable;
import zombie.network.packets.hit.IPositional;
import zombie.network.packets.hit.Player;
import zombie.network.packets.hit.Zombie;
import zombie.util.StringUtils;
import zombie.util.Type;

public class PacketValidator {
   private static final long USER_LOG_INTERVAL_MS = 300000L;
   private static final int MAX_TYPE_3 = 10;
   private static final int MAX_TYPE_4 = 101;

   public PacketValidator() {
   }

   public static boolean checkType1(UdpConnection var0, Character var1, Character var2, String var3) {
      boolean var4 = checkPVP(var1.getCharacter(), var2.getCharacter());
      if (!var4 && ServerOptions.instance.AntiCheatProtectionType1.getValue() && doKickUser(var0, var3, "UI_ValidationFailed_Type1")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\"", var0.username, "UI_ValidationFailed_Type1", var3));
      }

      return var4;
   }

   public static boolean checkType2(UdpConnection var0, IMovable var1, String var2) {
      float var3 = var1.getSpeed();
      double var4 = var1.isVehicle() ? ServerOptions.instance.SpeedLimit.getValue() : 10.0D;
      boolean var6 = (double)var3 <= var4 * ServerOptions.instance.AntiCheatProtectionType2ThresholdMultiplier.getValue();
      if (!var6 && ServerOptions.instance.AntiCheatProtectionType2.getValue() && doKickUser(var0, var2, "UI_ValidationFailed_Type2")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%f\"", var0.username, "UI_ValidationFailed_Type2", var2, var3));
      }

      return var6;
   }

   public static boolean checkType3(UdpConnection var0, IPositional var1, IPositional var2, String var3) {
      float var4 = IsoUtils.DistanceTo(var2.getX(), var2.getY(), var1.getX(), var1.getY());
      boolean var5 = (double)var4 <= (double)(var0.ReleventRange * 10) * ServerOptions.instance.AntiCheatProtectionType3ThresholdMultiplier.getValue();
      if (!var5 && ServerOptions.instance.AntiCheatProtectionType3.getValue() && doKickUser(var0, var3, "UI_ValidationFailed_Type3")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%f\"", var0.username, "UI_ValidationFailed_Type3", var3, var4));
      }

      return var5;
   }

   public static boolean checkType4(UdpConnection var0, Hit var1, String var2) {
      float var3 = var1.getDamage();
      boolean var4 = (double)var3 <= 101.0D * ServerOptions.instance.AntiCheatProtectionType4ThresholdMultiplier.getValue();
      if (!var4 && ServerOptions.instance.AntiCheatProtectionType4.getValue() && doKickUser(var0, var2, "UI_ValidationFailed_Type4")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%f\"", var0.username, "UI_ValidationFailed_Type4", var2, var3));
      }

      return var4;
   }

   public static boolean checkType5(UdpConnection var0, Zombie var1, String var2) {
      UdpConnection var3 = ((IsoZombie)var1.getCharacter()).authOwner;
      boolean var4 = var3 == var0;
      if (!var4 && ServerOptions.instance.AntiCheatProtectionType5.getValue() && doKickUser(var0, var2, "UI_ValidationFailed_Type5")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%s\"", var0.username, "UI_ValidationFailed_Type5", var2, Optional.ofNullable(var3).map((var0x) -> {
            return var0x.username;
         }).orElse("")));
      }

      return var4;
   }

   public static boolean checkType6(UdpConnection var0, Player var1, String var2) {
      IsoPlayer var3 = var1.getPlayer();
      boolean var4 = Arrays.stream(var0.players).anyMatch((var1x) -> {
         return var1x.getOnlineID() == var3.getOnlineID();
      });
      if (!var4 && ServerOptions.instance.AntiCheatProtectionType6.getValue() && doKickUser(var0, var2, "UI_ValidationFailed_Type6")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%s\"", var0.username, "UI_ValidationFailed_Type6", var2, var3.getUsername()));
      }

      return var4;
   }

   public static boolean checkType7(UdpConnection var0, String var1, String var2) {
      boolean var3 = StringUtils.isNullOrEmpty(var1) || var1.equals(var0.username) || var0.accessLevel >= 16;
      if (!var3 && ServerOptions.instance.AntiCheatProtectionType7.getValue() && doKickUser(var0, var2, "UI_ValidationFailed_Type7")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%s\"", var0.username, "UI_ValidationFailed_Type7", var2, var1));
      }

      return var3;
   }

   public static boolean checkType8(UdpConnection var0, IPositional var1, IPositional var2, String var3) {
      float var4 = IsoUtils.DistanceTo(var2.getX(), var2.getY(), var1.getX(), var1.getY());
      boolean var5 = (double)var4 <= 10.0D * ServerOptions.instance.AntiCheatProtectionType3ThresholdMultiplier.getValue();
      if (!var5 && ServerOptions.instance.AntiCheatProtectionType3.getValue() && doKickUser(var0, var3, "UI_ValidationFailed_Type3")) {
         LoggerManager.getLogger("kick").write(String.format("Kick: player=\"%s\" type=\"%s\" issuer=\"%s\" value=\"%f\"", var0.username, "UI_ValidationFailed_Type3", var3, var4));
      }

      return var5;
   }

   private static boolean isUntouchable(UdpConnection var0) {
      return Core.bDebug || !var0.isFullyConnected() || PlayerType.isPrivileged(var0.accessLevel) || Arrays.stream(var0.players).filter(Objects::nonNull).anyMatch(IsoGameCharacter::isGodMod);
   }

   public static boolean doLogUser(UdpConnection var0, String var1, String var2) {
      if (!isUntouchable(var0) && doAntiCheatProtection()) {
         long var3 = System.currentTimeMillis();
         if (var3 > var0.lastUnauthorizedPacket) {
            var0.lastUnauthorizedPacket = var3 + 300000L;
            ServerWorldDatabase.instance.addUserlog(var0.username, Userlog.UserlogType.UnauthorizedPacket, var2, var1, 1);
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean doKickUser(UdpConnection var0, String var1, String var2) {
      if (!isUntouchable(var0) && doAntiCheatProtection()) {
         ServerWorldDatabase.instance.addUserlog(var0.username, Userlog.UserlogType.Kicked, var2, var1, 1);
         GameServer.kick(var0, "UI_Policy_Kick", var2);
         var0.forceDisconnect(var1);
         GameServer.addDisconnect(var0);
         return true;
      } else {
         return false;
      }
   }

   public static boolean doBanUser(UdpConnection var0, String var1, String var2) throws Exception {
      if (!isUntouchable(var0) && doAntiCheatProtection()) {
         ServerWorldDatabase.instance.addUserlog(var0.username, Userlog.UserlogType.Banned, var2, var1, 1);
         ServerWorldDatabase.instance.banUser(var0.username, true);
         if (SteamUtils.isSteamModeEnabled()) {
            String var3 = SteamUtils.convertSteamIDToString(var0.steamID);
            ServerWorldDatabase.instance.banSteamID(var3, var2, true);
         } else {
            ServerWorldDatabase.instance.banIp(var0.ip, var0.username, var2, true);
         }

         GameServer.kick(var0, "UI_Policy_Ban", var2);
         var0.forceDisconnect(var1);
         GameServer.addDisconnect(var0);
         return true;
      } else {
         return false;
      }
   }

   private static boolean checkPVP(IsoGameCharacter var0, IsoMovingObject var1) {
      IsoPlayer var2 = (IsoPlayer)Type.tryCastTo(var0, IsoPlayer.class);
      IsoPlayer var3 = (IsoPlayer)Type.tryCastTo(var1, IsoPlayer.class);
      if (var3 != null) {
         if (var3.isGodMod() || !ServerOptions.instance.PVP.getValue() || ServerOptions.instance.SafetySystem.getValue() && var0.isSafety() && ((IsoGameCharacter)var1).isSafety()) {
            return false;
         }

         if (NonPvpZone.getNonPvpZone((int)var1.getX(), (int)var1.getY()) != null) {
            return false;
         }

         if (var2 != null && NonPvpZone.getNonPvpZone((int)var0.getX(), (int)var0.getY()) != null) {
            return false;
         }

         if (var2 != null && !var2.factionPvp && !var3.factionPvp) {
            Faction var4 = Faction.getPlayerFaction(var2);
            Faction var5 = Faction.getPlayerFaction(var3);
            if (var5 != null && var4 == var5) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean doAntiCheatProtection() {
      return !GameServer.bCoop;
   }

   static {
      LoggerManager.createLogger("kick", Core.bDebug);
   }
}
