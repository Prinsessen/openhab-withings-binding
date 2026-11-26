/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.withings.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openhab.binding.withings.handler.WithingsAccountHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.http.HttpService;

/**
 * The authorization servlet's answers, with mocked requests: the bridges page, the redirect to Withings with
 * client id, scope, redirect URI and state, a callback for an unknown bridge, and a bad POST. Written for the
 * move to Jakarta EE 10 (openHAB 5.3), where the servlet moves from {@code javax.servlet} and the HttpService
 * to {@code jakarta.servlet} and the servlet whiteboard: the assertions are the same on both lines; only the
 * imports and {@link #newServlet()} differ.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
class WithingsServletTest {

    private WithingsServlet servlet = newServlet();
    private HttpServletRequest request = mock(HttpServletRequest.class);
    private HttpServletResponse response = mock(HttpServletResponse.class);
    private StringWriter body = new StringWriter();

    /** openHAB 5.2: registered through the HttpService. */
    private static WithingsServlet newServlet() {
        return new WithingsServlet(mock(HttpService.class));
    }

    @BeforeEach
    void setUp() throws Exception {
        servlet = newServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    private WithingsAccountHandler bridge(String id) {
        WithingsAccountHandler h = mock(WithingsAccountHandler.class);
        Bridge thing = mock(Bridge.class);
        when(thing.getUID()).thenReturn(new ThingUID("withings", "account", id));
        when(thing.getLabel()).thenReturn("My Withings");
        when(thing.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(h.getThing()).thenReturn(thing);
        when(h.getClientId()).thenReturn("client-1");
        when(h.getRedirectUri()).thenReturn("http://openhab.local:8080/withings");
        return h;
    }

    @Test
    void withoutBridgesThePageSaysSo() throws Exception {
        servlet.doGet(request, response);
        verify(response).setContentType(startsWith("text/html"));
        assertTrue(body.toString().contains("No Withings bridges configured"), body.toString());
    }

    @Test
    void theBridgesPageListsEachBridgeWithItsAuthorizeButton() throws Exception {
        servlet.addBridgeHandler(bridge("home"));
        servlet.doGet(request, response);
        String page = body.toString();
        assertTrue(page.contains("My Withings"), page);
        assertTrue(page.contains("withings:account:home"), page);
        assertTrue(page.contains("http://openhab.local:8080/withings"), page);
        assertTrue(page.contains("Authorize with Withings"), page);
    }

    @Test
    void authorizeRedirectsToWithingsWithTheBridgeAsState() throws Exception {
        servlet.addBridgeHandler(bridge("home"));
        when(request.getParameter("action")).thenReturn("authorize");
        when(request.getParameter("bridgeId")).thenReturn("withings:account:home");
        servlet.doPost(request, response);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(url.capture());
        String u = url.getValue();
        assertTrue(u.startsWith("https://account.withings.com/oauth2_user/authorize2?response_type=code"), u);
        assertTrue(u.contains("&client_id=client-1"), u);
        assertTrue(u.contains("&scope=user.metrics%2Cuser.activity%2Cuser.sleepevents%2Cuser.info"), u);
        assertTrue(u.contains("&redirect_uri=http%3A%2F%2Fopenhab.local%3A8080%2Fwithings"), u);
        assertTrue(u.endsWith("&state=withings%3Aaccount%3Ahome"), u);
    }

    @Test
    void aCallbackForAnUnknownBridgeIsAnErrorPage() throws Exception {
        when(request.getParameter("code")).thenReturn("c-1");
        when(request.getParameter("state")).thenReturn("withings:account:nope");
        servlet.doGet(request, response);
        assertTrue(body.toString().contains("Unknown bridge: withings:account:nope"), body.toString());
    }

    @Test
    void aPostWithoutActionIsABadRequest() throws Exception {
        servlet.doPost(request, response);
        verify(response).sendError(eq(400), anyString());
    }
}
