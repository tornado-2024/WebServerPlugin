package my.plugin;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.Player;
import cn.nukkit.item.Item;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import cn.nukkit.utils.Config;
import com.google.gson.Gson;

public class WebServerPlugin extends PluginBase {

    private int port = 8080;
    private HttpServer server;
    private List<Route> routes = new ArrayList<>();
    private Set<String> allowedKeys = new HashSet<>();
    private final Gson gson = new Gson();

    @Override
    public void onEnable() {
        port = getConfig().getInt("server.port", 8080);
        loadRoutes();
        loadKeys();
        startServer();
    }

    private void startServer() {
        if (server != null) return;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", exchange -> {
                String reqPath = exchange.getRequestURI().getPath();

                Route matchedRoute = findRouteForPath(reqPath);
                if (matchedRoute == null) {
                    sendJsonResponse(exchange, 404, "error", "Route not found");
                    return;
                }

                String method = exchange.getRequestMethod();
                if (!matchedRoute.allow.contains(method.toUpperCase())) {
                    sendJsonResponse(exchange, 405, "error", "Method " + method + " not allowed");
                    return;
                }

                if (matchedRoute.auth) {
                    if (!checkAuth(exchange, matchedRoute)) {
                        sendJsonResponse(exchange, 401, "error", "Unauthorized");
                        return;
                    }
                }

                if (matchedRoute.path.equals("/server/console/execute")) {
                    handleConsoleExecute(exchange);
                    return;
                }
                if (matchedRoute.path.equals("/api/players")) {
                    handlePlayersList(exchange);
                    return;
                }
                if (matchedRoute.path.equals("/api/player/inventory")) {
                    handlePlayerInventory(exchange);
                    return;
                }
                if (matchedRoute.path.equals("/api/player/isadmin")) {
                    handleIsAdmin(exchange);
                    return;
                }

                serveFile(exchange, matchedRoute, reqPath);
            });

            server.setExecutor(null);
            server.start();
            getLogger().info("WebServer started on port " + port);

        } catch (Exception e) {
            getLogger().error("Failed to start WebServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            getLogger().info("WebServer stopped");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("webserver")) {
            if (args.length == 0) {
                sender.sendMessage("Использование: /webserver on|off");
                return true;
            }
            if ("on".equalsIgnoreCase(args[0])) {
                startServer();
                sender.sendMessage("WebServer включён!");
                return true;
            } else if ("off".equalsIgnoreCase(args[0])) {
                stopServer();
                sender.sendMessage("WebServer выключен!");
                return true;
            } else {
                sender.sendMessage("Использование: /webserver on|off");
                return true;
            }
        }
        return false;
    }

    private void loadRoutes() {
        try {
            File routesFile = new File(getDataFolder(), "routes.yml");
            if (!routesFile.exists()) saveResource("routes.yml", false);

            Config config = new Config(routesFile, Config.YAML);
            List<Map> routesList = config.getMapList("routes");
            if (routesList == null) return;

            for (Map<?, ?> routeMap : routesList) {
                String path = (String) routeMap.get("path");
                String dir = (String) routeMap.get("dir");
                String index = (String) routeMap.get("index");
                List<String> allow = (List<String>) routeMap.get("allow");
                Boolean auth = (Boolean) routeMap.get("auth");
                String key = (String) routeMap.get("key");

                if (path == null || allow == null) continue;

                Route route = new Route();
                route.path = path;
                route.dir = dir != null ? dir : "";
                route.index = index;
                route.allow = allow.stream().map(String::toUpperCase).collect(Collectors.toList());
                route.auth = auth != null && auth;
                route.key = key;

                routes.add(route);
            }
        } catch (Exception e) {
            getLogger().error("Failed to load routes.yml: " + e.getMessage());
        }
    }

    private void loadKeys() {
        try {
            File keysFile = new File(getDataFolder(), "keys.yml");
            if (!keysFile.exists()) saveResource("keys.yml", false);

            Config config = new Config(keysFile, Config.YAML);
            List<String> keys = config.getStringList("keys");
            if (keys != null) {
                allowedKeys.addAll(keys);
            }
        } catch (Exception e) {
            getLogger().error("Failed to load keys.yml: " + e.getMessage());
        }
    }

    private boolean checkAuth(HttpExchange exchange, Route route) {
        List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
        String key = null;

        if (authHeaders != null) {
            for (String header : authHeaders) {
                if (header.startsWith("Bearer ")) {
                    key = header.substring(7).trim();
                    break;
                }
            }
        }

        if (key == null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("key=")) {
                        key = param.substring(4);
                        break;
                    }
                }
            }
        }

        if (route.auth) {
            if (key == null) return false;

            if (route.key != null) {
                return route.key.equals(key);
            }

            return allowedKeys.contains(key);
        }

        return true;
    }

    private Route findRouteForPath(String path) {
        Route bestMatch = null;
        for (Route r : routes) {
            if (path.equals(r.path) || path.startsWith(r.path + "/") || (r.path.equals("/") && !path.isEmpty())) {
                if (bestMatch == null || r.path.length() > bestMatch.path.length()) {
                    bestMatch = r;
                }
            }
        }
        return bestMatch;
    }

    private void sendJsonResponse(HttpExchange exchange, int status, Object content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        String json = content instanceof String
                ? String.format("{\"status\":\"%s\"}", content)
                : gson.toJson(content);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String key, String message) throws IOException {
        Map<String, String> response = new HashMap<>();
        response.put(key, message);
        sendJsonResponse(exchange, status, response);
    }

    private void handleConsoleExecute(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();

        if (query == null) {
            sendJsonResponse(exchange, 400, "error", "Отсутствует параметр ?command=...");
            return;
        }

        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            int idx = param.indexOf('=');
            if (idx > 0) {
                String key = param.substring(0, idx);
                String value = param.substring(idx + 1);
                params.put(key, URLDecoder.decode(value, StandardCharsets.UTF_8.toString()));
            }
        }

        String command = params.get("command");
        if (command == null || command.isEmpty()) {
            sendJsonResponse(exchange, 400, "error", "Отсутствует параметр command");
            return;
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();

        PrintStream out = new PrintStream(outStream);
        PrintStream err = new PrintStream(errStream);

        try {
            System.setOut(out);
            System.setErr(err);

            boolean success = getServer().dispatchCommand(getServer().getConsoleSender(), command);

            out.flush();
            err.flush();

            String output = outStream.toString(StandardCharsets.UTF_8.name()).trim();
            String error = errStream.toString(StandardCharsets.UTF_8.name()).trim();

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("command", command);
            response.put("output", output.isEmpty() ? null : output);
            response.put("error", error.isEmpty() ? null : error);

            sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "error", "Ошибка выполнения: " + e.getMessage());
        } finally {
            System.setOut(System.out);
            System.setErr(System.err);
        }
    }

    private void handlePlayersList(HttpExchange exchange) throws IOException {
        List<String> playerNames = getServer().getOnlinePlayers().values().stream()
                .map(player -> player.getName())
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", playerNames.size());
        response.put("players", playerNames);

        sendJsonResponse(exchange, 200, response);
    }

    private void handlePlayerInventory(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            sendJsonResponse(exchange, 400, "error", "Отсутствует параметр name");
            return;
        }

        String playerName = null;
        for (String param : query.split("&")) {
            if (param.startsWith("name=")) {
                playerName = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8.toString());
                break;
            }
        }

        if (playerName == null || playerName.isEmpty()) {
            sendJsonResponse(exchange, 400, "error", "Параметр name не указан");
            return;
        }

        Player player = getServer().getPlayer(playerName);
        if (player == null) {
            sendJsonResponse(exchange, 404, "error", "Игрок не найден");
            return;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            Item item = player.getInventory().getItem(i);
            if (!item.isNull()) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("slot", i);
                itemData.put("id", item.getId());
                itemData.put("damage", item.getDamage());
                itemData.put("count", item.getCount());
                items.add(itemData);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("player", playerName);
        response.put("inventory", items);

        sendJsonResponse(exchange, 200, response);
    }

    private void handleIsAdmin(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            sendJsonResponse(exchange, 400, "error", "Отсутствует параметр name");
            return;
        }

        String playerName = null;
        for (String param : query.split("&")) {
            if (param.startsWith("name=")) {
                playerName = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8.toString());
                break;
            }
        }

        if (playerName == null || playerName.isEmpty()) {
            sendJsonResponse(exchange, 400, "error", "Параметр name не указан");
            return;
        }

        Player player = getServer().getPlayer(playerName);
        boolean isAdmin = (player != null) && player.isOp();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("player", playerName);
        response.put("isAdmin", isAdmin);

        sendJsonResponse(exchange, 200, response);
    }

    private void serveFile(HttpExchange exchange, Route route, String reqPath) throws IOException {
        String relativePath = reqPath.substring(route.path.length());
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            if (route.index != null) {
                relativePath = "/" + route.index;
            } else {
                sendJsonResponse(exchange, 404, "error", "Index file not defined for " + route.path);
                return;
            }
        }

        File baseDir = new File(getDataFolder(), route.dir);
        File target = new File(baseDir, relativePath);
        if (!target.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
            sendJsonResponse(exchange, 404, "error", "Access denied");
            return;
        }

        if (!target.exists() || target.isDirectory()) {
            sendJsonResponse(exchange, 404, "error", "File not found: " + relativePath);
            return;
        }

        String mime = getMimeType(target.getName());
        byte[] content = Files.readAllBytes(target.toPath());

        exchange.getResponseHeaders().add("Content-Type", mime);
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".html") || filename.endsWith(".htm"))
            return "text/html; charset=utf-8";
        if (filename.endsWith(".css"))
            return "text/css; charset=utf-8";
        if (filename.endsWith(".js"))
            return "application/javascript; charset=utf-8";
        if (filename.endsWith(".png"))
            return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))
            return "image/jpeg";
        if (filename.endsWith(".gif"))
            return "image/gif";
        if (filename.endsWith(".svg"))
            return "image/svg+xml";
        return "text/plain; charset=utf-8";
    }

    static class Route {
        String path;
        String dir;
        String index;
        List<String> allow;
        boolean auth;
        String key;
    }
}

