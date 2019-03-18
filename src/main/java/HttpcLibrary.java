import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class HttpcLibrary {

    private String USER_AGENT="Mozilla/5.0";


    public String createSendRequest(String method, String URL, String file, String data, Map headers, Boolean verbose, Boolean redirect){
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

        SocketAddress serverAddress = new InetSocketAddress(host, port);

        try(SocketChannel server = SocketChannel.open()) {

            server.connect(serverAddress);

            Charset utf8 = StandardCharsets.UTF_8;
            ByteBuffer byteBufferWrite = utf8.encode(request);

            server.write(byteBufferWrite);

            ByteBuffer byteBufferRead = ByteBuffer.allocate(1024*1024);

            for (; ; ) {
                int nr = server.read(byteBufferRead);

                if (nr == -1)
                    break;

                if (nr > 0) {

                    byteBufferRead.flip();
                    server.write(byteBufferRead);
                    byteBufferRead.clear();
                }
            }
            String temp[] = utf8.decode(byteBufferRead).toString().split("\\r\\n\\r\\n");

            responseHeader = temp[0];
            responseBody = temp[1];

            server.close();

        } catch (Exception e) {
            System.out.println("Error receiving response");

        }

        int statusCode = getStatusCode(responseHeader);

        if(statusCode >=300 && statusCode<=304 && redirect){

            String redirectLocation = getRedirectLocation(responseHeader);

            if(statusCode==304) {
                System.out.println("File has not been changed.");
                return "";
            }else{
                return createSendRequest(method, redirectLocation, file, data, headers, verbose, redirect);
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

}