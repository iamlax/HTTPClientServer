import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class httpfs {

    public static void main(String[]args) {

        OptionParser parser = new OptionParser();
        parser.accepts("p", "Listening port").withOptionalArg().defaultsTo("8080");
        parser.accepts("d", "Path to dir.").withRequiredArg().defaultsTo(".");
        parser.accepts("v", "Prints the detail of the response such as protocol, status, and headers.").withOptionalArg();

        OptionSet opts = parser.parse(args);

        if (args[0].equals("help")) {
            System.out.println("");
            System.out.println("httpfs is a simple file server.");
            System.out.println("Usage: httpfs get [-v] [-p PORT] [-d PATH-TO-DIR]\n\n");
            System.out.println("-v\t\tPrints debugging messages.");
            System.out.println("-v\t\tSpecifies the port number that the server will listen and serve at. Default is 8080.");
            System.out.println("-d\t\tSpecifies the directory that the server will use to read/write requested files. " +
                    "Default is the current directory when launching the application.");
        }

        int port = Integer.parseInt((String) opts.valueOf("p"));
        String dirPath = (String) opts.valueOf("d");
        Boolean verbose = opts.has("v");

        try{
            HttpfsLibrary server = new HttpfsLibrary();
            server.listenAndServe(port, dirPath);
        }catch(Exception e){
            System.out.println("Error while listen and serve: " + e);
        }
    }
}