package eu.fbk.dkm.cliques;

import eu.fbk.dh.tint.runner.TintPipeline;
import eu.fbk.dh.tint.runner.TintRunner;
import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.XMLHelper;
import org.apache.commons.io.output.WriterOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by alessio on 15/11/16.
 */

public class ParseXML {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParseXML.class);
    private static Pattern cityPattern = Pattern.compile("^[^a-z]* - ");
    private static Pattern rrPattern = Pattern.compile("RIPRODUZIONE RISERVATA");
    private static int MIN_LENGTH = 100;

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./parse-text")
                    .withHeader(
                            "Parse texts (in CAT XML format) with Tint and save into JSON format")
                    .withOption("i", "input", "Input folder", "FILE",
                            CommandLine.Type.DIRECTORY_EXISTING, true, false, true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);

            outputFile.mkdirs();

            TintPipeline pipeline = new TintPipeline();
            pipeline.loadDefaultProperties();
            pipeline.setProperty("annotators", "ita_toksent, pos, ita_morpho, ita_lemma, ner, ml");
            pipeline.setProperty("ml.annotator", "ml-annotate");
            pipeline.setProperty("ml.address", "http://ml.apnetwork.it/annotate");
            pipeline.setProperty("ml.min_confidence", "0.2");
            pipeline.load();

            int count = 0;

            for (File file : inputFolder.listFiles()) {
                if (!file.isFile()) {
                    continue;
                }

                Document doc = XMLHelper.getDocument(file);
                doc.getDocumentElement().normalize();

                NodeList nl = XMLHelper.getNodeList(doc, "/xml/file");
                for (int i = 0; i < nl.getLength(); i++) {

                    String outFileName =
                            outputFile.getAbsolutePath() + File.separator + file.getName() + "_" + (i + 1) + ".json";
                    File outFile = new File(outFileName);

                    BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
                    WriterOutputStream writerOutputStream = new WriterOutputStream(writer);

                    Node item = nl.item(i);

                    NodeList childNodes = item.getChildNodes();
                    String url = XMLHelper.getNodeValue("url", childNodes);
                    String content = XMLHelper.getNodeValue("content", childNodes);
                    String title = XMLHelper.getNodeValue("title", childNodes);

//                    writer.write("# URL: " + url + "\n");
//                    url = url.replaceAll("[^a-zA-Z0-9-_]", "");

                    Matcher matcher;

                    matcher = cityPattern.matcher(content);
                    if (matcher.find()) {
                        content = content.substring(matcher.end());
                    }

                    matcher = rrPattern.matcher(content);
                    if (matcher.find()) {
                        int start = matcher.start();
                        while (true) {
                            if (content.charAt(start) == '.' ||
                                    content.charAt(start) == '!' ||
                                    content.charAt(start) == '?') {
                                break;
                            }
                            start--;

                            if (start == -1) {
                                break;
                            }
                        }
                        content = content.substring(0, start + 1);
                    }

                    content = content.replaceAll("([.!?]) [^a-z]* - ", "$1 ");

                    if (content.length() < MIN_LENGTH) {
                        continue;
                    }

                    content = title + "\n" + content;
                    count++;

//                    File outFile = new File(outFolder.getAbsolutePath() + File.separator + url);

//                    FileOutputStream outputStream = new FileOutputStream(outFile);
                    pipeline.run(content, writerOutputStream, TintRunner.OutputFormat.JSON);

                    writer.close();
                }
            }

            System.out.println(count);

        } catch (Exception e) {
            CommandLine.fail(e);
        }

    }
}
