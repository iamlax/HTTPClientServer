import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpcLibrary {

    private String USER_AGENT="Mozilla/5.0";
    private static final Logger logger = LoggerFactory.getLogger(HttpcLibrary.class);
    private long sequenceNumberValue = 1L;


    public String createSendRequest(String method, String URL, String file, String data, Map headers, Boolean verbose, Boolean redirect, SocketAddress routerAddress, InetSocketAddress serverAddress){
        URI uri;
        try{
            uri =  new URI(URL);

        }catch (Exception e){
            System.out.println("There was en error getting the uri");
            return "";
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        String query = uri.getQuery();

        int port = uri.getPort();

        if(port == -1) {
            port = 80;
        }

        if(path==null || path.isEmpty()){
            path="/";
        }

        if(query==null){
            query="";
        }

        String request="";
        String responseHeader="";
        String responseBody="";

        if(method.equalsIgnoreCase("post")){
            request = createPostRequest(path, host, file, data, headers);
        }else{
            request = createGetRequestString(path, query, host, headers);
        }

        try{
            /* Setup 3-way handshaking
                0: Data
                1: ACK
                2: SYN
                3: SYN-ACK
                4: NAK
            */
            Packet response1 = threeWayHandshake(routerAddress, serverAddress);

            sequenceNumberValue +=1L;
            Packet response = runClient(routerAddress, serverAddress, request, 0, sequenceNumberValue);

            // ByteBuffer byteBufferRead = ByteBuffer.allocate(1024*1024);

            // String temp[] = utf8.decode(response).toString().split("\\r\\n\\r\\n");
            String payload = new String(response.getPayload(), UTF_8);

            String temp[] = payload.split("\\r\\n\\r\\n");

            responseHeader = temp[0];
            responseBody = temp[1];

        } catch (Exception e) {
            System.out.println("Error receiving response" + e);

        }

        int statusCode = getStatusCode(responseHeader);

        if(statusCode >=300 && statusCode<=304 && redirect){

            String redirectLocation = getRedirectLocation(responseHeader);

            if(statusCode==304) {
                System.out.println("File has not been changed.");
                return "";
            }else{
                return createSendRequest(method, redirectLocation, file, data, headers, verbose, redirect, routerAddress, serverAddress);
            }
        }

        if(verbose==true && !(responseHeader.isEmpty())){
            //response = response.substring(response.indexOf("{"), response.length());
            return(responseHeader.trim() + "\r\n\r\n" + responseBody.trim());
        }

        return responseBody.trim();
    }

    public int getStatusCode(String response){

        String responseLines[] = response.split("\r\n");

        int statusCode = -1;
        String pattern = "HTTP/\\d\\.\\d (\\d{3}) (.*)";

        for(String resp: responseLines ){
            if(resp.matches(pattern)) {
                statusCode = Integer.parseInt(resp.substring(resp.indexOf("HTTP/") + 9, resp.indexOf("HTTP/") + 12));
                break;
            }
        }
        return statusCode;
    }

    public String getRedirectLocation(String response){

        String responseLines[] = response.split("\r\n");

        String redirectLocation = "";
        String pattern = "Location: (.*)";

        for(String resp: responseLines ){
            if(resp.matches(pattern)) {
                redirectLocation = resp.substring(resp.indexOf("Location: ") + 9, resp.length());
                break;
            }
        }
        return redirectLocation.trim();
    }

    public String createGetRequestString(String path, String query, String host, Map<String, String> headers){

        String requestString = ("GET " + path + "?" + query + " HTTP/1.0\r\nHost: " + host + "\r\n");

        if(!headers.isEmpty()){
            requestString=addHeaders(requestString, headers);
        }

        return (requestString + "\r\n");
    }

    public String createPostRequest(String path, String host, String file, String data, Map<String, String> headers){

        String dataToAppend=data;

        if(!file.isEmpty()){
            dataToAppend = getFileData(file);
        }

        String requestString = ("POST " + path + " HTTP/1.0\r\nHost: " + host + "\r\nContent-Length: " + dataToAppend.length() + "\r\n" + "User-Agent: " + USER_AGENT + "\r\n");

        if(!headers.isEmpty()){
            requestString=addHeaders(requestString, headers);
        }

        return (requestString + "\r\n" + dataToAppend);
    }

    public String addHeaders(String requestString, Map<String, String> keyValue) {

        for (Map.Entry<String,String> pair : keyValue.entrySet()){
            requestString =(requestString + pair.getKey() + ": " +pair.getValue() + "\r\n");
        }

        return requestString;
    }

    public String getFileData(String fileUri) {

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(fileUri));

            StringBuilder stringBuilder = new StringBuilder();
            String fileLine = bufferedReader.readLine();

            while (fileLine != null) {
                stringBuilder.append(fileLine);
                fileLine = bufferedReader.readLine();
            }

            bufferedReader.close();

            return stringBuilder.toString();

        } catch (Exception e) {
            try {
                bufferedReader.close();
            } catch (Exception er) {
                System.out.println("Error Getting file data");
            }
        }

        return "";
    }

    public Packet threeWayHandshake(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {

        Packet response = null;

        try(DatagramChannel channel = DatagramChannel.open()){
            Packet p = new Packet.Builder()
                    .setType(2)
                    .setSequenceNumber(sequenceNumberValue)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(("").getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}", "", routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(10000);

            Set<SelectionKey> keys = selector.selectedKeys();

            if(keys.isEmpty()){
                logger.error("No response after timeout threeway");
                // Timer timer = new Timer();
                // timer.schedule( new threeWayHandshake(routerAddr, serverAddr, request, type, sequenceNumber), 1000);
                threeWayHandshake(routerAddr, serverAddr);
                // return response;
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", resp);
            response = resp;
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}",  payload);

            keys.clear();

            if(response.getType() == 3){
                Packet p2 = new Packet.Builder()
                        .setType(1)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(("").getBytes())
                        .create();
                channel.send(p2.toBuffer(), routerAddr);
                logger.info("Sending \"{}\" to router at {}", "", routerAddr);
            } else{
                logger.info("Received incorrect packet type {}", response.getType());
                threeWayHandshake(routerAddr, serverAddr);
            }
        }

        return response;
    }

    public Packet runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, String request, int type, long sequenceNumber) throws IOException {
        Packet response = null;

        try(DatagramChannel channel = DatagramChannel.open()){
            Packet p = new Packet.Builder()
                    .setType(type)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(request.getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}", request, routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(5000);

            Set<SelectionKey> keys = selector.selectedKeys();

            if(keys.isEmpty()){
                logger.error("No response after timeout run client");
                // Timer timer = new Timer();
                // timer.schedule( new resendPacket(routerAddr, serverAddr, request, type, sequenceNumber), 1000);
                runClient(routerAddr, serverAddr, request, type, sequenceNumber);
                // return response;
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", resp);
            response = resp;
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}",  payload);


            Packet p2 = new Packet.Builder()
                    .setType(1)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(("").getBytes())
                    .create();
            channel.send(p2.toBuffer(), routerAddr);

            keys.clear();
        }

        return response;
    }
}