Usage:
------

        import com.lleggieri.concurrent.lock.Chain;
        import java.util.concurrent.CompletionStage;

        public class YourClass {

            private final Chain<Key> locker = new Chain<Key>();

            private CompletionStage<Value> yourMethodThatReturnsAPromise(final Key key) { ... }
  
            public CompletionStage<Value> wrappedMethod(final Key key) {
                return locker.executeLockingOn(key, () -> yourMethodThatReturnsAPromise(key));
            }
            
        }
    