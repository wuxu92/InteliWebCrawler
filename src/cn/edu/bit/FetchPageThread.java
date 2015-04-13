package cn.edu.bit;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by wuxu92 on 3/25/2015.
 * class fetch one page's content
 */
public class FetchPageThread implements Runnable{

    /**
     * the very first url to start the crawler
     */
    private String initUrl;

    private boolean isNewParserTread = true;

    /**
     * the size of the waiting blocking queue
     * set to 100 as const
     */
    public static final int URL_QUEUE_SIZE = 3000;

    /**
     * decrease the limit of pages buffer to 20
     * for net fetching is much more slower than page parser
     */
    public static final int PAGE_QUEUE_SIZE = 20;

    /**
     * url blocking queue, use blockingQueue class to implements concurrency
     * void the usage of await and notify calls
     */
    private BlockingQueue<String> urlQueue = new ArrayBlockingQueue<String>(URL_QUEUE_SIZE);
    private BlockingQueue<String> pageQueue = new ArrayBlockingQueue<String>(PAGE_QUEUE_SIZE);

    public FetchPageThread(String initUrl) {
        this.initUrl = initUrl;
        System.out.println("init as " + initUrl);
        try {
            urlQueue.put(initUrl);
        } catch (InterruptedException e) {
            System.out.println("wait");
        }
    }

    @Override
    public void run() {
        String url = null;
        Main.currentThreadNum++;
        System.out.println("new thread starting : " + Thread.currentThread().getName());
        try {
            url = urlQueue.take();
        } catch (InterruptedException e) {
            System.out.println("url queue take error " + e.getMessage());
        }

        while (url != null) {
            try {

                System.out.println("fetching " + url);

                /**
                 * add url to the first line of the pageStr
                 * @date 2015-04-01 22:50
                 */
                String pageStr = "" + url + System.getProperty("line.separator") + fetchOnePage(url);
                // if it is to short, drop it
                if (pageStr.length() <= 5000 && pageStr.length() > 5*1000*1000) {
                    System.out.println("page no content or too big" + pageStr.length());
                    continue;
                }

                // use new thread to parseHTML
                // one fetch thread with one parse thread
                pageQueue.put(pageStr);
                if (this.isNewParserTread) {
                    new Thread(new HtmlParserThread(pageQueue, urlQueue)).start();
                    this.isNewParserTread = false;
                }

                Main.pageSize++;
                if (Main.pageSize % 500 == 0) System.out.println("fetching success no: " + Main.pageSize);
                if (Main.pageSize > Main.FULL_PAGE_SIZE ) break;

                // sleep
                // wait(100);
                // Thread.sleep(100);
            } catch (InterruptedException e) {
                // e.printStackTrace();
                System.out.println("InterruptedException " + " " +  e.getMessage());
            } finally {
                try {
                    url = urlQueue.take();
                    // if thread size not full, then start a new thread
                    if (Main.currentThreadNum < Main.THREAD_SIZE) {
                        new Thread(new FetchPageThread(url)).start();
                    }
                } catch (InterruptedException e) {
                    System.out.println("url queue take error " + e.getMessage());
                }
            }

        }

        System.out.println("end of thread :" + Thread.currentThread().getName());
    }

    /**
     * fetch one page as string from a url
     * @param urlStr String
     * @return String
     */
    public static String fetchOnePage(String urlStr) {

        URL url;
        /**
         * @date 2015-04-01 add proxy
         */
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.4.20.2", 3128));

        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            System.out.println("Param::" + urlStr + " is not a good url string.");
            return "";
        }

        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection)url.openConnection(proxy);
        } catch (IOException e) {
            System.out.println("open connection error " + e.getMessage());
            return "";
        }
        if (conn == null) {
            throw new IllegalArgumentException("url protocol must be http or proxy server error");
        }

        // set connect and read timeout both to just 1 second
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-agent", "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.101 Safari/537.36");

        // send request
        try {
            conn.connect();
        } catch (IOException e) {
            System.out.println("conn connect error " + e.getMessage());
            return "";
        }
        Map<String, List<String>> headers = conn.getHeaderFields();
        // int resCode = conn.getResponseCode();
        int contentLength = conn.getContentLength();

        // get charset
        String resType = conn.getContentType();
        String charset = null;
        if (resType != null) {
            if (!resType.contains("text")) {
                System.out.println("res content type error: " + resType);
                return "";
            }
            int charsetIndex = resType.indexOf("charset=");
            if (charsetIndex != -1) {
                int nextCommaIndex = resType.indexOf(";", charsetIndex);
                if (nextCommaIndex != -1) charset = resType.substring(charsetIndex + 8, resType.indexOf(";", charsetIndex));
                else charset = resType.substring(charsetIndex + 8);
            }
        }
        InputStream res = null;
        try {
            res = conn.getInputStream();
        } catch (IOException e) {
            System.out.println("res getInputStream error " + e.getMessage());
            return "";
        }
        // build string
        // System.out.println(res.toString());
        StringBuilder pageStrBuilder =  (readFromStreamByLine(res));
        // if (contentLength != -1) pageStrBuilder = new StringBuilder(readBytesFromStream(res, contentLength, charset));
        // else {
        // pageStrBuilder = (readFromStreamByLine(res));
        
        // System.out.println("pageStr length: " + pageStrBuilder.length());
        // }

        // get head and remove it
        /**
         * head tag handle
         * remove for now
         */
        // int indexOfHead = pageStrBuilder.indexOf("</head>");
        // StringBuilder headBuilder = pageStrBuilder.delete(0, indexOfHead + 7);

        // get all meta tags
        // Map<String, String> metaTags = getMetaMap(headBuilder);


        // remove all style/script tag and return
        // String trimedStr = trimExtraTag(pageStrBuilder);
        String trimedStr = new String(pageStrBuilder);
        if (charset != null) {
            byte[] strBytes = trimedStr.getBytes();
            Charset cs = Charset.forName(charset);
            return new String(strBytes, cs);
        } else {
            return trimedStr;
        }
    }

    /**
     * read page's content
     * @param is InputStream
     * @param len int
     * @param charset String
     * @return String
     * @throws IOException
     */
    public static String readBytesFromStream(InputStream is, int len, String charset) throws IOException {
        // int bufLen = Math.max(1024, Math.max(len, is.available()));
        // byte[] buff = new byte[bufLen];

        // simple read all to byte[]
        // @todo use buffer
        byte[] bytes = new byte[len];
        int readLen = is.read(bytes);

        if (readLen == -1) return "";

        if (charset != null) {
            return new String(bytes, Charset.forName(charset));
        } else {
            return new String(bytes);
        }

    }


    /**
     * read by line if length is not available
     * @param is InputStream
     * @return StringBuilder
     */
    public static StringBuilder readFromStreamByLine(InputStream is) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = br.readLine() ) != null) {
                sb.append(line);
                // break it if file is too big
                if (sb.length() > 5 * 1000 * 1000) break;
                // System.out.println("line: " + line);
            }
        } catch (IOException e) {
            System.out.println("read line error");
        }
        // System.out.println("read finish:" + sb.length());

        return sb;
    }

    /**
     * get header's meta info as map
     * key as meta tag's name
     * value as meta tag's content
     * @param header String
     * @return Map
     */
    public static Map<String, String> getMetaMap(StringBuilder header) {
        StringBuilder name;
        StringBuilder content;

        // map to save the meta info for return
        Map<String, String> metas = new HashMap<String, String>();

        for (int metaIndex = header.indexOf("<meta"); metaIndex != -1; ) {
            String metaStr = header.substring(metaIndex, header.indexOf(">", metaIndex));

            // find name attribute
            int nameIndex = metaStr.indexOf("name=");
            int contentIndex = metaStr.indexOf("content=");

            // if no name|content attributes in this meta tag, then continue
            if (nameIndex == -1 || contentIndex == -1) continue;

            int len = metaStr.length();
            name = new StringBuilder("");
            content = new StringBuilder("");

            // step by step, search for the end of the name and content
            for ( ; nameIndex <= len; nameIndex++) {
                char currChar = metaStr.charAt(nameIndex);
                if ('"' != currChar) {
                    name.append(metaStr.charAt(nameIndex));
                }
                else break;
            }
            for (; contentIndex <= len; contentIndex++ ) {
                char currChar = metaStr.charAt(contentIndex);
                if ('"' != currChar) {
                    content.append(currChar);
                }
            }
            metas.put(name.toString(), content.toString());
        }
        return metas;
    }

    public static String trimExtraTag(StringBuilder pageStrBuilder) {
        // remote all style&script node
        for ( int cssIndex = pageStrBuilder.indexOf("<style"); cssIndex != -1; ) {
            int cssEndIndex = pageStrBuilder.indexOf("</style>", cssIndex);
            if (cssEndIndex == -1) break;
            pageStrBuilder.delete(cssIndex, cssEndIndex+8);
            cssIndex = pageStrBuilder.indexOf("<style");
        }
        // remote all style&script node
        for ( int jsIndex = pageStrBuilder.indexOf("<script"); jsIndex != -1; ) {
            int jsEndIndex = pageStrBuilder.indexOf("</script>", jsIndex);
            if (jsEndIndex == -1) break;
            pageStrBuilder.delete(jsIndex, jsEndIndex+9);
            jsIndex = pageStrBuilder.indexOf("<script");
        }

        return new String(pageStrBuilder);
    }
}
