package adp.io.jars;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PackageClassLoader extends ClassLoader {

    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    final Map<String, byte[]> classBytesViaLongFormClassName;

    public PackageClassLoader(Map<String, byte[]> classBytesViaLongFormClassName) {
        this.classBytesViaLongFormClassName = classBytesViaLongFormClassName;
    }

    @Override
    public Class<?> findClass(String longFormClassName) {

        return cachedClasses.computeIfAbsent(longFormClassName, name -> {

            System.out.println("Loading Class > " + name);

            final byte[] classBytes = classBytesViaLongFormClassName.get(name);

            return defineClass(name, classBytes, 0, classBytes.length);

        });

    }

}
