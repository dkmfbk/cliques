package eu.fbk.dkm.cliques;

import com.google.common.collect.HashMultimap;
import eu.fbk.dkm.utils.CommandLine;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.LinkedEntity;
import ixa.kaflib.WF;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by alessio on 15/01/16.
 */

public class ExtractGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractGraph.class);

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {

            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }

        });

        Map<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static void printMap(Map map) {
        printMap(map, 0);
    }

    public static void printMap(Map map, int spaces) {
        String s = "";
        for (int i = 0; i < spaces; i++) {
            s += " ";
        }
        for (Object key : map.keySet()) {
            System.out.println(s + key);
            Object value = map.get(key);
            if (value instanceof Map) {
                Map sortedMap = sortByValue((Map) value);
                printMap(sortedMap, spaces + 4);
            } else {
                System.out.println(s + value);
            }
        }

    }

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./extract-graph")
                    .withHeader(
                            "Extract person names in a NAF set of files")
                    .withOption("i", "input", "Input folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true, false,
                            true)
                    .withOption("c", "clusters", "Input file with clusters", "FILE", CommandLine.Type.FILE_EXISTING, true, false,
                            true)
                    .withOption("d", "dates", "List of dates", "FILE", CommandLine.Type.FILE_EXISTING, true, false,
                            true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);
            File csvFile = cmd.getOptionValue("clusters", File.class);
            File datesFile = cmd.getOptionValue("dates", File.class);

            HashMultimap<String, String> clusters = HashMultimap.create();
            HashMap<String, Integer> deathDates = new HashMap<>();
            Reader in;

            LOGGER.info("Loading clusters file");
            in = new FileReader(csvFile);
            for (CSVRecord record : CSVFormat.DEFAULT.parse(in)) {
                String clusterName = record.get(0);
                for (int i = 1; i < record.size(); i++) {
                    clusters.put(record.get(i), clusterName);
                }
            }
            in.close();

            LOGGER.info("Loading dates file");
            in = new FileReader(datesFile);
            for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                String pageID = record.get(0);
                if (record.get(2) != null && !record.get(2).equals("null")) {
                    deathDates.put(pageID, Integer.parseInt(record.get(2)));
                }
            }
            in.close();

            printMap(deathDates);
            System.exit(1);


            HashMultimap<String, String> topics = HashMultimap.create();

            LOGGER.info("Looping NAFs");
            Iterator<File> fileIterator = FileUtils.iterateFiles(inputFolder, new String[] { "naf.gz" }, true);
            while (fileIterator.hasNext()) {
                File file = fileIterator.next();
                GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file));
                KAFDocument document = KAFDocument.createFromStream(new InputStreamReader(gis, "UTF-8"));

                HashMap<WF, HashMap<String, Double>> entityMap = new HashMap<WF, HashMap<String, Double>>();

                Set<WF> okWFs = new HashSet<WF>();

                List<LinkedEntity> linkedEntities = document.getLinkedEntities();
                for (LinkedEntity linkedEntity : linkedEntities) {
                    if (linkedEntity.isSpotted()) {
                        for (WF wf : linkedEntity.getWFs().getTargets()) {
                            okWFs.add(wf);
                        }
                    }
                }

                for (LinkedEntity linkedEntity : linkedEntities) {
                    for (WF wf : linkedEntity.getWFs().getTargets()) {
                        if (!okWFs.contains(wf)) {
                            continue;
                        }

                        if (entityMap.get(wf) == null) {
                            entityMap.put(wf, new HashMap<String, Double>());
                        }

                        String reference = linkedEntity.getReference();
                        double confidence = linkedEntity.getConfidence();

                        if (linkedEntity.getTypes().get("DBPEDIA") != null) {
                            topics.putAll(reference, linkedEntity.getTypes().get("DBPEDIA"));
                        }
                        entityMap.get(wf).put(reference, confidence);
                    }
                }


//                System.out.println(file.getAbsolutePath());
//                printMap(entityMap);
//                printMap(topics.asMap());

                break;
            }

//            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
//            for (String name : list) {
//                writer.append(name).append("\n");
//            }
//            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }

    }
}
