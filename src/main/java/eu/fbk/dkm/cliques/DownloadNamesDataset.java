package eu.fbk.dkm.cliques;

import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * Created by alessio on 20/01/16.
 */

public class DownloadNamesDataset {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadNamesDataset.class);
    private static final HashSet<String> classes = new HashSet<>();

    static {
        classes.add("u1");
        classes.add("c1");
        classes.add("c1g");
        classes.add("u1g");

        classes.add("c0g");
        classes.add("c0");
        classes.add("u0");
        classes.add("u0g");
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        String outputFolder = args[0];
        if (!outputFolder.endsWith(File.separator)) {
            outputFolder += File.separator;
        }

        Character sex = null;
        if (args.length > 1) {
            sex = args[1].charAt(0);
        }

        Character letter = null;
        if (args.length > 2) {
            letter = args[2].charAt(0);
        }

        File outputFile = new File(outputFolder);
        if (outputFile.exists() && !outputFile.isDirectory()) {
            LOGGER.error("File {} is not valid", outputFolder);
            System.exit(1);
        }

        outputFile.mkdirs();

        char[] genders = { 'm', 'f' };

        genderLoop:
        for (char gender : genders) {

            if (sex != null && sex != gender) {
                continue;
            }

            charLoop:
            for (char c = 'a'; c <= 'z'; c++) {

                if (letter != null && c < letter) {
                    continue;
                }

                File letterFile = new File(outputFile.getAbsolutePath() + File.separator + gender + "-" + c);
                letterFile.mkdir();

                pageLoop:
                for (int i = 1; i < 10000; i++) {
                    boolean acceptRedirects = false;
                    if (i == 1) {
                        acceptRedirects = true;
                    }
                    String url = String.format("http://www.babynamespedia.com/start/%s/%s/%d", gender, c, i);
                    LOGGER.info("##### {}", url);
                    Connection.Response response = Jsoup.connect(url).followRedirects(acceptRedirects).execute();
                    if (response.statusCode() != 200) {
                        break pageLoop;
                    }
                    Document doc = response.parse();
                    Elements links = doc.select("td a[class]");
                    for (Element link : links) {
                        String className = link.attr("class");
                        if (!classes.contains(className)) {
                            continue;
                        }

                        String nameURL = "http://www.babynamespedia.com" + link.attr("href");
                        String name = nameURL.substring(38).replaceAll("/", "-");

                        File thisFile = new File(letterFile.getAbsolutePath() + File.separator + name);
                        if (thisFile.exists()) {
                            continue;
                        }
                        LOGGER.info("### {}", nameURL);

                        StringBuffer buffer = new StringBuffer();

                        Connection.Response nameResponse = Jsoup.connect(nameURL).followRedirects(true).execute();

                        Document nameDoc = nameResponse.parse();
                        Elements elements = nameDoc.select("h1, div.col-sm-12 p");
                        for (Element element : elements) {
                            buffer.append(element.outerHtml()).append("\n");
                        }

                        FileUtils.write(thisFile, buffer.toString());
                    }
                }
            }
        }
    }
}
