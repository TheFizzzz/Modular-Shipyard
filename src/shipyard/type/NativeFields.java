package shipyard.type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Copy mutable native fields without copying Content identity or registering new types. */
public final class NativeFields{
    private NativeFields(){}
    public static void copy(Object source,Object target,Class<?> first,Class<?> stop){
        try{
            for(Class<?> type=first;type!=stop;type=type.getSuperclass()){
                for(Field field:type.getDeclaredFields()){
                    int flags=field.getModifiers();
                    if(Modifier.isPublic(flags) && !Modifier.isStatic(flags) && !Modifier.isFinal(flags))field.set(target,field.get(source));
                }
            }
        }catch(IllegalAccessException inaccessibleField){throw new IllegalStateException("Cannot copy native fields from "+first.getName(),inaccessibleField);}
    }
}
