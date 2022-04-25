package zombie.network;

import java.util.HashMap;
import zombie.core.Rand;

public class IsoObjectID<T> {
   private static final short MAX_ID = 32767;
   private final HashMap<Short, T> IDToObjectMap = new HashMap(32767);
   private short nextID = (short)Rand.Next(32766);

   public IsoObjectID() {
   }

   public void put(short var1, T var2) {
      this.IDToObjectMap.put(var1, var2);
   }

   public void remove(short var1) {
      this.IDToObjectMap.remove(var1);
   }

   public T get(short var1) {
      return this.IDToObjectMap.get(var1);
   }

   public int size() {
      return this.IDToObjectMap.size();
   }

   public short allocateID() {
      short var10002 = this.nextID;
      this.nextID = (short)(var10002 + 1);
      return var10002;
   }
}
