import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpfsLibrary {

    private static final Logger logger = LoggerFactory.getLogger(HttpfsLibrary.class);
    private long sequenceNumberValue = 1L;
    // private Timer timer = new Timer();

    public String display(String x, File folder){
        String requestFile;
        String input = x;

        String message[] = input.split("\r\n\r\n");
        String header = (message.length>0? message[0] : "");
        String body = (message.length>1? message[1] : "");

        String request[] = header.split("\r\n")[0].split(" ");
        String method = (request.length>0? request[0] : "");

        String response = "";
        if(method.equalsIgnoreCase("get")){

            String requestIgnoreQuery = request[1].substring(0, request[1].lastIndexOf("?"));

            String listFiles="";
            if(requestIgnoreQuery.equals("/")){
                String listOfFiles = listFiles(folder, listFiles);
                response = "HTTP/1.0 200 OK\r\n\r\n" + listOfFiles;
            }else if(requestIgnoreQuery.substring(0,1).equals("/")){
                requestFile = requestIgnoreQuery.substring(1);
                if(requestFile.contains("../") || requestFile.contains("..\\") || requestFile.contains("httpfs") || requestFile.contains("httpfsLibrary")){
                    response = "HTTP/1.0 403 Forbidden\r\n\r\n" + "Access to the file path is forbidden.";
                    return response;
                }
                String content = listFileContent(folder, requestFile);

                if(!content.isEmpty()){
                    String contentDisposition= createContentDisposition(folder, requestFile);
                    String contentType = createContentType(folder, requestFile);
                    response = "HTTP/1.0 200 OK" + "\r\n" + contentDisposition + "\r\n" + contentType +"\r\n\r\n" + content;
                }else{
                    response = "HTTP/1.0 404 Not Found\r\n\r\n" + "File was not found.";
                }
            }

        }else if(method.equalsIgnoreCase("post")){

            String requestIgnoreQuery = request[1];

            if(requestIgnoreQuery.equals("/")){
                response = "HTTP/1.0 403 Forbidden\r\n\r\n";
            }else if(requestIgnoreQuery.substring(0,1).equals("/")){
                requestFile = requestIgnoreQuery.substring(1);

                if(requestFile.contains("../") || requestFile.contains("..\\") || requestFile.contains("httpfs") || requestFile.contains("httpfsLibrary")){
                    response = "HTTP/1.0 403 Forbidden\r\n\r\n" + "Access to the file path is forbidden.";
                    return response;
                }

                Boolean fileWritten = writeToFile(folder, requestFile, body);

                if(fileWritten){
                    response = "HTTP/1.0 200 OK\r\n\r\n" + "File has been written to.";
                }else{
                    response = "HTTP/1.0 405 Error Writing to file\r\n\r\n" + "Writing to file failed.";
                }
            }
        }else{
            response = "HTTP/1.0 404 Not Found\r\n\r\n" + "Invalid method, supports only commands get and post";
        }

        return response;
    }

    public void readRequestAndRepeat(DatagramChannel socket, String dir) {


        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router;
                Packet packet;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */

                buf.clear();
                router = client.receive(buf);
                buf.flip();
                packet = Packet.fromBuffer(buf);
                buf.flip();
                if(packet.getSequenceNumber() == (sequenceNumberValue+1L)){
                    sequenceNumberValue+=1;
                    String payload = new String(packet.getPayload(), UTF_8);
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);
                    File folder = new File(dir);
                    String response = display(payload, folder);

                    selectiveRepeat(socket, dir, packet, router, response);
                }

            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public void threeWayHandshake(DatagramChannel socket, String dir) {

        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router;
                Packet packet;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */
                while(true){
                    buf.clear();
                    router = client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();
                    if(packet.getType() == 2){
                        sequenceNumberValue = packet.getSequenceNumber();
                        Packet resp = packet.toBuilder()
                                .setType(3)
                                .setPayload(("").getBytes())
                                .create();
                        client.send(resp.toBuffer(), router);
                        buf.clear();
                        break;
                    } else {
                        logger.info("Received incorrect packet type {}", packet.getType());
                        return;
                    }
                }

                Timer timer = new Timer();
                timer.schedule( new threeWay(socket, dir), 1000);

                while(true){
                    buf.clear();
                    client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();
                    if(packet.getType() == 1){
                        timer.cancel();
                        buf.clear();
                        break;
                    }
                }
                readRequestAndRepeat(socket, dir);
                // ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(socket, dir));
            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public void selectiveRepeat(DatagramChannel socket, String dir, Packet pack, SocketAddress route, String respon) {

        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router = route;
                Packet packet = pack;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */

                String response = respon;
                Charset utf8 = StandardCharsets.UTF_8;
                ByteBuffer byteBufferWrite = utf8.encode(response);
                Packet resp = packet.toBuilder()
                        .setType(0)
                        .setSequenceNumber(sequenceNumberValue)
                        .setPayload(byteBufferWrite.array())
                        .create();
                client.send(resp.toBuffer(), router);
                buf.clear();

                Timer timer = new Timer();
                timer.schedule( new resendPacket(socket, dir, packet, router, response), 1000);

                while(true){
                    buf.clear();
                    client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();

                    if(packet.getType() == 1 && packet.getSequenceNumber() == sequenceNumberValue){

                        buf.clear();
                        timer.cancel();
                        break;
                    }
                }
                threeWayHandshake(socket, dir);
                // ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(socket, dir));

            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    class resendPacket extends TimerTask {

        private DatagramChannel socket;
        private String dir;
        private String response;
        private Packet packet;
        private SocketAddress router;

        resendPacket(DatagramChannel socket, String dir, Packet packet, SocketAddress router, String response){
            this.socket = socket;
            this.dir = dir;
            this.packet = packet;
            this.router = router;
            this.response = response;
        }

        public void run() {
            selectiveRepeat(socket, dir, packet, router, response);
        }
    }

    class threeWay extends TimerTask {

        private DatagramChannel socket;
        private String dir;

        threeWay(DatagramChannel socket, String dir){
            this.socket = socket;
            this.dir = dir;
        }

        public void run() {
            threeWayHandshake(socket, dir);
        }
    }

    public Packet createPacket(ByteBuffer buf, int type, long sequenceNumber, String payload){

        Packet packet;
        Packet response = null;

        try{
            packet = Packet.fromBuffer(buf);
            response = packet.toBuilder()
                    .setType(type)
                    .setSequenceNumber(sequenceNumber)
                    .setPayload(payload.getBytes())
                    .create();
        }catch(Exception e){

        }
        return response;
    }

    public void listenAndServe(int port, String dir) throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("HttpfsLibrary is listening at {}", channel.getLocalAddress());
            logger.info("New client from {}", channel.getRemoteAddress());
            threeWayHandshake(channel, dir);
            // readRequestAndRepeat(channel, dir);
        } catch (Exception e) {
            logger.error("Echo error {}", e);
        }
    }

    public String createContentDisposition(File folder, String fileName){
        File file = getFile(folder, fileName);
        return ("Content-Disposition: attachment; filename=" + file.getName());
    }

    public String createContentType(File folder, String fileName){
        try{
            File file = getFile(folder, fileName);
            Path path = file.toPath();
            String contentType = Files.probeContentType(path);
            return ("Content-Type: " + contentType + "; filename=" + file.getName());

        }catch (Exception e){

        }

        return ("");
    }

    public String listFiles(File folder, String listOfFiles){
        File[] fileNames = folder.listFiles();
        for(File file : fileNames){
            if(file.isDirectory()){
                listOfFiles+= (file.getPath() + "\n");
                listOfFiles+=listFiles(file, "");
            }else{
                listOfFiles+= (file.getPath() + "\n");
            }
        }
        return listOfFiles;
    }

    public String listFileContent(File folder, String fileName) {
        File[] fileNames = folder.listFiles();
        String fileNameNoExtension ="";
        String content = "";
        for (File file : fileNames) {

            if(file.isDirectory()){
                listFileContent(file, fileName);
            }else{
                fileNameNoExtension = file.getName();
                if (fileNameNoExtension.indexOf(".") > 0) {
                    fileNameNoExtension = fileNameNoExtension.substring(0, fileNameNoExtension.indexOf('.'));
                }

                if (fileNameNoExtension.equalsIgnoreCase(fileName)) {
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String strLine;
                        content += ("File: " + file.getName() + "\n");
                        while ((strLine = br.readLine()) != null) {
                            content += (strLine + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        return content;
    }

    public File getFile(File folder, String fileName) {
        File[] fileNames = folder.listFiles();
        String fileNameNoExtension ="";
        File tempFile = null;
        for (File file : fileNames) {

            if(file.isDirectory()){
                listFileContent(file, fileName);
            }else{
                fileNameNoExtension = file.getName();
                if (fileNameNoExtension.indexOf(".") > 0) {
                    fileNameNoExtension = fileNameNoExtension.substring(0, fileNameNoExtension.indexOf('.'));
                }

                if (fileNameNoExtension.equalsIgnoreCase(fileName)) {
                    tempFile = file;
                }
            }
        }

        return tempFile;
    }

    public Boolean writeToFile(File folder, String fileName, String data){
        FileWriter fileWriter = null;
        Boolean success = false;
        String folderPath = folder.getPath().replace("\\", "\\\\");

        try{
            fileWriter = new FileWriter(folderPath + "\\" + fileName, false);
            fileWriter.write(data);
            success =true;
        }catch (IOException e){

        }finally{
            try{
                fileWriter.close();

            }catch (IOException e) {
            }

        }
        return success;
    }
}