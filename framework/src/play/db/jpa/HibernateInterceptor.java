package play.db.jpa;

import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.type.Type;

import play.Play;
import play.db.PostTransaction;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HibernateInterceptor extends EmptyInterceptor {

    public HibernateInterceptor() {

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
    protected final ThreadLocal<LinkedList<Pair>> entities = new ThreadLocal<LinkedList<Pair>>();

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        entityLocal.set(entity);
        putEntity(entities, entity, id);
        return super.onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState,
            String[] propertyNames, Type[] types) {
        putEntity(entities, entity, id);
        return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        putEntity(entities, entity, id);
        super.onDelete(entity, id, state, propertyNames, types);
    }

    static class Pair {
        String clazz;
        Long id;
    }

    private static void putEntity(ThreadLocal<LinkedList<Pair>> entities, Object object, Serializable id) {
        if (entities.get() == null) {
            entities.set(new LinkedList<>());
        }
        Pair pair = new Pair();
        pair.clazz = object.getClass().getName();
        pair.id = (Long) id;
        entities.get().push(pair);
    }

    @Override
    public void afterTransactionCompletion(org.hibernate.Transaction tx) {
        if (tx.getStatus() == TransactionStatus.COMMITTED && entities.get() != null && entities.get().size() > 0) {
            entities.get().stream()
                    .collect(Collectors.groupingBy(x -> x.clazz,
                            Collectors.mapping(x -> x.id, Collectors.toCollection(LinkedList::new))))
                    .entrySet().stream().forEach(new Consumer<Map.Entry<String, LinkedList<Long>>>() {
                        public void accept(Entry<String, LinkedList<Long>> t) {
                            try {
                                Class clazz = Play.classloader.loadApplicationClass(t.getKey().toString());
                                if (clazz != null) {
                                    for (Method method : clazz.getMethods()) {
                                        if (method.isAnnotationPresent(PostTransaction.class)) {
                                            if (method.getParameterTypes() != null
                                                    && method.getParameterTypes().length == 1)
                                                method.invoke(null, t.getValue());
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
        if (entities.get() != null) {
            entities.get().clear();
        }
        entityLocal.remove();
    }
}