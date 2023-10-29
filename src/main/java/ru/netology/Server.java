package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
   private static final int PORT = 9999;
   private static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
           "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
   ServerSocket serverSocket;
   Socket clientSocket;
   private Map<String, Handler> getHandlers;
   private Map<String, Handler> postHandlers;

   public Server() {
      getHandlers = new HashMap<>();
      postHandlers = new HashMap<>();
   }

   public void addHandler(String method, String path, Handler handler) {
      if (method.equals("GET") && validPaths.contains(path)) {
         getHandlers.put(path, handler);
         System.out.println("> GET-handler added");
      } else if (method.equals("POST") && validPaths.contains(path)) {
         postHandlers.put(path, handler);
         System.out.println("> POST-handler added");
      }
   }

   public void start() throws IOException {
      serverSocket = new ServerSocket(PORT);
      System.out.println("Server started at port "+ PORT);
      ExecutorService threadPool = Executors.newFixedThreadPool(64);

      while (true) {
         clientSocket = serverSocket.accept();
         System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
         threadPool.submit(() -> {
            try {
               handleRequest(clientSocket);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }
   }

   private void handleRequest(Socket socket) throws IOException {
      while (true) {
         try (
                 final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 final var out = new BufferedOutputStream(socket.getOutputStream());
         ) {
            int c;
            StringBuilder wholeRequest = new StringBuilder();
            System.out.println("> reading request...");
            while((c=in.read()) != -1) {
               wholeRequest.append((char) c);
            }
            System.out.println("> wholeRequest:\n\n" + wholeRequest);

            Request currentRequest = parseHttpRequest(wholeRequest.toString());

            if(currentRequest.getMethod().equals("GET")
                    && getHandlers.containsKey(currentRequest.getPath())
                    //&& validPaths.contains(currentRequest.getPath())
            ) {
               Handler handler = getHandlers.get(currentRequest.getPath());
               handler.handle(currentRequest, out);

            } else if (currentRequest.getMethod().equals("POST")
                    && postHandlers.containsKey(currentRequest.getPath())
                    //&& validPaths.contains(currentRequest.getPath())
            ) {
               Handler handler = postHandlers.get(currentRequest.getPath());
               handler.handle(currentRequest, out);

            } else {
               System.out.println("> 404");
               out.write(("HTTP/1.1 404 Not Found\r\n" +
                       "Content-Length: 0\r\n" +
                       "Connection: close\r\n" +
                       "\r\n").getBytes());
            }

            out.flush();
            socket.close();
         }//try-with-res
      }//while
   }//handleRequest

   public static Request parseHttpRequest(String httpRequest) {
      System.out.println("> parsing req started...");
      Request request = new Request();
      String[] lines = httpRequest.split("\n");

      // Method, path
      String[] firstLineTokens = lines[0].split(" ");
      request.setMethod(firstLineTokens[0]);
      request.setPath(firstLineTokens[1]);

      // Headers
      Map<String, String> headers = new HashMap<>();
      for (int i = 1; i < lines.length; i++) {
         String line = lines[i];

         // Body
         if (line.isEmpty()) {
            StringBuilder body = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
               body.append(lines[j]);
            }
            request.setBody(body.toString());
            break;
         } else {
            String[] headerTokens = line.split(": ");
            headers.put(headerTokens[0], headerTokens[1]);
         }
      }
      request.setHeaders(headers);
      return request;
   }

}//Server