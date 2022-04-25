package zombie.network;

import zombie.core.logger.LoggerManager;
import zombie.core.raknet.UdpConnection;
import zombie.core.znet.ZNetSessionState;
import zombie.debug.DebugLog;
import zombie.debug.LogSeverity;

public class ConnectionManager {
   public ConnectionManager() {
   }

   public static void log(String var0, UdpConnection var1) {
      if (GameServer.bServer) {
         try {
            LoggerManager.getLogger("connection").write(var0 + ": " + LoginQueue.getDescription() + " " + GameServer.udpEngine.getDescription() + " " + GameServer.getDescription());
            if (var1 != null) {
               LoggerManager.getLogger("connection").write(var0 + ": " + var1.getDescription());
               LoggerManager.getLogger("connection").write(var0 + ": " + var1.getPlayerDescription());
               ZNetSessionState var2 = var1.getP2PSessionState();
               if (var2 != null) {
                  LoggerManager.getLogger("connection").write(var0 + ": " + var2.getDescription());
               }
            }
         } catch (Exception var3) {
            DebugLog.Multiplayer.printException(var3, "ConnectionManager.log", LogSeverity.Error);
         }
      } else if (GameClient.bClient) {
         DebugLog.General.warn(var0);
      }

   }
}
