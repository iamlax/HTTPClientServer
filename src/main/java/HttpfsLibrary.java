import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

public class HttpfsLibrary {

    private static final Logger logger = LoggerFactory.getLogger(HttpfsLibrary.class);

    public String display(ByteBuffer x, File folder){
        Charset utf8 = StandardCharsets.UTF_8;
        String requestFile;
        String input = utf8.decode(x).toString();

        String message[] = input.split("\r\n\r\n");
        String header = message[0];
        String body = (message.length>1? message[1] : "");

        String request[] = header.split("\r\n")[0].split(" ");
        String response = "";

        if(request[0].equalsIgnoreCase("get")){

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

        }else if(request[0].equalsIgnoreCase("post")){

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

    public void readRequestAndRepeat(SocketChannel socket, String dir) {
        try (SocketChannel client = socket) {
            ByteBuffer buf = ByteBuffer.allocate(1024);
            for (; ; ) {
                int nr = client.read(buf);

                if (nr == -1)
                    break;

                if (nr > 0) {
                    // ByteBuffer is tricky, you have to flip when switch from read to write, or vice-versa
                    buf.flip();

                    File folder = new File(dir);
                    Charset utf8 = StandardCharsets.UTF_8;

                    String response = display(buf.duplicate(), folder);
                    ByteBuffer byteBufferWrite = utf8.encode(response);
                    client.write(byteBufferWrite);
                    byteBufferWrite.clear();

                    buf.flip();
                    buf.clear();
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public void listenAndServe(int port, String dir) throws IOException {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            logger.info("HttpfsLibrary is listening at {}", server.getLocalAddress());
            for (; ; ) {
                SocketChannel client = server.accept();
                logger.info("New client from {}", client.getRemoteAddress());
                ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(client, dir));
            }
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