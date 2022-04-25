package zombie.util.lambda;

import java.util.Comparator;
import zombie.util.Pool;
import zombie.util.PooledObject;

public final class Comparators {
   public Comparators() {
   }

   public static final class Params2 {
      public Params2() {
      }

      public static final class CallbackStackItem<E, T1, T2> extends Comparators.Params2.StackItem<T1, T2> implements Comparator<E> {
         private Comparators.Params2.ICallback<E, T1, T2> comparator;
         private static final Pool<Comparators.Params2.CallbackStackItem<Object, Object, Object>> s_pool = new Pool(Comparators.Params2.CallbackStackItem::new);

         public CallbackStackItem() {
         }

         public int compare(E var1, E var2) {
            return this.comparator.compare(var1, var2, this.val1, this.val2);
         }

         public static <E, T1, T2> Comparators.Params2.CallbackStackItem<E, T1, T2> alloc(T1 var0, T2 var1, Comparators.Params2.ICallback<E, T1, T2> var2) {
            Comparators.Params2.CallbackStackItem var3 = (Comparators.Params2.CallbackStackItem)s_pool.alloc();
            var3.val1 = var0;
            var3.val2 = var1;
            var3.comparator = var2;
            return var3;
         }

         public void onReleased() {
            this.val1 = null;
            this.val2 = null;
            this.comparator = null;
         }
      }

      private static class StackItem<T1, T2> extends PooledObject {
         T1 val1;
         T2 val2;

         private StackItem() {
         }
      }

      public interface ICallback<E, T1, T2> {
         int compare(E var1, E var2, T1 var3, T2 var4);
      }
   }

   public static final class Params1 {
      public Params1() {
      }

      public static final class CallbackStackItem<E, T1> extends Comparators.Params1.StackItem<T1> implements Comparator<E> {
         private Comparators.Params1.ICallback<E, T1> comparator;
         private static final Pool<Comparators.Params1.CallbackStackItem<Object, Object>> s_pool = new Pool(Comparators.Params1.CallbackStackItem::new);

         public CallbackStackItem() {
         }

         public int compare(E var1, E var2) {
            return this.comparator.compare(var1, var2, this.val1);
         }

         public static <E, T1> Comparators.Params1.CallbackStackItem<E, T1> alloc(T1 var0, Comparators.Params1.ICallback<E, T1> var1) {
            Comparators.Params1.CallbackStackItem var2 = (Comparators.Params1.CallbackStackItem)s_pool.alloc();
            var2.val1 = var0;
            var2.comparator = var1;
            return var2;
         }

         public void onReleased() {
            this.val1 = null;
            this.comparator = null;
         }
      }

      private static class StackItem<T1> extends PooledObject {
         T1 val1;

         private StackItem() {
         }
      }

      public interface ICallback<E, T1> {
         int compare(E var1, E var2, T1 var3);
      }
   }
}
