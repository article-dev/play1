package play.db;

public class Operation<T> {
    public OperationType operationType;
    public Class clazz;
    public Long id;
    public T currentState;
    public T previousState;
}