package ru.kotokatalog;

import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ServerMain {
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
            var catsHtml = new StringBuilder();
            try (var statement = conn.createStatement()) {
                statement.execute("select name from cat");
                var rs = statement.getResultSet();
                while (rs.next()) {
                    var name = rs.getString(1);
                    catsHtml.append("<p>");
                    catsHtml.append(name);
                    catsHtml.append("</p>\n");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.exit(1);
            }
            String html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<body>\n" +
                    "<h1>Котокаталог</h1>\n" +
                    catsHtml +
                    "<a href=\"/add\">добавить кота</a>\n" +
                    "</body>\n" +
                    "</html>\n";
            byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            exchange.getResponseBody().write(htmlBytes);
            exchange.close();
        });
        server.createContext("/add", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>

                    <form action="/add" method="post">
                      <label for="name">Имя:</label><br>
                      <input type="text" id="name" name="name" value=""><br>
                      <input type="submit" value="Добавить">
                    </form>
                    </body>
                    </html>""";
            byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
            if (exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                exchange.getResponseBody().write(htmlBytes);
            } else {
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
}
