package play.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a STATIC method on a model entity class to be called after a transaction finishes.
 * Will only be triggered when this certain entity is created/updated/deleted in this transaction.
 * The annotated static method needs to have one parameter, a collection of {@link Operation}
 * 
 * @author han
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostTransaction {

    public OperationType[] operationType() default { OperationType.INSERT, OperationType.UPDATE, OperationType.DELETE };

}