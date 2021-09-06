package my.main;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import my.Static.Static;
import my.Threads.GetUserInfo;
import my.Threads.GetUserUrl;
import my.Threads.HandleTopic;



public class Main {
    
    public static void getTopicID() throws ClientProtocolException, IOException, InterruptedException {
        HttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet("https://www.zhihu.com/topics");
        CloseableHttpResponse response = (CloseableHttpResponse) client.execute(get);


        HttpEntity enity = response.getEntity();
        String body = EntityUtils.toString(enity, "UTF-8");

        String regex = "data-id=\"[0-9]{0,6}\"";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(body);

        while (m.find()) {
            // System.out.println(m.group());
            // System.out.println(m.start()+"->"+m.end());
            String s = m.group();
            System.out.println(s.substring(9, s.length() - 1));
            Static.topicID.add(m.group().substring(9, s.length() - 1));
        }

        response.close();
        EntityUtils.consume(enity);

    }

    public static void getAllSubTopic() throws InterruptedException, SQLException, IOException {


        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(2);
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(200);
        CloseableHttpClient httpClient = HttpClients.custom().setRetryHandler(new DefaultHttpRequestRetryHandler())// ��������ʱ�����Դ���
                .setConnectionManager(cm).build();

        System.out.println("--------------- " + Static.topicID.size() + "--------------- ");
        int len = Static.topicID.size();
        for (int i = 0; i < len; i++) {
            String url = "https://www.zhihu.com/node/TopicsPlazzaListV2";
            HttpPost httppost = new HttpPost(url);

            fixedThreadPool.execute(new HandleTopic(httpClient, httppost, Static.topicID.poll()));
            httppost.releaseConnection();
            System.out.println(i + "---------------------");

        }


        fixedThreadPool.shutdown();

        while (true) {

            if (fixedThreadPool.isTerminated()) {
                httpClient.close();
                System.out.println(Static.topicID.size());
                System.out.println(
                        "���е����̶߳������� ��ȡsecondTopicId����Ϊ: "
                                + Static.SecondtopicID.size() + "\n");
                break;
            }
            Thread.sleep(1000);
        }

    }

    public static void getAllUserUrl() throws InterruptedException, SQLException, IOException {


        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(5);
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(200);



        CloseableHttpClient httpClient = HttpClients.custom()
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .setConnectionManager(cm).build();

        System.out.println(Static.SecondtopicID.size());

        int len = Static.SecondtopicID.size();
        for (int i = 0; i < len; i++) {
            try {

                HttpPost httppost = new HttpPost(
                        "https://www.zhihu.com/topic/" + Static.SecondtopicID.take() + "/followers");
                System.out.println(httppost.getURI());
                fixedThreadPool.execute(new GetUserUrl(httpClient, httppost));
                httppost.releaseConnection();

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        fixedThreadPool.shutdown();
        while (true) {
            System.out.println("queue ..........................." + Static.SecondtopicID.size());
            System.out.println("Map size.........." + Static.map.size());

            if (fixedThreadPool.isTerminated()) {
                httpClient.close();
                System.out.println("���е����̶߳������ˣ�" + Static.SecondtopicID.size());


            }
            Thread.sleep(1000);
        }

    }

    public static void getFromDB() throws SQLException {
        Connection conn = Static.getConn();
        java.sql.Statement s = conn.createStatement();

        String sql = "SELECT * FROM userurl";


        ResultSet rs = s.executeQuery(sql);
        int i = 0;
        while (rs.next()) {

            String url = rs.getString("url");
            i++;

            Static.userurl.add(url);
            System.out.println(url);
        }
        System.out.println("��543ms" + i);
    }

    public static void getAllUser() throws InterruptedException, SQLException, IOException {

        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(200);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setRetryHandler(new DefaultHttpRequestRetryHandler())// ��������ʱ�����Դ���Ĭ��3��
                .setConnectionManager(cm).build();

        // �����ݿ����ó�
        getFromDB();
        int len = Static.userurl.size();

        System.out.println(len);
        for (int i = 0; i < len; i++) {
            String userurl = "https://www.zhihu.com/people/" + Static.userurl.take() + "/about";
            HttpGet httpget = new HttpGet(userurl);
            fixedThreadPool.execute(new GetUserInfo(httpClient, httpget));
            httpget.releaseConnection();
        }

        fixedThreadPool.shutdown();
        while (true) {

            if (fixedThreadPool.isTerminated()) {
                httpClient.close();
                System.out.println("���е��̶߳������ˣ�");
                break;
            }
            Thread.sleep(1000);
        }

    }

    public static void main(String[] args) {

        try {
            long t1 = System.currentTimeMillis();
            getTopicID();
            getAllSubTopic();

            //ע:userurl�����������ݿ����ܵ���getAllUser();
            getAllUserUrl();
            //getAllUser();
            long t2 = System.currentTimeMillis();
            System.out.println("����" + (t2 - t1) + "ms");
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

}
