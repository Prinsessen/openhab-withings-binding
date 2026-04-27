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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.openhab.binding.withings.WithingsBindingConstants.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.dto.WithingsApiResponse;
import org.openhab.binding.withings.handler.WithingsAccountHandler;
import org.openhab.core.thing.ThingStatus;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Withings OAuth2 callback servlet. Handles the authorization flow for Withings API accounts.
 *
 * <p>
 * Provides a web page at /withings where users can initiate the OAuth2 authorization flow
 * for their Withings bridges. After the user authorizes with Withings, the callback returns
 * to this servlet with an authorization code, which is exchanged for access/refresh tokens.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
@Component(service = WithingsServlet.class, scope = ServiceScope.SINGLETON, immediate = true)
public class WithingsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SERVLET_PATH = "/withings";

    private final Logger logger = LoggerFactory.getLogger(WithingsServlet.class);
    private final HttpService httpService;
    private final Set<WithingsAccountHandler> bridgeHandlers;
    private final Gson gson = new GsonBuilder().create();

    @Activate
    public WithingsServlet(@Reference HttpService httpService) {
        this.httpService = httpService;
        this.bridgeHandlers = new CopyOnWriteArraySet<>();

        try {
            logger.debug("Registering Withings servlet at {}", SERVLET_PATH);
            httpService.registerServlet(SERVLET_PATH, this, null, httpService.createDefaultHttpContext());
        } catch (ServletException | NamespaceException e) {
            logger.warn("Could not register Withings servlet at {}: {}", SERVLET_PATH, e.getMessage(), e);
        }
    }

    @Deactivate
    protected void dispose() {
        httpService.unregister(SERVLET_PATH);
    }

    /**
     * Register a bridge handler so the servlet can manage its authorization.
     */
    public void addBridgeHandler(WithingsAccountHandler handler) {
        bridgeHandlers.add(handler);
    }

    /**
     * Unregister a bridge handler.
     */
    public void removeBridgeHandler(WithingsAccountHandler handler) {
        bridgeHandlers.remove(handler);
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }

        response.setContentType("text/html; charset=UTF-8");
        response.setCharacterEncoding(UTF_8.name());

        String code = request.getParameter("code");
        String state = request.getParameter("state");

        if (code != null && state != null && !code.isBlank() && !state.isBlank()) {
            handleCallback(request, response, code, state);
        } else {
            showBridgesPage(response);
        }
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }

        String action = request.getParameter("action");
        String bridgeId = request.getParameter("bridgeId");

        if ("authorize".equals(action) && bridgeId != null && !bridgeId.isBlank()) {
            handleAuthorize(response, bridgeId);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing action or bridgeId");
        }
    }

    /**
     * Shows the bridges overview page with authorize buttons.
     */
    private void showBridgesPage(HttpServletResponse response) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append(pageHeader("Withings Authorization"));
        html.append("<h1>Withings Account Authorization</h1>");

        if (bridgeHandlers.isEmpty()) {
            html.append("<div class='card'>");
            html.append("<p>No Withings bridges configured. Add a Withings Account bridge first.</p>");
            html.append("</div>");
        } else {
            for (WithingsAccountHandler handler : bridgeHandlers) {
                String bridgeUid = handler.getThing().getUID().getAsString();
                String label = handler.getThing().getLabel();
                ThingStatus status = handler.getThing().getStatus();
                String redirectUri = handler.getRedirectUri();

                html.append("<div class='card'>");
                html.append("<h2>").append(escapeHtml(label != null ? label : bridgeUid)).append("</h2>");
                html.append("<table>");
                html.append("<tr><td><strong>Bridge UID:</strong></td><td>").append(escapeHtml(bridgeUid))
                        .append("</td></tr>");
                html.append("<tr><td><strong>Status:</strong></td><td><span class='status-")
                        .append(status == ThingStatus.ONLINE ? "online" : "offline").append("'>")
                        .append(escapeHtml(status.toString())).append("</span></td></tr>");
                html.append("<tr><td><strong>Redirect URI:</strong></td><td>").append(escapeHtml(redirectUri))
                        .append("</td></tr>");
                html.append("</table>");
                html.append("<form method='POST'>");
                html.append("<input type='hidden' name='action' value='authorize'/>");
                html.append("<input type='hidden' name='bridgeId' value='").append(escapeHtml(bridgeUid)).append("'/>");
                html.append("<button type='submit'>Authorize with Withings</button>");
                html.append("</form>");
                html.append("</div>");
            }
        }

        html.append(pageFooter());
        response.getWriter().write(html.toString());
    }

    /**
     * Generates the Withings authorization URL and redirects the browser.
     */
    private void handleAuthorize(HttpServletResponse response, String bridgeId) throws IOException {
        Optional<WithingsAccountHandler> handler = getBridgeHandler(bridgeId);

        if (handler.isEmpty()) {
            sendErrorPage(response, "Unknown bridge: " + bridgeId);
            return;
        }

        WithingsAccountHandler bridge = handler.get();
        String clientId = bridge.getClientId();
        String redirectUri = bridge.getRedirectUri();

        if (clientId == null || clientId.isBlank()) {
            sendErrorPage(response, "Client ID is not configured on the bridge.");
            return;
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            sendErrorPage(response, "Redirect URI is not configured on the bridge.");
            return;
        }

        String authUrl = API_AUTH_URL + "?" + "response_type=code" + "&client_id=" + URLEncoder.encode(clientId, UTF_8)
                + "&scope=" + URLEncoder.encode(OAUTH_SCOPE, UTF_8) + "&redirect_uri="
                + URLEncoder.encode(redirectUri, UTF_8) + "&state=" + URLEncoder.encode(bridgeId, UTF_8);

        logger.info("Redirecting to Withings authorization URL for bridge {}", bridgeId);
        response.sendRedirect(authUrl);
    }

    /**
     * Handles the OAuth2 callback from Withings. Exchanges the authorization code for tokens
     * and updates the bridge handler.
     */
    private void handleCallback(HttpServletRequest request, HttpServletResponse response, String code, String state)
            throws IOException {
        logger.info("Received Withings OAuth2 callback (state={})", state);

        Optional<WithingsAccountHandler> handler = getBridgeHandler(state);

        if (handler.isEmpty()) {
            logger.warn("Received callback for unknown bridge: {}", state);
            sendErrorPage(response,
                    "Unknown bridge: " + state + ". Make sure the bridge is configured and the binding is loaded.");
            return;
        }

        WithingsAccountHandler bridge = handler.get();
        String clientId = bridge.getClientId();
        String clientSecret = bridge.getClientSecret();
        String redirectUri = bridge.getRedirectUri();

        if (clientId == null || clientSecret == null || redirectUri == null) {
            sendErrorPage(response,
                    "Bridge configuration is incomplete (missing clientId, clientSecret, or redirectUri).");
            return;
        }

        // Exchange the authorization code for tokens using Withings non-standard OAuth2
        try {
            Map<String, String> params = Map.of("action", "requesttoken", "grant_type", "authorization_code",
                    "client_id", clientId, "client_secret", clientSecret, "code", code, "redirect_uri", redirectUri);

            String formBody = params.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(e.getValue(), UTF_8))
                    .collect(Collectors.joining("&"));

            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest tokenRequest = HttpRequest.newBuilder().uri(URI.create(API_TOKEN_URL))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody)).build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest,
                    HttpResponse.BodyHandlers.ofString(UTF_8));

            if (tokenResponse.statusCode() != 200) {
                String msg = "Withings token endpoint returned HTTP " + tokenResponse.statusCode();
                logger.error("{}: {}", msg, tokenResponse.body());
                sendErrorPage(response, msg);
                return;
            }

            WithingsApiResponse apiResponse = gson.fromJson(tokenResponse.body(), WithingsApiResponse.class);

            if (apiResponse == null || apiResponse.status != 0 || apiResponse.body == null) {
                int status = apiResponse != null ? apiResponse.status : -1;
                logger.error("Withings token exchange failed with status: {} - {}", status, tokenResponse.body());
                sendErrorPage(response,
                        "Withings token exchange failed (status=" + status + "). Check the logs for details.");
                return;
            }

            String accessToken = apiResponse.body.access_token;
            String refreshToken = apiResponse.body.refresh_token;
            int userId = apiResponse.body.userid;

            if (accessToken == null || refreshToken == null) {
                sendErrorPage(response, "Token exchange returned null tokens.");
                return;
            }

            logger.info("Withings OAuth2 authorization successful for bridge {} (userId={})", state, userId);

            // Pass tokens to the bridge handler
            bridge.handleAuthorizationComplete(accessToken, refreshToken);

            // Show success page
            sendSuccessPage(response, bridge, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendErrorPage(response, "Token exchange was interrupted.");
        } catch (Exception e) {
            logger.error("Failed to exchange Withings authorization code: {}", e.getMessage(), e);
            sendErrorPage(response, "Token exchange failed: " + e.getMessage());
        }
    }

    private void sendSuccessPage(HttpServletResponse response, WithingsAccountHandler bridge, int userId)
            throws IOException {
        String label = bridge.getThing().getLabel();
        StringBuilder html = new StringBuilder();
        html.append(pageHeader("Authorization Successful"));
        html.append("<div class='card success'>");
        html.append("<h1>&#10003; Authorization Successful</h1>");
        html.append("<p>The Withings account has been authorized successfully.</p>");
        html.append("<table>");
        html.append("<tr><td><strong>Bridge:</strong></td><td>").append(escapeHtml(label != null ? label : "—"))
                .append("</td></tr>");
        html.append("<tr><td><strong>User ID:</strong></td><td>").append(userId).append("</td></tr>");
        html.append("</table>");
        html.append("<p>Tokens have been saved to the bridge configuration. The bridge is being reinitialized.</p>");
        html.append("<a href='/withings' class='button'>Back to Overview</a>");
        html.append("</div>");
        html.append(pageFooter());
        response.getWriter().write(html.toString());
    }

    private void sendErrorPage(HttpServletResponse response, String message) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append(pageHeader("Authorization Error"));
        html.append("<div class='card error'>");
        html.append("<h1>&#10007; Authorization Error</h1>");
        html.append("<p>").append(escapeHtml(message)).append("</p>");
        html.append("<a href='/withings' class='button'>Back to Overview</a>");
        html.append("</div>");
        html.append(pageFooter());
        response.getWriter().write(html.toString());
    }

    private Optional<WithingsAccountHandler> getBridgeHandler(String bridgeUid) {
        for (WithingsAccountHandler handler : bridgeHandlers) {
            if (handler.getThing().getUID().getAsString().equals(bridgeUid)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String pageHeader(String title) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + title + "</title>" + "<style>"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; "
                + "max-width: 700px; margin: 40px auto; padding: 0 20px; background: #f5f5f5; color: #333; }"
                + "h1 { color: #1a73e8; } "
                + ".card { background: white; border-radius: 8px; padding: 24px; margin: 20px 0; "
                + "box-shadow: 0 1px 3px rgba(0,0,0,0.12); }" + ".card h2 { margin-top: 0; color: #333; }"
                + "table { border-collapse: collapse; margin: 12px 0; }"
                + "td { padding: 6px 16px 6px 0; vertical-align: top; }"
                + ".status-online { color: #0d904f; font-weight: bold; }"
                + ".status-offline { color: #d93025; font-weight: bold; }"
                + "button, .button { display: inline-block; background: #1a73e8; color: white; border: none; "
                + "padding: 10px 24px; border-radius: 4px; font-size: 14px; cursor: pointer; text-decoration: none; }"
                + "button:hover, .button:hover { background: #1557b0; }"
                + ".success { border-left: 4px solid #0d904f; }" + ".success h1 { color: #0d904f; }"
                + ".error { border-left: 4px solid #d93025; }" + ".error h1 { color: #d93025; }"
                + "</style></head><body>";
    }

    private static String pageFooter() {
        return "<p style='color: #999; font-size: 12px; margin-top: 40px;'>openHAB Withings Binding</p></body></html>";
    }
}
