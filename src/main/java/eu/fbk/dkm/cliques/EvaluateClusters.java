package eu.fbk.dkm.cliques;

import com.google.common.collect.Sets;
import eu.fbk.twm.index.FormPageSearcher;
import eu.fbk.twm.index.PageAirpediaTypeSearcher;
import eu.fbk.twm.index.util.FreqSetSearcher;
import eu.fbk.twm.utils.AirpediaOntology;
import eu.fbk.twm.utils.DBpediaOntologyNode;
import eu.fbk.twm.utils.WeightedSet;
import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.FrequencyHashSet;
import eu.fbk.utils.core.diff_match_patch;
import eu.fbk.utils.eval.PrecisionRecall;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.*;

/**
 * Created by alessio on 27/01/16.
 */

public class EvaluateClusters {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateClusters.class);
    private static final Double AMBIGUITY_THRESHOLD = 0.2;
    private static final Double AMBIGUITY_THRESHOLD_POST = 0.5;
    private static int MAX_LEV_DIST = 2;
    private static boolean USE_ONE_CLASS = true;

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./evaluate-cluster")
                    .withHeader(
                            "Evaluate clusters against the gold standard")
                    .withOption("g", "gold", "Gold standard file", "FILE", CommandLine.Type.FILE_EXISTING, true, false,
                            true)
                    .withOption("o", "ontology", "DBpedia ontology", "FILE", CommandLine.Type.FILE_EXISTING, true,
                            false, true)
                    .withOption("a", "airpedia", "Airpedia index", "FOLDER", CommandLine.Type.DIRECTORY_EXISTING, true,
                            false, true)
                    .withOption("f", "form-page-index", "Wikimachine form-page index", "FOLDER",
                            CommandLine.Type.DIRECTORY_EXISTING, true, false, true)
                    .withOption("l", "links", "Links file", "FILE", CommandLine.Type.FILE_EXISTING, true, false, true)
                    .withOption("c", "links-cluster", "Links cluster", "FILE", CommandLine.Type.FILE_EXISTING, true,
                            false, false)
                    .withOption("m", "links-cluster-mapping", "Links cluster with mappings", "FILE",
                            CommandLine.Type.FILE_EXISTING, true, false, false)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File goldFile = cmd.getOptionValue("gold", File.class);
            File ontologyFile = cmd.getOptionValue("ontology", File.class);
            File linksFile = cmd.getOptionValue("links", File.class);
            File airpediaFile = cmd.getOptionValue("airpedia", File.class);
            File fpFile = cmd.getOptionValue("form-page-index", File.class);

            File linksClusterFile = cmd.getOptionValue("links-cluster", File.class);
            File linksClusterMapFile = cmd.getOptionValue("links-cluster-mapping", File.class);

            // ---

            diff_match_patch dmp = new diff_match_patch();
            HashMap<String, String> clusters = new HashMap<>();
            HashMap<String, String> clLinks = new HashMap<>();

            Reader in;
            if (linksClusterFile != null) {
                in = new FileReader(linksClusterFile);
                for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                    String clusterName = record.get(0);
                    for (int i = 1; i < record.size(); i++) {
                        clusters.put(record.get(i), clusterName);
                    }
                }
                in.close();
            }
            if (linksClusterMapFile != null) {
                in = new FileReader(linksClusterMapFile);
                for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                    String cluster = clusters.get(record.get(1));
                    if (cluster != null) {
                        clusters.put(record.get(0), cluster);
                    }
                }
                in.close();
            }

            LOGGER.debug("Clusters: {}", clusters);

            AirpediaOntology ontology = new AirpediaOntology(ontologyFile.getAbsolutePath());
            HashMap<String, DBpediaOntologyNode> lcNodes = new HashMap<>();
            HashSet<DBpediaOntologyNode> nodes = ontology.getNodes();
            for (DBpediaOntologyNode node : nodes) {
                lcNodes.put(node.className.toLowerCase(), node);
            }

            PageAirpediaTypeSearcher airpediaTypeSearcher = new PageAirpediaTypeSearcher(
                    airpediaFile.getAbsolutePath());
            FormPageSearcher formPageSearcher = new FormPageSearcher(fpFile.getAbsolutePath());

//            FreqSetSearcher.Entry[] search = formPageSearcher.search("John Dalton");
//            for (FreqSetSearcher.Entry entry : search) {
//                System.out.println(entry);
//            }
//            System.exit(1);

            List<String> lines;

            lines = Files.readAllLines(linksFile.toPath());
            for (String line : lines) {
                line = line.trim();
                String[] parts = line.split("\t");

                String clusterName = parts[0];
                String[] tokens = clusterName.split("\\s+");
                if (tokens.length <= 1) {
                    continue;
                }

                String linkClusterName = clusters.get(clusterName);
                if (linkClusterName == null) {
                    linkClusterName = clusterName;
                }

                HashSet<String> links = new HashSet<>();
                LinkedHashMap<String, Double> map = filterPersons(formPageSearcher.search(linkClusterName),
                        airpediaTypeSearcher);
                boolean useLinks = true;
                for (String key : map.keySet()) {
                    if (map.get(key) < AMBIGUITY_THRESHOLD) {
                        useLinks = false;
                    }
                    break;
                }

                if (useLinks) {
                    for (int i = 1; i < parts.length; i++) {
                        String wikipediaPage = parts[i].replaceAll(".*/([^/]+)$", "$1");
                        String onlyName = wikipediaPage.replaceAll("_\\(.*\\)$", "").replaceAll("_", " ");
                        String thisCluster = clusters.get(onlyName);

//                    System.out.println(wikipediaPage);
//                    System.out.println(onlyName);
//                    System.out.println(thisCluster);
                        if (thisCluster != null && thisCluster.equals(linkClusterName)) {
                            links.add(wikipediaPage);
                            continue;
                        }

                        // Add constraints on some other features (such as Levhenstein)
                        String s1 = onlyName.replaceAll("([^a-zA-Z])[A-Z]\\.", "$1").replaceAll("\\s+", " ");
                        String s2 = linkClusterName.replaceAll("([^a-zA-Z])[A-Z]\\.", "$1").replaceAll("\\s+", " ");
                        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(s1, s2, false);
                        int levDist = dmp.diff_levenshtein(diffs);
                        if (levDist <= MAX_LEV_DIST) {
                            LOGGER.debug("Added because of Levenshtein distance: " + levDist);
                            links.add(wikipediaPage);
                        }
                    }
                } else {
                    LOGGER.debug("Ignoring links for {}, too ambiguous", linkClusterName);
                }

                if (links.size() > 1) {
                    FreqSetSearcher.Entry[] entries = formPageSearcher.search(clusterName);
                    for (FreqSetSearcher.Entry entry : entries) {
                        if (links.contains(entry.getValue())) {
                            links = new HashSet<>();
                            links.add(entry.getValue());
                        }
                    }
                }

                if (links.size() > 1) {
                    LOGGER.warn("Links set is too big: {} - {}", clusterName, links);
                }

                for (String link : links) {
                    clLinks.put(clusterName, link);
                    break;
                }
            }

            PrecisionRecall.Evaluator evaluatorDBpediaOnly = PrecisionRecall.evaluator();
            PrecisionRecall.Evaluator evaluatorGroups = PrecisionRecall.evaluator();
            PrecisionRecall.Evaluator evaluatorDBpediaOnlyPlus = PrecisionRecall.evaluator();

            PrecisionRecall.Evaluator evaluatorBaseline = PrecisionRecall.evaluator();
            PrecisionRecall.Evaluator evaluatorBaselineGroups = PrecisionRecall.evaluator();

            int linked = 0;
            int total = 0;

            HashSet<String> baseline = new HashSet<>();
            DBpediaOntologyNode baselineNode = lcNodes.get("athlete");
            ArrayList<DBpediaOntologyNode> baselineNodes = ontology.getHistoryFromName(baselineNode.className);
            for (DBpediaOntologyNode thisNode : baselineNodes) {
                baseline.add(thisNode.className);

                baseline.remove("Person");
                baseline.remove("Agent");
            }

            lines = Files.readAllLines(goldFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith("-")) {
                    continue;
                }

                line = line.replaceAll("^-+", "").trim();
                String[] parts = line.split("\t");

                FrequencyHashSet<String> guessCount = new FrequencyHashSet<>();

                String dbpClass = parts[0];
                dbpClass = dbpClass.toLowerCase();
                DBpediaOntologyNode node = lcNodes.get(dbpClass);
                if (node == null) {
                    LOGGER.error("Node is null: {}", dbpClass);
                    continue;
                }

                FrequencyHashSet<String> nullCollection = new FrequencyHashSet<>();

                for (int i = 1; i < parts.length; i++) {
                    String[] subparts = parts[i].split("\\|");

                    String name = subparts[0];
//                    String thisClass;
//                    try {
//                        thisClass = subparts[1];
//                    } catch (Exception e) {
//                        LOGGER.error(line);
//                        continue;
//                    }

                    // Get link from entity linking
                    String link = clLinks.get(name);
                    String link2 = null;

//                    System.out.println(name);
//                    System.out.println(link);

                    HashSet<String> result = new HashSet<>();
                    HashSet<String> result2 = new HashSet<>();

                    addClassesToResult(link, result, airpediaTypeSearcher, guessCount);

//                    System.out.println(line);
//                    System.out.println(link);
//                    System.out.println(result);

                    if (result.size() == 0) {
                        FreqSetSearcher.Entry[] entries = formPageSearcher.search(name);
                        LinkedHashMap<String, Double> map = filterPersons(entries,
                                airpediaTypeSearcher);
                        for (String key : map.keySet()) {
                            Double val = map.get(key);
                            if (val > AMBIGUITY_THRESHOLD_POST) {
                                link2 = key;
                            }
                        }
                    }

//                    System.out.println(link2);

                    addClassesToResult(link2, result2, airpediaTypeSearcher, guessCount);

//                    System.out.println(guessCount);
//                    System.out.println();

                    HashSet<String> gold = new HashSet<>();
                    HashSet<String> allClasses = new HashSet<>();
                    for (int j = 1; j < subparts.length; j++) {
                        String thisClass = subparts[j];
                        thisClass = thisClass.toLowerCase();
                        allClasses.add(thisClass);
                        DBpediaOntologyNode thisNode = lcNodes.get(thisClass);
                        if (thisNode == null) {
                            thisNode = ontology.getNodeByName("Person");
                        }
                        ArrayList<DBpediaOntologyNode> goldNodes = ontology.getHistoryFromName(thisNode.className);
                        for (DBpediaOntologyNode goldNode : goldNodes) {
                            gold.add(goldNode.className);

                            gold.remove("Person");
                            gold.remove("Agent");
                        }

                        if (USE_ONE_CLASS) {
                            break;
                        }
                    }

                    Sets.SetView<String> intersection = Sets.intersection(gold, result);
                    int tp = intersection.size();
                    evaluatorDBpediaOnly.addTP(tp);
                    evaluatorDBpediaOnly.addFP(result.size() - tp);
                    evaluatorDBpediaOnly.addFN(gold.size() - tp);

                    total++;
                    if (result.size() != 0) {
                        linked++;
                    }

                    if (result2.size() == 0) {
                        if (result.size() == 0) {
                            nullCollection.addAll(allClasses);
                        } else {
                            evaluatorDBpediaOnlyPlus.addTP(tp);
                            evaluatorDBpediaOnlyPlus.addFP(result.size() - tp);
                            evaluatorDBpediaOnlyPlus.addFN(gold.size() - tp);
                        }
                    } else {
                        Sets.SetView<String> intersection2 = Sets.intersection(gold, result2);
                        int tp2 = intersection2.size();
                        evaluatorDBpediaOnlyPlus.addTP(tp2);
                        evaluatorDBpediaOnlyPlus.addFP(result2.size() - tp2);
                        evaluatorDBpediaOnlyPlus.addFN(gold.size() - tp2);
                    }

                    // Baseline
                    intersection = Sets.intersection(gold, baseline);
                    tp = intersection.size();
                    evaluatorBaseline.addTP(tp);
                    evaluatorBaseline.addFP(baseline.size() - tp);
                    evaluatorBaseline.addFN(gold.size() - tp);
                }

//                System.out.println(line);
//                System.out.println(nullCollection);
//                System.out.println();

                // Premio per le classi più profonde
//                Set<String> guesses = new HashSet<>(guessCount.keySet());
//                for (String guess : guesses) {
//                    int depth = ontology.getDepth(guess) - 2;
//                    int value = guessCount.get(guess);
//                    guessCount.remove(guess);
//                    guessCount.add(guess, value * depth);
//                }

                HashSet<String> lineResult = new HashSet<>();
                HashSet<String> goldResult = new HashSet<>();

                ArrayList<DBpediaOntologyNode> goldNodes = ontology.getHistoryFromName(node.className);
                for (DBpediaOntologyNode goldNode : goldNodes) {
                    goldResult.add(goldNode.className);

                    goldResult.remove("Person");
                    goldResult.remove("Agent");
                }

                String finalGuess = guessCount.mostFrequent();
                if (finalGuess != null) {
                    ArrayList<DBpediaOntologyNode> resultNodes = ontology.getHistoryFromName(finalGuess);
                    for (DBpediaOntologyNode resultNode : resultNodes) {
                        lineResult.add(resultNode.className);

                        lineResult.remove("Person");
                        lineResult.remove("Agent");
                    }
                }

//                System.out.println(guessCount);
//                System.out.println(goldResult);
//                System.out.println(lineResult);
//                System.out.println();

                Sets.SetView<String> intersection = Sets.intersection(goldResult, lineResult);
                int tp = intersection.size();
                evaluatorGroups.addTP(tp);
                evaluatorGroups.addFP(lineResult.size() - tp);
                evaluatorGroups.addFN(goldResult.size() - tp);

                for (String key : nullCollection.keySet()) {
                    DBpediaOntologyNode thisNode = lcNodes.get(key);
                    if (thisNode == null) {
                        thisNode = ontology.getNodeByName("Person");
                    }
                    HashSet<String> gold = new HashSet<>();
                    ArrayList<DBpediaOntologyNode> goldSubNodes = ontology.getHistoryFromName(thisNode.className);
                    for (DBpediaOntologyNode goldNode : goldSubNodes) {
                        gold.add(goldNode.className);

                        gold.remove("Person");
                        gold.remove("Agent");
                    }
                    Sets.SetView<String> subIntersection = Sets.intersection(gold, lineResult);
                    int subTp = subIntersection.size();
                    evaluatorDBpediaOnlyPlus.addTP(subTp);
                    evaluatorDBpediaOnlyPlus.addFP(lineResult.size() - subTp);
                    evaluatorDBpediaOnlyPlus.addFN(gold.size() - subTp);
                }

                intersection = Sets.intersection(goldResult, baseline);
                tp = intersection.size();
                evaluatorBaselineGroups.addTP(tp);
                evaluatorBaselineGroups.addFP(baseline.size() - tp);
                evaluatorBaselineGroups.addFN(goldResult.size() - tp);
            }

            System.out.println("Total processed: " + total);
            System.out.println("Total linked: " + linked);

            System.out.println("Baseline: " + evaluatorBaseline.getResult());
            System.out.println("DBpedia only: " + evaluatorDBpediaOnly.getResult());
            System.out.println("All elements in clique: " + evaluatorDBpediaOnlyPlus.getResult());
            System.out.println();
            System.out.println("Baseline groups: " + evaluatorBaselineGroups.getResult());
            System.out.println("Groups: " + evaluatorGroups.getResult());

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }

    private static void addClassesToResult(String l, HashSet<String> result,
            PageAirpediaTypeSearcher airpediaTypeSearcher, FrequencyHashSet<String> guessCount) {
        if (l != null) {
            WeightedSet classSet = airpediaTypeSearcher.search(l);
            result.addAll(classSet.keySet());

            for (String s : classSet.keySet()) {
                if (s.equals("Person")) {
                    continue;
                }
                if (s.equals("Agent")) {
                    continue;
                }
                if (guessCount != null) {
                    guessCount.add(s);
                }
            }

            result.remove("Person");
            result.remove("Agent");
        }
    }

    private static LinkedHashMap<String, Double> filterPersons(FreqSetSearcher.Entry[] search,
            PageAirpediaTypeSearcher airpediaTypeSearcher) {
        ArrayList<FreqSetSearcher.Entry> retList = new ArrayList<>();

        if (search != null) {
            for (FreqSetSearcher.Entry entry : search) {
                WeightedSet set = airpediaTypeSearcher.search(entry.getValue());
                if (set.contains("Person")) {
                    retList.add(entry);
                }
            }
        }

        double sum = 0;
        for (FreqSetSearcher.Entry entry : retList) {
            sum += entry.getFreq();
        }

        LinkedHashMap<String, Double> ret = new LinkedHashMap<>();
        if (sum > 0) {
            for (FreqSetSearcher.Entry entry : retList) {
                ret.put(entry.getValue(), entry.getFreq() / sum);
            }
        }

        return ret;
    }
}
