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
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    public final static void main(String[] args) throws IOException, ParseException {
        List<Integer> idx = new ArrayList<>();
        final Long millisInADay = 86400000L;
        if (args.length < 3) {
            System.err.println("PollNet.jar path/filename.js accessURL ID1 ID2 IDN");
            System.exit(500);
        }
        for( int i = 3; i < args.length; i++){
            idx.add(Integer.parseInt(args[i]));
        }

        SimpleDateFormat inputSdf = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat outputSdf = new SimpleDateFormat("dd-MM-yyyy");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(args[1]);
        HttpClientContext context = HttpClientContext.create();
        CloseableHttpResponse resp = httpclient.execute(httpGet, context);
        CookieStore cookieStore = context.getCookieStore();
        JSONArray measurements = new JSONArray();

        for(int id : idx){
            httpGet = new HttpGet(args[2]+id);
            HttpClientContext context1 = HttpClientContext.create();
            context1.setCookieStore(cookieStore);
            CloseableHttpResponse resp2 = httpclient.execute(httpGet, context1);
            String respondeBody = EntityUtils.toString(resp2.getEntity(), "UTF-8");
            Document doc = Jsoup.parse(respondeBody, "UTF-8");
            String dateString = doc.getElementById("gcUpdate").text().replace("Ultimo aggiornamento: ", "");

            String startDayString = doc.getElementById("PublishDate").text().replace(" al ", "-").replaceAll("[^0-9.\\-]", "").split("-")[0];
            Date startDay = inputSdf.parse(startDayString);
            List<Date> weekdays = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                weekdays.add(new Date(startDay.getTime() + day * millisInADay));
            }

            String stationString = doc.getElementById("gcStation").ownText().replaceAll(".*-\\s", "");
            Date date = inputSdf.parse(dateString);
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
                        Double valueAsNumber;
                        try {
                            valueAsNumber = format.parse(td.text()).doubleValue();
                        }
                        catch (ParseException e){
                            System.out.println("Failed to parse: "+td.text());
                            valueAsNumber = Double.NaN;
                        }
                        String familyName = family2idMaps.get(td.id().split("_")[1]);
                        familyMaps.get(familyName).set(dayIndex, valueAsNumber);
                        concentrazioneMap.get(familyName).set(dayIndex, td.className());
                    }
                }
            }
            for (String family : familyMaps.keySet()) {
                List<Double> valore = familyMaps.get(family);
                List<String> concentrazione = concentrazioneMap.get(family);
                for (int day = 0; day < 7; day++) {
                    JSONObject obj = new JSONObject();
                    obj.put("stazione", stationString);
                    obj.put("famiglia", family);
                    obj.put("trend_famiglia", trendMap.get(family));
                    obj.put("last_update", date.getTime());
                    obj.put("giorno", outputSdf.format(weekdays.get(day)));
                    obj.put("timestamp_osservazione", weekdays.get(day).getTime());
                    obj.put("valore", valore.get(day));
                    obj.put("concentrazione", concentrazione.get(day));
                    measurements.add(obj);
                }

            }
        }

        System.out.println(measurements.toJSONString());
        //Path file = Paths.get(args[0]);
        //Files.write(file, Arrays.asList(measurements.toJSONString()), Charset.forName("UTF-8"));
    }
}
