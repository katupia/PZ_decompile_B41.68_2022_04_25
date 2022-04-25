package zombie.network.packets;

import java.nio.ByteBuffer;
import java.util.Iterator;
import zombie.core.Core;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.iso.IsoMovingObject;
import zombie.iso.objects.IsoDeadBody;
import zombie.network.packets.hit.Square;

public class RemoveCorpseFromMap implements INetworkPacket {
   public Square position = new Square();
   private short id;
   private IsoDeadBody deadBody = null;

   public RemoveCorpseFromMap() {
   }

   public void set(IsoDeadBody var1) {
      this.position.set(var1.getSquare());
      this.id = var1.getOnlineID();
      this.deadBody = var1;
   }

   public void parse(ByteBuffer var1, UdpConnection var2) {
      this.position.parse(var1, var2);
      this.id = var1.getShort();
      this.deadBody = null;
      if (this.position.getSquare() != null) {
         Iterator var3 = this.position.getSquare().getStaticMovingObjects().iterator();

         while(var3.hasNext()) {
            IsoMovingObject var4 = (IsoMovingObject)var3.next();
            if (var4 instanceof IsoDeadBody && ((IsoDeadBody)var4).getOnlineID() == this.id) {
               this.deadBody = (IsoDeadBody)var4;
               break;
            }
         }
      }

   }

   public void write(ByteBufferWriter var1) {
      this.position.write(var1);
      var1.putShort(this.id);
   }

   public void process() {
      if (Core.bDebug) {
         String var1 = this.getDescription();
         if (!DebugLog.isEnabled(DebugType.Death)) {
            DebugLog.log(DebugType.Multiplayer, var1);
         }

         DebugLog.log(DebugType.Death, var1);
      }

      this.position.getSquare().removeCorpse(this.deadBody, true);
   }

   public String getDescription() {
      return String.format(this.getClass().getSimpleName() + " [ id=%d, position=%s ]", this.id, this.position.getDescription());
   }

   public boolean isConsistent() {
      if (!this.position.isConsistent()) {
         return false;
      } else {
         boolean var1 = false;
         Iterator var2 = this.position.getSquare().getStaticMovingObjects().iterator();

         while(var2.hasNext()) {
            IsoMovingObject var3 = (IsoMovingObject)var2.next();
            if (var3 instanceof IsoDeadBody && ((IsoDeadBody)var3).getOnlineID() == this.id) {
               var1 = true;
               break;
            }
         }

         return var1;
      }
   }
}
