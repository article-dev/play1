package play.db.jpa;

import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.type.Type;

import play.Play;
import play.db.Operation;
import play.db.OperationType;
import play.db.PostTransaction;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HibernateInterceptor extends EmptyInterceptor {

    public HibernateInterceptor() {
        operations = new ThreadLocal<LinkedList<Operation>>();
        operations.set(new LinkedList<>());
    }

    @Override
    public int[] findDirty(Object o, Serializable id, Object[] arg2, Object[] arg3, String[] arg4, Type[] arg5) {
        if (o instanceof JPABase && !((JPABase) o).willBeSaved) {
            return new int[0];
        }
        return null;
    }

    @Override
    public boolean onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        if (collection instanceof PersistentCollection) {
            Object o = ((PersistentCollection) collection).getOwner();
            if (o instanceof JPABase) {
                if (entityLocal.get() != null) {
                    return ((JPABase) o).willBeSaved || ((JPABase) entityLocal.get()).willBeSaved;
                } else {
                    return ((JPABase) o).willBeSaved;
                }
            }
        } else {
            System.out.println("HOO: Case not handled !!!");
        }
        return super.onCollectionUpdate(collection, key);
    }

    @Override
    public boolean onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
        if (collection instanceof PersistentCollection) {
            Object o = ((PersistentCollection) collection).getOwner();
            if (o instanceof JPABase) {
                if (entityLocal.get() != null) {
                    return ((JPABase) o).willBeSaved || ((JPABase) entityLocal.get()).willBeSaved;
                } else {
                    return ((JPABase) o).willBeSaved;
                }
            }
        } else {
            System.out.println("HOO: Case not handled !!!");
        }

        return super.onCollectionRecreate(collection, key);
    }

    @Override
    public boolean onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        if (collection instanceof PersistentCollection) {
            Object o = ((PersistentCollection) collection).getOwner();
            if (o instanceof JPABase) {
                if (entityLocal.get() != null) {
                    return ((JPABase) o).willBeSaved || ((JPABase) entityLocal.get()).willBeSaved;
                } else {
                    return ((JPABase) o).willBeSaved;
                }
            }
        } else {
            System.out.println("HOO: Case not handled !!!");
        }
        return super.onCollectionRemove(collection, key);
    }

    protected final ThreadLocal<Object> entityLocal = new ThreadLocal<>();
    protected final ThreadLocal<LinkedList<Operation>> operations;

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        entityLocal.set(entity);
        Operation operation = new Operation();
        operation.operationType = OperationType.INSERT;
        operation.id = (Long) id;
        operation.clazz = entity.getClass();
        operation.currentState = entity;
        operation.previousState = null;
        operations.get().add(operation);
        return super.onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState,
            String[] propertyNames, Type[] types) {
        Operation operation = new Operation();
        operation.operationType = OperationType.UPDATE;
        operation.id = (Long) id;
        operation.clazz = entity.getClass();
        operation.currentState = entity;

        try {
            Object previousEntity = Play.classloader.loadApplicationClass(operation.clazz.getName()).newInstance();
            for (int i = 0; i < previousState.length; i++) {
                Field field = previousEntity.getClass().getDeclaredField(propertyNames[i]);
                field.setAccessible(true);
                field.set(previousEntity, previousState[i]);
                operation.previousState = previousEntity;
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
                | InstantiationException e) {
            e.printStackTrace();
        }

        operations.get().add(operation);
        return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        Operation operation = new Operation();
        operation.operationType = OperationType.DELETE;
        operation.id = (Long) id;
        operation.clazz = entity.getClass();
        operation.currentState = null;
        operation.previousState = entity;
        operations.get().add(operation);
        super.onDelete(entity, id, state, propertyNames, types);
    }

    @Override
    public void afterTransactionCompletion(org.hibernate.Transaction tx) {
        if (tx.getStatus() == TransactionStatus.ROLLED_BACK && operations.get() != null
                && operations.get().size() > 0) {
            operations.get().stream()
                    .collect(Collectors.groupingBy(x -> x.clazz, Collectors.toCollection(LinkedList::new))).entrySet()
                    .stream().forEach(new Consumer<Map.Entry<Class, LinkedList<Operation>>>() {
                        // maybe should cache annotated methods to avoid checking every time.
                        // not sure how play's hot code deploy will act if cached in a static collection
                        public void accept(Entry<Class, LinkedList<Operation>> t) {
                            try {
                                Class clazz = Play.classloader.loadApplicationClass(t.getKey().getName());
                                if (clazz != null) {
                                    for (Method method : clazz.getMethods()) {
                                        if (method.isAnnotationPresent(PostTransaction.class)
                                                && method.getParameterTypes() != null
                                                && method.getParameterTypes().length == 1) {
                                            for (PostTransaction postTransaction : method
                                                    .getAnnotationsByType(PostTransaction.class)) {
                                                if (postTransaction.operationType() == null
                                                        || postTransaction.operationType().length == 0) {
                                                    return;
                                                }
                                                List<OperationType> operationTypes = Arrays
                                                        .asList(postTransaction.operationType());
                                                List<Operation> operations = t.getValue().stream()
                                                        .filter(x -> operationTypes.contains(x.operationType))
                                                        .collect(Collectors.toList());
                                                method.invoke(null, operations);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("CALLING POST TRANSACTION METHOD FAILED: " + e.getMessage());
                            }
                        }
                    });
            ;
        }
        if (operations.get() != null) {
            operations.get().clear();
        }
        entityLocal.remove();
    }
}