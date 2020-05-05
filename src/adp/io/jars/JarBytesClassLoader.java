package adp.io.jars;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarBytesClassLoader extends ClassLoader {

    //jars as ByteBlobs
    Map<Set<String>, byte[]> jarBytesViaSupportedClasses = new ConcurrentHashMap<>();

    //defined class cache
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();

    //for package assembly
    private final Map<String, byte[]> cachedClassBytes = new ConcurrentHashMap<>();

    public void addJarBytes(byte[] jarBytes) throws Exception {
        jarBytesViaSupportedClasses.put(extractClassNamesFromJarByteBlob(jarBytes), jarBytes);
    }

    public static Set<String> extractClassNamesFromJarByteBlob(byte[] jarByteBlob) throws IOException {

        Set<String> toReturn = new HashSet<>();

        try (ZipInputStream jis = new ZipInputStream(new ByteArrayInputStream(jarByteBlob))) {

            for (ZipEntry entry; (entry = jis.getNextEntry()) != null; toReturn.add(entry.getName()));

        }

        return Collections.unmodifiableSet(toReturn);

    }

    public boolean supportsClass(final String className) {
        return jarBytesViaSupportedClasses.keySet().stream().anyMatch(classesSupportedByTheseJarBytes -> classesSupportedByTheseJarBytes.contains(className));
    }

    @Override
    public Class<?> findClass(String longFormClassName) {

        return cachedClasses.computeIfAbsent(longFormClassName, name -> {

            //check if the requested class has already been defined by our parent classloader
            Class<?> c = findLoadedClass(name);

            if(c != null)
                return c;

            final byte[] classBytes = getClassByteBlob(name);

            cachedClassBytes.put(name, classBytes);

            System.out.println("Loading Class > " + name);

            return defineClass(name, classBytes, 0, classBytes.length);

        });

    }

    @Override
    public InputStream getResourceAsStream(String name) {

        for(Map.Entry<Set<String>, byte[]> jar : jarBytesViaSupportedClasses.entrySet()) {

            Set<String> supportedClasses = jar.getKey();

            if(supportedClasses.contains(name)) {

                try {

                    byte[] jarBytes = jar.getValue();

                    ZipInputStream toReturn = new ZipInputStream(new ByteArrayInputStream(jarBytes));

                    ZipEntry entry;
                    while ((entry = toReturn.getNextEntry()) != null)
                        if (entry.getName().equals(name))
                            return toReturn;

                    toReturn.close(); // Only close the stream if the entry could not be found (thus why this is not a try-with-resources)

                } catch (IOException e) {

                    e.printStackTrace(System.out);

                }

            }

        }

        return null; // all else has failed, return null

    }

    private byte[] getClassByteBlob(String className) {

        byte[] toReturn = null;

        //read class and write into byte
        try(InputStream is = getResourceAsStream(className.replace(".", "/")+".class");
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {

            for(int len; (len=is.read())!=-1; byteStream.write(len));

            toReturn = byteStream.toByteArray();

        } catch (Exception e) {

            System.out.println("Failed to Load Class > " + className);

        }

        //convert into byte array
        return toReturn;

    }


    /*
        note:
            eventually i'd like to compile classes from jars into "packages"
            of classes that can exist independent of their jars so that
            a jar might contain a collection of assorted classes and we
            can pick only the ones we want to deploy into the cloud
            without dragging along the rest.

            see: PackageClassLoader.java
     */
    public Map<String, byte[]> getCachedClassBytes() {
        return Collections.unmodifiableMap(cachedClassBytes);
    }


}
