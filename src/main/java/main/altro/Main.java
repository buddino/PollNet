package main.altro;

import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    public final static void main(String[] args) throws IOException, ParseException {
        if (args.length != 3) {
            System.err.println("PollNet.jar path/filename.js accessURL dataURL");
            System.exit(500);
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.mm.YYYY");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(args[1]);
        HttpClientContext context = HttpClientContext.create();
        CloseableHttpResponse resp = httpclient.execute(httpGet, context);
        CookieStore cookieStore = context.getCookieStore();
        httpGet = new HttpGet(args[2]);
        HttpClientContext context1 = HttpClientContext.create();
        context1.setCookieStore(cookieStore);
        CloseableHttpResponse resp2 = httpclient.execute(httpGet, context1);
        String respondeBody = EntityUtils.toString(resp2.getEntity(), "UTF-8");
        Document doc = Jsoup.parse(respondeBody, "UTF-8");
        String dateString = doc.getElementById("gcUpdate").text().replace("Ultimo aggiornamento: ", "");
        String stationString = doc.getElementById("gcStation").ownText().replaceAll(".*-\\s", "");
        Date date = simpleDateFormat.parse(dateString);
        Elements values = doc.getElementsByClass("valori").get(0).getElementsByTag("tr");
        Map<String, String> family2idMaps = new HashMap<String, String>();
        Map<String, List<Double>> familyMaps = new HashMap<String, List<Double>>();
        Map<String, String> trendMap = new HashMap<String, String>();
        Map<String, List<String>> concentrazioneMap = new HashMap<String, List<String>>();
        NumberFormat format = NumberFormat.getInstance(Locale.ITALY);
        for (Element tr : values) {
            for (Element c : tr.select(".genere,.famiglia")) {
                String trend = tr.select(".tendenz img").attr("src").replace("img/", "").replace(".gif", "");
                family2idMaps.put(c.id().split("_")[1], c.text());
                List<Double> valueList = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                List<String> concentrazioneList = Arrays.asList("", "", "", "", "", "", "");
                familyMaps.put(c.text(), valueList);
                concentrazioneMap.put(c.text(), concentrazioneList);
                trendMap.put(c.text(), trend);
            }
        }
        for (Element tr : values) {
            for (Element td : tr.getAllElements()) {
                //If it is a measurement
                if (td.id().matches("^(.*?)dDay\\d")) {
                    int dayIndex = Integer.parseInt(td.id().substring(28));
                    Number valueAsNumber = format.parse(td.text());
                    String familyName = family2idMaps.get(td.id().split("_")[1]);
                    familyMaps.get(familyName).set(dayIndex, valueAsNumber.doubleValue());
                    concentrazioneMap.get(familyName).set(dayIndex, td.className());
                }
            }
        }
        JSONArray measurements = new JSONArray();
        for (String family : familyMaps.keySet()) {
            JSONObject obj = new JSONObject();
            obj.put("stazione",stationString);
            obj.put("famiglia", family);
            obj.put("trend", trendMap.get(family));
            obj.put("last_update",date.getTime());
            /*for( int day = 0; day < familyMaps.get(family).size(); day++){
                obj.put(weekday[day],familyMaps.get(family).get(day));
            }*/
            obj.put("valori", familyMaps.get(family));
            obj.put("concentrazioni", concentrazioneMap.get(family));
            measurements.add(obj);
        }

        Path file = Paths.get(args[0]);
        Files.write(file, Arrays.asList(measurements.toJSONString()), Charset.forName("UTF-8"));
    }
}
