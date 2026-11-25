/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.joran.event;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.Attributes;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.testUtil.CoreTestConstants;
import ch.qos.logback.core.status.testUtil.StatusChecker;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * Test whether SaxEventRecorder does a good job.
 * 
 * @author Ceki Gulcu
 */
public class SaxEventRecorderTest {

    Context context = new ContextBase();
    StatusChecker statusChecker = new StatusChecker(context);

    SAXParser createParser() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        return spf.newSAXParser();
    }

    public List<SaxEvent> doTest(String filename) throws Exception {
        SaxEventRecorder recorder = new SaxEventRecorder(context);
        FileInputStream fis = new FileInputStream(CoreTestConstants.TEST_SRC_PREFIX + "input/joran/" + filename);
        recorder.recordEvents(fis);
        return recorder.getSaxEventList();

    }

    public void dump(List<SaxEvent> seList) {
        for (SaxEvent se : seList) {
            System.out.println(se);
        }
    }

    @Test
    public void test1() throws Exception {
        List<SaxEvent> seList = doTest("event1.xml");
        Assertions.assertTrue(statusChecker.getHighestLevel(0) == Status.INFO);
        // dump(seList);
        Assertions.assertEquals(11, seList.size());
    }

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    public void testEventSSRF() throws Exception {
        try {
            List<SaxEvent> seList = doTest("event-ssrf.xml");
            Assertions.assertTrue(statusChecker.getHighestLevel(0) == Status.WARN);
            statusChecker.assertContainsMatch(Status.WARN, "Document Type Declaration");
            Assertions.assertEquals(11, seList.size());
        } finally {
            StatusPrinter.print(context);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void doctypeSystemReferenceDoesNotTriggerOutboundRequest() throws Exception {
        assertNoOutboundRequest("<!DOCTYPE test SYSTEM \"http://127.0.0.1:%d/ssrf.dtd\">");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void doctypePublicReferenceDoesNotTriggerOutboundRequest() throws Exception {
        assertNoOutboundRequest("<!DOCTYPE test PUBLIC \"-//SSRF//DTD Test//EN\" \"http://127.0.0.1:%d/ssrf.dtd\">");
    }

    private void assertNoOutboundRequest(String doctypeTemplate) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            final AtomicInteger connectionCount = new AtomicInteger();
            Thread acceptor = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try (Socket socket = serverSocket.accept()) {
                        connectionCount.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            String doctype = String.format(doctypeTemplate, serverSocket.getLocalPort());
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + doctype + "\n"
                    + "<test><hello name=\"John Doe\">XXX&amp;</hello></test>";

            SaxEventRecorder recorder = new SaxEventRecorder(context);
            try {
                recorder.recordEvents(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            } finally {
                StatusPrinter.print(context);
            }

            Assertions.assertEquals(0, connectionCount.get(), "the DOCTYPE external reference was fetched");
            statusChecker.assertContainsMatch(Status.WARN, "Document Type Declaration");
            Assertions.assertEquals(5, recorder.getSaxEventList().size());
        }
    }

    @Test
    public void test2() throws Exception {
        List<SaxEvent> seList = doTest("ampEvent.xml");
        Assertions.assertTrue(statusChecker.getHighestLevel(0) == Status.INFO);
        // dump(seList);
        Assertions.assertEquals(3, seList.size());

        BodyEvent be = (BodyEvent) seList.get(1);
        Assertions.assertEquals("xxx & yyy", be.getText());
    }

    @Test
    public void test3() throws Exception {
        List<SaxEvent> seList = doTest("inc.xml");
        Assertions.assertTrue(statusChecker.getHighestLevel(0) == Status.INFO);
        // dump(seList);
        Assertions.assertEquals(4, seList.size());

        StartEvent se = (StartEvent) seList.get(1);
        Attributes attr = se.getAttributes();
        Assertions.assertNotNull(attr);
        Assertions.assertEquals("1", attr.getValue("increment"));
    }

    @Test
    public void bodyWithSpacesAndQuotes() throws Exception {
        List<SaxEvent> seList = doTest("spacesAndQuotes.xml");
        Assertions.assertEquals(3, seList.size());
        BodyEvent be = (BodyEvent) seList.get(1);
        Assertions.assertEquals("[x][x] \"xyz\"%n", be.getText());
    }

}
