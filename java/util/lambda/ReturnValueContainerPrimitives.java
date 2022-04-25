package zombie.util.lambda;

import zombie.util.Pool;
import zombie.util.PooledObject;

public final class ReturnValueContainerPrimitives {
   public ReturnValueContainerPrimitives() {
   }

   public static final class RVInt extends PooledObject {
      public int ReturnVal;
      private static final Pool<ReturnValueContainerPrimitives.RVInt> s_pool = new Pool(ReturnValueContainerPrimitives.RVInt::new);

      public RVInt() {
      }

      public void onReleased() {
         this.ReturnVal = 0;
      }

      public static ReturnValueContainerPrimitives.RVInt alloc() {
         return (ReturnValueContainerPrimitives.RVInt)s_pool.alloc();
      }
   }

   public static final class RVFloat extends PooledObject {
      public float ReturnVal;
      private static final Pool<ReturnValueContainerPrimitives.RVFloat> s_pool = new Pool(ReturnValueContainerPrimitives.RVFloat::new);

      public RVFloat() {
      }

      public void onReleased() {
         this.ReturnVal = 0.0F;
      }

      public static ReturnValueContainerPrimitives.RVFloat alloc() {
         return (ReturnValueContainerPrimitives.RVFloat)s_pool.alloc();
      }
   }

   public static final class RVBoolean extends PooledObject {
      public boolean ReturnVal;
      private static final Pool<ReturnValueContainerPrimitives.RVBoolean> s_pool = new Pool(ReturnValueContainerPrimitives.RVBoolean::new);

      public RVBoolean() {
      }

      public void onReleased() {
         this.ReturnVal = false;
      }

      public static ReturnValueContainerPrimitives.RVBoolean alloc() {
         return (ReturnValueContainerPrimitives.RVBoolean)s_pool.alloc();
      }
   }
}
