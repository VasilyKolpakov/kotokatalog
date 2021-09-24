package ru.kotokatalog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.stream.Collectors;

public class ServerMain {
    static {
        // init Velocity
        Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        properties.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        Velocity.init(properties);
    }

    public static void main(String[] args) throws IOException, SQLException {
        final String dbFile;
        if (args.length > 0) {
            dbFile = args[0];
        } else {
            dbFile = "kotokatalog.db";
        }
        var conn = DriverManager.getConnection("jdbc:sqlite:%1$s".formatted(dbFile));
        try (var statement = conn.createStatement()) {
            statement.execute("create table if not exists cat (name text)");
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
        InetSocketAddress socketAddress = new InetSocketAddress("localhost", 8080);
        var server = HttpServer.create(socketAddress, 10);
        server.createContext("/", exchange -> {
            var cats = new ArrayList<String>();
            try (var statement = conn.createStatement()) {
                statement.execute("select name from cat");
                var rs = statement.getResultSet();
                while (rs.next()) {
                    var name = rs.getString(1);
                    cats.add(name);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.exit(1);
            }
            byte[] htmlBytes = renderIndexPage(cats);
            exchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            exchange.getResponseBody().write(htmlBytes);
            exchange.close();
        });
        server.createContext("/add", exchange -> {
            var htmlStream = ServerMain.class.getClassLoader().getResourceAsStream("add_cat.html");
            assert htmlStream != null;
            byte[] htmlBytes = readStreamAsBytes(htmlStream);
            if (exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                exchange.getResponseBody().write(htmlBytes);
            } else {
                HashMap<String, String> queryParams = parseQueryParameters(exchange);
                try (var statement = conn.prepareStatement("insert into cat values (?)")) {
                    statement.setString(1, queryParams.get("name"));
                    statement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(300, 0);
            }
            exchange.close();
        });
        server.start();
        System.out.printf("http://%s:%d/%n", socketAddress.getHostName(), socketAddress.getPort());
    }

    private static HashMap<String, String> parseQueryParameters(HttpExchange exchange) {
        var requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines()
                .collect(Collectors.joining());
        var queryParams = new HashMap<String, String>();
        for (var paramString : requestBody.split("&")) {
            var nameAndValue = paramString.split("=");
            queryParams.put(
                    URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8)
            );
        }
        return queryParams;
    }

    private static byte[] renderIndexPage(ArrayList<String> cats) {
        VelocityContext context = new VelocityContext();
        context.put("cats", cats);
        var template = Velocity.getTemplate("index.v.html");
        StringWriter sw = new StringWriter();
        template.merge(context, sw);
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] readStreamAsBytes(InputStream src) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.transferTo(out);
        return out.toByteArray();
    }
}
