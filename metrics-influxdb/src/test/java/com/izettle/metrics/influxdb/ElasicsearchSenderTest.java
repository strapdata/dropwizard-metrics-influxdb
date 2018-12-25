package com.izettle.metrics.influxdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.Test;

import com.izettle.metrics.influxdb.data.InfluxDbPoint;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ElasicsearchSenderTest {

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "This is the response";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    @Test
    public void sendPoints() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        try {
            server.createContext("/_bulk", new MyHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            ElasticsearchSender influxDbHttpSender = new ElasticsearchSender(
                "http",
                "localhost",
                8080,
                "testdb",
                "asdf",
                1000,
                1000,
                "",
                null
            );
            influxDbHttpSender.appendPoints(new InfluxDbPoint("metric1",
                    new HashMap<String,String>() {{ put("sensor","truc"); }},
                    new Date().getTime(),
                    new HashMap<String, Object>() {{ put("v", 1.01D); }}));

            influxDbHttpSender.appendPoints(new InfluxDbPoint("metric2",
                    new HashMap<String,String>() {{ put("sensor","truc"); }},
                    new Date().getTime(),
                    new HashMap<String, Object>() {{ put("v", 2.01D); }}));
            influxDbHttpSender.writeData();
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            server.stop(0);
        }
    }

    @Test(expected = UnknownHostException.class)
    public void shouldThrowUnknownHostException() throws Exception {
        ElasticsearchSender influxDbHttpSender = new ElasticsearchSender(
            "http",
            "testtestasdfg",
            80,
            "testdb",
            "asdf",
            1000,
            1000,
            "",
            null
        );
        influxDbHttpSender.writeData(new byte[0]);
    }

    @Test(expected = ConnectException.class)
    public void shouldThrowConnectException() throws Exception {
        ElasticsearchSender influxDbHttpSender = new ElasticsearchSender(
            "http",
            "localhost",
            10080,
            "testdb",
            "asdf",
            1000,
            1000,
            "",
            null
        );
        influxDbHttpSender.writeData(new byte[0]);
    }

    @Test(expected = IOException.class)
    public void shouldThrowCosnnectException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(10081), 0);
        try {
            server.createContext("/_bulk", new MyHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            ElasticsearchSender influxDbHttpSender = new ElasticsearchSender(
                "http",
                "localhost",
                10082,
                "testdb",
                "asdf",
                1000,
                1000,
                "",
                null
            );
            influxDbHttpSender.writeData(new byte[0]);
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            server.stop(0);
        }

    }

    @Test
    public void shouldNotThrowException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(10081), 0);
        try {
            server.createContext("/_bulk", new MyHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            ElasticsearchSender influxDbHttpSender = new ElasticsearchSender(
                "http",
                "localhost",
                10081,
                "testdb",
                "asdf",
                1000,
                1000,
                "",
                null
            );
            assertThat(influxDbHttpSender.writeData(new byte[0]) == 0);
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            server.stop(0);
        }
    }
}
