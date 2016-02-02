package eu.fbk.dkm.cliques;

import com.google.common.collect.HashMultimap;
import eu.fbk.dkm.utils.CommandLine;
import eu.fbk.dkm.utils.FrequencyHashSet;
import ixa.kaflib.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BronKerboschCliqueFinder;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * Created by alessio on 15/01/16.
 */

public class ExtractGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractGraph.class);
    private static final Integer DEFAULT_MAX_BIRTH_YEAR = 1943;
    private static final Integer DEFAULT_MIN_CLIQUE_SIZE = 3;
    private static final Integer DEFAULT_MIN_CLIQUE_SIZE_FOR_ALMOST = 4;

//    private static boolean CHECK_SPOTTED = false;
//    private static boolean CHECK_YOUNG = true;
//    private static boolean USE_COREF = true;
//    private static boolean USE_CLUSTERS = true;

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

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./extract-graph")
                    .withHeader(
                            "Extract person names in a NAF set of files")
                    .withOption("i", "input", "Input folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true, false,
                            true)
                    .withOption("c", "clusters", "Input file with clusters", "FILE", CommandLine.Type.FILE_EXISTING,
                            true, false, true)
                    .withOption("m", "cluster-mappings", "Input file with cluster mappings", "FILE",
                            CommandLine.Type.FILE_EXISTING, true, false, true)
                    .withOption("d", "dates", "List of dates", "FILE", CommandLine.Type.FILE_EXISTING, true, false,
                            true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption("l", "output-links", "Output file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption(null, "min-clique-size",
                            String.format("Min size for a clique (default %d)", DEFAULT_MIN_CLIQUE_SIZE), "value",
                            CommandLine.Type.INTEGER, true, false, false)
                    .withOption(null, "min-almost-clique-size",
                            String.format("Min size for finding an almost-clique (default %d)",
                                    DEFAULT_MIN_CLIQUE_SIZE_FOR_ALMOST), "value", CommandLine.Type.INTEGER, true, false,
                            false)
                    .withOption(null, "max-birth-year",
                            String.format("Maximum year of birth (default %d)",
                                    DEFAULT_MAX_BIRTH_YEAR), "year", CommandLine.Type.INTEGER, true, false,
                            false)
                    .withOption(null, "use-almost", "Use almost-cliques")
                    .withOption(null, "use-clusters", "Use clusters")
                    .withOption(null, "use-coref", "Use coreference")
                    .withOption(null, "use-spotted", "Use spotted in DBpedia")
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File csvFile = cmd.getOptionValue("clusters", File.class);
            File csvFileMap = cmd.getOptionValue("cluster-mappings", File.class);
            File datesFile = cmd.getOptionValue("dates", File.class);

            File outputFile = cmd.getOptionValue("output", File.class);
            File outputLinksFile = cmd.getOptionValue("output-links", File.class);

            boolean useAlmostCliques = cmd.hasOption("use-almost");
            boolean useClusters = cmd.hasOption("use-clusters");
            boolean useCoref = cmd.hasOption("use-coref");
            boolean useSpotted = cmd.hasOption("use-spotted");

            Integer minCliqueSize = cmd.getOptionValue("min-clique-size", Integer.class, DEFAULT_MIN_CLIQUE_SIZE);
            Integer maxBirthYear = cmd.getOptionValue("max-birth-year", Integer.class, DEFAULT_MAX_BIRTH_YEAR);
            Integer minCliqueSizeForAlmost = cmd
                    .getOptionValue("min-almost-clique-size", Integer.class, DEFAULT_MIN_CLIQUE_SIZE_FOR_ALMOST);

            HashMap<String, String> clusters = new HashMap<>();
            HashMap<String, Integer> birthDates = new HashMap<>();
            Reader in;

            if (useClusters) {
                LOGGER.info("Loading clusters files");

                in = new FileReader(csvFile);
                for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                    String clusterName = record.get(0);
                    for (int i = 1; i < record.size(); i++) {
                        clusters.put(record.get(i), clusterName);
                    }
                }
                in.close();

                in = new FileReader(csvFileMap);
                for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                    String cluster = clusters.get(record.get(1));
                    if (cluster != null) {
                        clusters.put(record.get(0), cluster);
                    }
                }
                in.close();
            }

            LOGGER.info("Loading dates file");
            in = new FileReader(datesFile);
            for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                String pageID = record.get(1);
                if (record.get(2) != null && !record.get(2).equals("null")) {
                    birthDates.put("http://dbpedia.org/resource/" + pageID, Integer.parseInt(record.get(2)));
                }
            }
            in.close();

            HashMultimap<String, String> topics = HashMultimap.create();
            HashMap<String, HashMap<String, Double>> linksForClusters = new HashMap<>();
            AtomicInteger skipped = new AtomicInteger(0);
            Integer modifiedCliques = 0;
            Integer addedPersons = 0;

            UndirectedGraph<String, DefaultEdge> graph = new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);

            LOGGER.info("Looping NAFs");
            Iterator<File> fileIterator = FileUtils.iterateFiles(inputFolder, new String[] { "naf.gz" }, true);
            while (fileIterator.hasNext()) {
                File file = fileIterator.next();

                LOGGER.debug(file.getAbsolutePath());
                GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file));
                KAFDocument document = KAFDocument.createFromStream(new InputStreamReader(gis, "UTF-8"));

                HashMultimap<Term, LinkedEntity> linkedEntityHashMultimap = HashMultimap.create();
                HashMultimap<Term, Term> corefHashMultimap = HashMultimap.create();
                HashMultimap<Term, Entity> entityHashMultimap = HashMultimap.create();

                HashMap<WF, Term> termsMap = new HashMap<>();
                for (Term term : document.getTerms()) {
                    for (WF wf : term.getWFs()) {
                        termsMap.put(wf, term);
                    }
                }

                Set<WF> okWFs = new HashSet<WF>();
                List<LinkedEntity> linkedEntities = document.getLinkedEntities();
                for (LinkedEntity linkedEntity : linkedEntities) {
                    if (linkedEntity.isSpotted()) {
                        for (WF wf : linkedEntity.getWFs().getTargets()) {
                            okWFs.add(wf);
                        }
                    }
                    if (linkedEntity.getTypes().get("DBPEDIA") != null) {
                        String reference = linkedEntity.getReference();
                        topics.putAll(reference, linkedEntity.getTypes().get("DBPEDIA"));
                    }
                }

                for (LinkedEntity linkedEntity : linkedEntities) {
                    Span<Term> termSpan = KAFDocument.newTermSpan();
                    for (WF wf : linkedEntity.getWFs().getTargets()) {
                        if (!useSpotted || okWFs.contains(wf)) {
                            termSpan.addTarget(termsMap.get(wf));
                        }
                    }
                    Set<String> t = topics.get(linkedEntity.getReference());
                    if (termSpan.size() > 0 && t != null && t.contains("Person")) {
                        Term termsHead = document.getTermsHead(termSpan.getTargets());
                        if (termsHead != null) {
                            linkedEntityHashMultimap.put(termsHead, linkedEntity);
                        }
                    }
                }

                for (Entity entity : document.getEntities()) {
                    if (entity.getType().equals("PERSON")) {
                        Term termsHead = document.getTermsHead(entity.getTerms());
                        if (termsHead != null) {
                            entityHashMultimap.put(termsHead, entity);
                        }
                    }
                }

                for (Coref coref : document.getCorefs()) {
                    Set<Term> heads = new HashSet<>();
                    for (Span<Term> termSpan : coref.getSpans()) {
                        Term termsHead = document.getTermsHead(termSpan.getTargets());
                        if (termsHead != null) {
                            heads.add(termsHead);
                        }
                    }
                    for (Term head : heads) {
                        corefHashMultimap.putAll(head, heads);
                    }

                }

                for (int i = 0; i < document.getNumSentences(); i++) {
                    int sentNo = i + 1;
                    List<Term> termsBySent = document.getTermsBySent(sentNo);

                    HashMultimap<Entity, LinkedEntity> entities = HashMultimap.create();

                    for (Term term : termsBySent) {
                        Set<Entity> entTerm = entityHashMultimap.get(term);
                        Set<Term> corefTerms = corefHashMultimap.get(term);

                        if (entTerm != null && entTerm.size() > 0) {
                            for (Entity entity : entTerm) {
                                LinkedEntity bestChoice = getBest(term, linkedEntityHashMultimap, birthDates,
                                        maxBirthYear, skipped);
                                entities.put(entity, bestChoice);
                            }
                        } else if (corefTerms.size() > 0 && useCoref) {
                            for (Term corefTerm : corefTerms) {
                                if (corefTerm.equals(term)) {
                                    continue;
                                }
                                Set<Entity> corefEntities = entityHashMultimap.get(corefTerm);
                                for (Entity corefEntity : corefEntities) {
                                    LinkedEntity corefBestChoice = getBest(corefTerm, linkedEntityHashMultimap,
                                            birthDates, maxBirthYear, skipped);
                                    entities.put(corefEntity, corefBestChoice);
                                }
                            }
                        }
                    }

                    if (entities.size() > 0) {
                        HashSet<String> clusterInThisSentence = new HashSet<>();

                        for (Entity entity : entities.keySet()) {
                            LOGGER.trace("ENT: {}", entity.getStr());
                            String cluster = clusters.get(entity.getStr());
                            if (cluster == null) {
                                cluster = entity.getStr();
                            }

                            clusterInThisSentence.add(cluster);
                            graph.addVertex(cluster);

                            LOGGER.trace("CLUSTER: {}", cluster);
                            Set<LinkedEntity> entitySet = entities.get(entity);
                            for (LinkedEntity linkedEntity : entitySet) {
                                if (linkedEntity != null) {
                                    if (linksForClusters.get(cluster) == null) {
                                        linksForClusters.put(cluster, new HashMap<>());
                                    }
                                    linksForClusters.get(cluster)
                                            .put(linkedEntity.getReference(), linkedEntity.getConfidence());
                                    LOGGER.trace("LINK: {}", linkedEntity.getReference());
                                }
                            }
                        }

                        for (String c1 : clusterInThisSentence) {
                            for (String c2 : clusterInThisSentence) {
                                if (c1.equals(c2)) {
                                    continue;
                                }

                                graph.addEdge(c1, c2);
                            }

                        }

                    }
                }
            }

            if (outputLinksFile != null) {
                LOGGER.info("Writing links");
                BufferedWriter linkWriter = new BufferedWriter(new FileWriter(outputLinksFile));
                for (String cluster : linksForClusters.keySet()) {
                    Map<String, Double> linkedEntities = linksForClusters.get(cluster);
                    linkedEntities = sortByValue(linkedEntities);
                    linkWriter.append(cluster);
                    for (String page : linkedEntities.keySet()) {
                        linkWriter.append('\t').append(page);
                    }
                    linkWriter.append('\n');
                }
                linkWriter.close();
            }

            LOGGER.info("Finding cliques");
            NeighborIndex neighborIndex = new NeighborIndex(graph);

            BufferedWriter writer = null;
            if (outputFile != null) {
                writer = new BufferedWriter(new FileWriter(outputFile));
            }

            FrequencyHashSet<Integer> cliqueStats = new FrequencyHashSet<>();
            FrequencyHashSet<Integer> cliqueAddedStats = new FrequencyHashSet<>();

            int nCliques = 0;
            LOGGER.info("Graph size: {}", graph.edgeSet().size());
            BronKerboschCliqueFinder<String, DefaultEdge> cliqueFinder = new BronKerboschCliqueFinder<>(graph);
            Collection<Set<String>> cliques = cliqueFinder.getAllMaximalCliques();

            for (Set<String> clique : cliques) {
                int size = clique.size();
                if (size >= minCliqueSize) {

                    // Consider also almost-cliques
                    if (useAlmostCliques && size >= minCliqueSizeForAlmost) {
                        FrequencyHashSet<String> neighbors = new FrequencyHashSet<>();

                        LOGGER.debug("Clique: {}", clique);

                        for (String s : clique) {
                            for (Object neighbor : neighborIndex.neighborsOf(s)) {
                                String n = (String) neighbor;
                                if (!clique.contains(n)) {
                                    neighbors.add(n);
                                }
                            }
                        }

                        for (String s : neighbors.keySet()) {
                            if (neighbors.get(s) == size - 1) {
                                LOGGER.debug("Added {}", s);
                                LOGGER.debug("Neighbors of {}: {}", s, neighborIndex.neighborsOf(s));
                                clique.add(s);
                            }
                        }

                        if (clique.size() != size) {
                            modifiedCliques++;
                            addedPersons += clique.size() - size;
                        }

                    }

                    cliqueStats.add(size);
                    cliqueAddedStats.add(clique.size());
                    StringBuffer row = new StringBuffer();
                    for (String c : clique) {
                        row.append(c).append('\t');
                    }

                    if (writer != null) {
                        writer.append(row.toString().trim()).append('\n');
                    }
                    nCliques++;
                }
            }

            if (writer != null) {
                writer.close();
            }

            LOGGER.info("Cliques: {}", nCliques);
            LOGGER.info("Clique stats: {}", cliqueStats);
            LOGGER.info("Skipped for birth year: {}", skipped);
            LOGGER.info("Added persons to cliques: {}", addedPersons);
            LOGGER.info("Clique stats (new): {}", cliqueAddedStats);
            LOGGER.info("Modified cliques: {}", modifiedCliques);

//            LOGGER.info(neighborIndex.neighborsOf("Nixon").toString());

//            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
//            for (String name : list) {
//                writer.append(name).append("\n");
//            }
//            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }

    }

    private static LinkedEntity getBest(Term term, HashMultimap<Term, LinkedEntity> linkedEntityHashMultimap,
            HashMap<String, Integer> birthDates, Integer maxBirthYear, AtomicInteger skipped) {
        Map<LinkedEntity, Double> entitiesForThisTerm = new HashMap<>();
        for (LinkedEntity linkedEntity : linkedEntityHashMultimap.get(term)) {
            entitiesForThisTerm.put(linkedEntity, linkedEntity.getConfidence());
        }
        entitiesForThisTerm = sortByValue(entitiesForThisTerm);
        LinkedEntity bestChoice = null;
        for (LinkedEntity linkedEntity : entitiesForThisTerm.keySet()) {
            Integer birthYear = birthDates.get(linkedEntity.getReference());
            if (birthYear != null && birthYear > maxBirthYear) {
                LOGGER.trace("Skipping " + linkedEntity.getReference() + ", too young");
                skipped.incrementAndGet();
                continue;
            }
            bestChoice = linkedEntity;
            break;
        }

        return bestChoice;
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

}
