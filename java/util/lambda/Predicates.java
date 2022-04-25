package zombie.util.lambda;

import java.util.function.Predicate;
import zombie.util.Pool;
import zombie.util.PooledObject;

public final class Predicates {
   public Predicates() {
   }

   public static final class Params3 {
      public Params3() {
      }

      public static final class CallbackStackItem<E, T1, T2, T3> extends Predicates.Params3.StackItem<T1, T2, T3> implements Predicate<E> {
         private Predicates.Params3.ICallback<E, T1, T2, T3> predicate;
         private static final Pool<Predicates.Params3.CallbackStackItem<Object, Object, Object, Object>> s_pool = new Pool(Predicates.Params3.CallbackStackItem::new);

         public CallbackStackItem() {
         }

         public boolean test(E var1) {
            return this.predicate.test(var1, this.val1, this.val2, this.val3);
         }

         public static <E, T1, T2, T3> Predicates.Params3.CallbackStackItem<E, T1, T2, T3> alloc(T1 var0, T2 var1, T3 var2, Predicates.Params3.ICallback<E, T1, T2, T3> var3) {
            Predicates.Params3.CallbackStackItem var4 = (Predicates.Params3.CallbackStackItem)s_pool.alloc();
            var4.val1 = var0;
            var4.val2 = var1;
            var4.val3 = var2;
            var4.predicate = var3;
            return var4;
         }

         public void onReleased() {
            this.val1 = null;
            this.val2 = null;
            this.val3 = null;
            this.predicate = null;
         }
      }

      private static class StackItem<T1, T2, T3> extends PooledObject {
         T1 val1;
         T2 val2;
         T3 val3;

         private StackItem() {
         }
      }

      public interface ICallback<E, T1, T2, T3> {
         boolean test(E var1, T1 var2, T2 var3, T3 var4);
      }
   }

   public static final class Params2 {
      public Params2() {
      }

      public static final class CallbackStackItem<E, T1, T2> extends Predicates.Params2.StackItem<T1, T2> implements Predicate<E> {
         private Predicates.Params2.ICallback<E, T1, T2> predicate;
         private static final Pool<Predicates.Params2.CallbackStackItem<Object, Object, Object>> s_pool = new Pool(Predicates.Params2.CallbackStackItem::new);

         public CallbackStackItem() {
         }

         public boolean test(E var1) {
            return this.predicate.test(var1, this.val1, this.val2);
         }

         public static <E, T1, T2> Predicates.Params2.CallbackStackItem<E, T1, T2> alloc(T1 var0, T2 var1, Predicates.Params2.ICallback<E, T1, T2> var2) {
            Predicates.Params2.CallbackStackItem var3 = (Predicates.Params2.CallbackStackItem)s_pool.alloc();
            var3.val1 = var0;
            var3.val2 = var1;
            var3.predicate = var2;
            return var3;
         }

         public void onReleased() {
            this.val1 = null;
            this.val2 = null;
            this.predicate = null;
         }
      }

      private static class StackItem<T1, T2> extends PooledObject {
         T1 val1;
         T2 val2;

         private StackItem() {
         }
      }

      public interface ICallback<E, T1, T2> {
         boolean test(E var1, T1 var2, T2 var3);
      }
   }

   public static final class Params1 {
      public Params1() {
      }

      public static final class CallbackStackItem<E, T1> extends Predicates.Params1.StackItem<T1> implements Predicate<E> {
         private Predicates.Params1.ICallback<E, T1> predicate;
         private static final Pool<Predicates.Params1.CallbackStackItem<Object, Object>> s_pool = new Pool(Predicates.Params1.CallbackStackItem::new);

         public CallbackStackItem() {
         }

         public boolean test(E var1) {
            return this.predicate.test(var1, this.val1);
         }

         public static <E, T1> Predicates.Params1.CallbackStackItem<E, T1> alloc(T1 var0, Predicates.Params1.ICallback<E, T1> var1) {
            Predicates.Params1.CallbackStackItem var2 = (Predicates.Params1.CallbackStackItem)s_pool.alloc();
            var2.val1 = var0;
            var2.predicate = var1;
            return var2;
         }

         public void onReleased() {
            this.val1 = null;
            this.predicate = null;
         }
      }

      private static class StackItem<T1> extends PooledObject {
         T1 val1;

         private StackItem() {
         }
      }

      public interface ICallback<E, T1> {
         boolean test(E var1, T1 var2);
      }
   }
}
