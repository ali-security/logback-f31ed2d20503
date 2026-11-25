package ch.qos.logback.core.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.testUtil.StatusChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HardenedObjectInputStreamTest {

    ByteArrayOutputStream bos;
    ObjectOutputStream oos;
    HardenedObjectInputStream inputStream;
    Context context = new ContextBase();
    String[] whitelist = new String[] { Innocent.class.getName() };

    @BeforeEach
    public void setUp() throws Exception {
        bos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(bos);
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void smoke() throws ClassNotFoundException, IOException {
        Innocent innocent = new Innocent();
        innocent.setAnInt(1);
        innocent.setAnInteger(2);
        innocent.setaString("smoke");
        Innocent back = writeAndRead(innocent);
        assertEquals(innocent, back);
    }

    private Innocent writeAndRead(Innocent innocent) throws IOException, ClassNotFoundException {
        writeObject(oos, innocent);
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        inputStream = new HardenedObjectInputStream(context, bis, whitelist);
        Innocent fooBack = (Innocent) inputStream.readObject();
        inputStream.close();
        return fooBack;
    }

    private void writeObject(ObjectOutputStream oos, Object o) throws IOException {
        oos.writeObject(o);
        oos.flush();
        oos.close();
    }

    @Test
    public void denialOfService() throws ClassNotFoundException, IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload());
        inputStream = new HardenedObjectInputStream(context, bis, whitelist);
        try {
            assertThrows(InvalidClassException.class, () -> inputStream.readObject());
        } finally {
            inputStream.close();
        }
    }

    // Classes in the java.util package are no longer blindly authorized. HashSet is
    // used by many gadget chains (e.g. SerialDOS).
    @Test
    public void unauthorizedJavaUtilHashSet() throws IOException {
        Set<String> set = new HashSet<>();
        set.add("a");
        assertUnauthorized(set, "java.util.HashSet");
    }

    // PriorityQueue is the entry point of well known gadget chains (e.g.
    // CommonsCollections2/4). It must not be deserialized just because it lives
    // in java.util.
    @Test
    public void unauthorizedJavaUtilPriorityQueue() throws IOException {
        PriorityQueue<String> queue = new PriorityQueue<>();
        queue.add("a");
        assertUnauthorized(queue, "java.util.PriorityQueue");
    }

    // Classes in the java.lang package are no longer blindly authorized either.
    @Test
    public void unauthorizedJavaLangClass() throws IOException {
        assertUnauthorized(Thread.State.NEW, "java.lang.Thread$State");
    }

    @Test
    public void authorizedJavaClassesAreStillAccepted() throws IOException, ClassNotFoundException {
        HashMap<String, Object> map = new HashMap<>();
        map.put("string", "s");
        map.put("integer", 1);
        map.put("long", 2L);
        map.put("boolean", Boolean.TRUE);
        ArrayList<String> list = new ArrayList<>();
        list.add("x");
        map.put("list", list);
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<>();
        linkedHashMap.put("k", "v");
        map.put("linkedHashMap", linkedHashMap);

        writeObject(oos, map);
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        inputStream = new HardenedObjectInputStream(context, bis, whitelist);
        try {
            Object back = inputStream.readObject();
            assertEquals(map, back);
        } finally {
            inputStream.close();
        }
        new StatusChecker(context).assertIsErrorFree();
    }

    // the context-less constructors are retained for binary compatibility and
    // must enforce the same restrictions
    @Test
    public void contextLessConstructorsRejectUnauthorizedClasses() throws IOException {
        PriorityQueue<String> queue = new PriorityQueue<>();
        queue.add("a");
        writeObject(oos, queue);
        byte[] bytes = bos.toByteArray();

        HardenedObjectInputStream arrayVariant = new HardenedObjectInputStream(new ByteArrayInputStream(bytes),
                whitelist);
        try {
            assertThrows(InvalidClassException.class, () -> arrayVariant.readObject());
        } finally {
            arrayVariant.close();
        }

        HardenedObjectInputStream listVariant = new HardenedObjectInputStream(new ByteArrayInputStream(bytes),
                Arrays.asList(whitelist));
        try {
            assertThrows(InvalidClassException.class, () -> listVariant.readObject());
        } finally {
            listVariant.close();
        }

        HardenedObjectInputStream nullWhitelist = new HardenedObjectInputStream(new ByteArrayInputStream(bytes),
                (String[]) null);
        try {
            assertThrows(InvalidClassException.class, () -> nullWhitelist.readObject());
        } finally {
            nullWhitelist.close();
        }
    }

    private void assertUnauthorized(Object o, String className) throws IOException {
        writeObject(oos, o);
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        inputStream = new HardenedObjectInputStream(context, bis, whitelist);
        try {
            InvalidClassException e = assertThrows(InvalidClassException.class, () -> inputStream.readObject());
            assertTrue(e.getMessage().contains(className), e.getMessage());
        } finally {
            inputStream.close();
        }
        new StatusChecker(context).assertContainsMatch(Status.ERROR,
                "Unauthorized deserialization attempt for class \\[" + Pattern.quote(className) + "\\]");
    }

    private byte[] payload() throws IOException {
        Set root = buildEvilHashset();
        writeObject(oos, root);
        return bos.toByteArray();
    }

    private Set buildEvilHashset() {
        Set root = new HashSet();
        Set s1 = root;
        Set s2 = new HashSet();
        for (int i = 0; i < 100; i++) {
            Set t1 = new HashSet();
            Set t2 = new HashSet();
            t1.add("foo"); // make it not equal to t2
            s1.add(t1);
            s1.add(t2);
            s2.add(t1);
            s2.add(t2);
            s1 = t1;
            s2 = t2;
        }
        return root;
    }
}
