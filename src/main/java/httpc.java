import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import java.util.*;
import java.util.List;

public class httpc {


    // httpc (get|post) [-v] (-h "k:v")* [-d inline-data] [-f file] URL

    public static void main(String[]args) {

        OptionParser parser = new OptionParser();

        parser.accepts("v", "Prints the detail of the response such as protocol, status, and headers.").withOptionalArg();
        parser.accepts("h", "Associates headers to HTTP Request with the format 'key:value'.").withRequiredArg();
        parser.accepts("d", "Associates an inline data to the body HTTP POST request.").withRequiredArg();
        parser.accepts("f", "Associates the content of a file to the body HTTP POST request.").availableUnless("d").withRequiredArg();
        parser.accepts("r", "Allow redirect.").withOptionalArg();
        parser.accepts("o", "Associates the content of a file to the body HTTP POST request.").withRequiredArg();

        OptionSet opts = parser.parse(args);

        if (args.length < 1){
            System.exit(0);
        }

        if (args[0].equals("help")) {
            System.out.println("\nhttpc is a curl-like application but supports HTTP protocol only.\n" +
                    "\nUsage:\n" +
                    " httpc command [arguments]\n" +
                    "The commands are:\n" +
                    " get executes a HTTP GET request and prints the response.\n" +
                    " post executes a HTTP POST request and prints the response.\n" +
                    " help prints this screen.\n" +
                    "Use \"httpc help [command]\" for more information about a command.\n");
        }

        if (args.length < 2){
            System.exit(0);
        }

        if (args[0].equals("help")) {
            if (args[1].equalsIgnoreCase("get")) {
                System.out.println("usage: httpc get [-v] [-r] [-o file] [-h key:value] URL\n\n" +
                        "Get executes a HTTP GET request for a given URL.\n\n");

                System.out.println("v\t\tPrints the detail of the response such as protocol, status, and headers.");
                System.out.println("h\t\tAssociates headers to HTTP Request with the format 'key:value'.");
                System.out.println("r\t\tAllow redirect. Omit for no redirect");
                System.out.println("o\t\twrite the body of the response to the specified file instead of the console. Format: hello.txt");
            }
            if (args[1].equalsIgnoreCase("post")) {
                System.out.println("usage: httpc post [-v] [-r] [-o file] [-h key:value] [-d inline-data] [-f file] URL\n\n" +
                        "Post executes a HTTP POST request for a given URL with inline data or from file.\n\n");

                System.out.println("v\t\tPrints the detail of the response such as protocol, status, and headers.");
                System.out.println("h\t\tAssociates headers to HTTP Request with the format 'key:value'.");
                System.out.println("d\t\tAssociates an inline data to the body HTTP POST request.");
                System.out.println("f\t\tAssociates the content of a file to the body HTTP POST request.");
                System.out.println("r\t\tAllow redirect. Omit for no redirect");
                System.out.println("o\t\twrite the body of the response to the specified file instead of the console. Format: hello.txt");
            }
        }

        if (args[0].equalsIgnoreCase("POST") || args[0].equalsIgnoreCase("GET")) {

            Boolean verbose = opts.has("v");
            Boolean redirect = opts.has("r");

            String outputFile="";
            if (opts.has("o")) {
                outputFile = (String) opts.valueOf("o");
            }

            String method = "";
            String URL = "";
            Map headers = new HashMap();
            String data = "";
            String file = "";

            if (args[0].equals("get")) {
                method = "GET";

                if (opts.has("h")) {
                    headers = getHeaders(opts.valuesOf("h"));
                }

            } else {
                method = "POST";

                if (opts.has("h")) {
                    headers = getHeaders(opts.valuesOf("h"));
                }
                if (opts.has("d")) {
                    data = (String) opts.valueOf("d");
                }
                if (opts.has("f")) {
                    file = (String) opts.valueOf("f");
                }
            }

            for (int x = 0; x < args.length; ++x) {
                if (args[x].startsWith("https://") || args[x].startsWith("http://")) {
                    URL = args[x];
                    break;
                }
            }

            HttpcLibrary httpcLibrary = new HttpcLibrary();
            String response = httpcLibrary.createSendRequest(method, URL, file, data, headers, verbose, redirect);
            Writer writer=null;
            if(!outputFile.isEmpty()){
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"));
                    writer.write(response);
                }catch (Exception e){
                    System.out.println("There was an error writing output to file");
                }finally {
                    try{
                        writer.close();
                    }catch (Exception e2){

                    }
                }
            }else
            {
                System.out.println(response);
            }
        }

    }

    public static Map getHeaders(List list){

        Map<String, String> headers = new HashMap();

        for (int x = 0; x<list.size(); ++x) {

            String keyValue = (String) list.get(x);
            if(keyValue.contains(":")){
                String key = keyValue.substring(0, keyValue.indexOf(":"));
                String value = keyValue.substring(keyValue.indexOf(":")+1, keyValue.length());
                headers.put(key, value);
            }
        }
        return headers;
    }
}