package zombie.network.packets;

import java.nio.ByteBuffer;
import zombie.GameWindow;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.radio.ZomboidRadio;

public class WaveSignal implements INetworkPacket {
   int sourceX;
   int sourceY;
   int channel;
   String msg;
   String codes;
   float r;
   float g;
   float b;
   int signalStrength;
   boolean isTV;

   public WaveSignal() {
   }

   public void set(int var1, int var2, int var3, String var4, String var5, float var6, float var7, float var8, int var9, boolean var10) {
      this.sourceX = var1;
      this.sourceY = var2;
      this.channel = var3;
      this.msg = var4;
      this.codes = var5;
      this.r = var6;
      this.g = var7;
      this.b = var8;
      this.signalStrength = var9;
      this.isTV = var10;
   }

   public void parse(ByteBuffer var1, UdpConnection var2) {
      this.sourceX = var1.getInt();
      this.sourceY = var1.getInt();
      this.channel = var1.getInt();
      this.msg = null;
      if (var1.get() == 1) {
         this.msg = GameWindow.ReadString(var1);
      }

      this.codes = null;
      if (var1.get() == 1) {
         this.codes = GameWindow.ReadString(var1);
      }

      this.r = var1.getFloat();
      this.g = var1.getFloat();
      this.b = var1.getFloat();
      this.signalStrength = var1.getInt();
      this.isTV = var1.get() == 1;
   }

   public void write(ByteBufferWriter var1) {
      var1.putInt(this.sourceX);
      var1.putInt(this.sourceY);
      var1.putInt(this.channel);
      var1.putBoolean(this.msg != null);
      if (this.msg != null) {
         GameWindow.WriteString(var1.bb, this.msg);
      }

      var1.putByte((byte)(this.codes != null ? 1 : 0));
      if (this.codes != null) {
         var1.putUTF(this.codes);
      }

      var1.putFloat(this.r);
      var1.putFloat(this.g);
      var1.putFloat(this.b);
      var1.putInt(this.signalStrength);
      var1.putByte((byte)(this.isTV ? 1 : 0));
   }

   public void process(UdpConnection var1) {
      if (GameServer.bServer) {
         ZomboidRadio.getInstance().SendTransmission(var1.getConnectedGUID(), this.sourceX, this.sourceY, this.channel, this.msg, this.codes, this.r, this.g, this.b, this.signalStrength, this.isTV);
      } else {
         ZomboidRadio.getInstance().ReceiveTransmission(this.sourceX, this.sourceY, this.channel, this.msg, this.codes, this.r, this.g, this.b, this.signalStrength, this.isTV);
      }

   }
}
