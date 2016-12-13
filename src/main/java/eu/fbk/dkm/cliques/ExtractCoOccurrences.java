package eu.fbk.dkm.cliques;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.FrequencyHashSet;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/**
 * Created by alessio on 16/11/16.
 */

public class ExtractCoOccurrences {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractCoOccurrences.class);

//    private static boolean ADD_SINGLE_TOKEN_PERSONS = true;
//    private static boolean NAME_NORMALIZATION = false;

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./parse-text")
                    .withHeader(
                            "Extract nodes connection")
                    .withOption("i", "input", "Input folder (filled with json files)", "FILE",
                            CommandLine.Type.DIRECTORY_EXISTING, true, false, true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withOption("l", "output-links", "Output file for links", "FILE", CommandLine.Type.FILE, true,
                            false, false)
                    .withOption("p", "output-persons", "Output file for persons", "FILE", CommandLine.Type.FILE, true,
                            false, false)
                    .withOption("b", "blacklist", "Blacklist file (one person per line)", "FILE",
                            CommandLine.Type.FILE_EXISTING, true, false, false)
                    .withOption("s", "skip-name-normalization", "Skip name normalization")
                    .withOption("t", "add-single-token-persons", "Add single token persons")
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);
            File outputFileLinks = cmd.getOptionValue("output-links", File.class);
            File outputFilePersons = cmd.getOptionValue("output-persons", File.class);
            File blacklistFile = cmd.getOptionValue("blacklist", File.class);

            boolean addSingleTokenPersons = cmd.hasOption("add-single-token-persons");
            boolean skipNameNormalization = cmd.hasOption("skip-name-normalization");

            FrequencyHashSet<Set<String>> clusters = new FrequencyHashSet<>();
            FrequencyHashSet<Set<String>> connections = new FrequencyHashSet<>();
            Set<String> allPersons = new HashSet<>();

            Set<String> blacklist = new HashSet<>();
            if (blacklistFile != null) {
                List<String> lines = Files.readLines(blacklistFile, Charsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().length() > 0) {
                        line = line.replaceAll("\\s+", " ");
                        blacklist.add(line);
                    }
                }
            }

            Map<String, FrequencyHashSet<String>> frequencies = new HashMap<>();

            for (File file : inputFolder.listFiles()) {
                if (!file.isFile()) {
                    continue;
                }

                LOGGER.info(file.getName());
                long size = file.length();
                if (size == 0) {
                    continue;
                }

                // Group by day
//                String day = file.getName().substring(0, 10);

                String content = FileUtils.readFileToString(file);
                JsonElement jelement = new JsonParser().parse(content);
                JsonObject jobject = jelement.getAsJsonObject();
                JsonArray jSentences = jobject.getAsJsonArray("sentences");

                Set<String> docPersons = new HashSet<>();

                for (JsonElement jSentence : jSentences) {
                    Set<String> persons = new HashSet<>();
                    StringBuffer isPER = null;

                    JsonArray jTokens = jSentence.getAsJsonObject().getAsJsonArray("tokens");
                    for (JsonElement jToken : jTokens) {
                        boolean thisIsPER = jToken.getAsJsonObject().get("ner").getAsString().equals("PER");
                        String form = jToken.getAsJsonObject().get("originalText").getAsString();

                        if (isPER == null && thisIsPER) {
                            isPER = new StringBuffer();
                            isPER.append(form).append(" ");
                        } else if (isPER != null && thisIsPER) {
                            isPER.append(form).append(" ");
                        } else if (isPER != null && !thisIsPER) {
                            String personName = isPER.toString().trim();
                            personName = personName.replaceAll(",", "");
                            personName = personName.replaceAll("\\s+", " ");

                            if (!skipNameNormalization) {
                                for (String docPerson : docPersons) {
                                    if (docPerson.endsWith(personName)) {
                                        personName = docPerson;
                                        break;
                                    }
                                }
                            }

                            if (addSingleTokenPersons || personName.contains(" ")) {
                                persons.add(personName);
                                docPersons.add(personName);
                            }
                            isPER = null;
                        }
                    }
                    if (isPER != null) {
                        String personName = isPER.toString().trim();
                        personName = personName.replaceAll(",", "");
                        personName = personName.replaceAll("\\s+", " ");

                        if (!skipNameNormalization) {
                            for (String docPerson : docPersons) {
                                if (docPerson.endsWith(personName)) {
                                    personName = docPerson;
                                    break;
                                }
                            }
                        }

                        if (addSingleTokenPersons || personName.contains(" ")) {
                            persons.add(personName);
                            docPersons.add(personName);
                        }
                    }

                    if (persons.size() > 1) {
                        clusters.add(persons);
                        allPersons.addAll(persons);
//                        System.out.println(persons);
                    }
                }

                // Add linkings
                JsonArray jLinkings = jobject.getAsJsonArray("linkings");
                for (JsonElement jLinking : jLinkings) {
                    String page = jLinking.getAsJsonObject().get("page").getAsString();
                    String form = jLinking.getAsJsonObject().get("originalText").getAsString();
                    if (docPersons.contains(form)) {
                        frequencies.putIfAbsent(form, new FrequencyHashSet<>());
                        frequencies.get(form).add(page);
                    }
                }

            }

            if (outputFileLinks != null) {
                BufferedWriter lWriter = new BufferedWriter(new FileWriter(outputFileLinks));
                for (String form : frequencies.keySet()) {
                    lWriter.append(form).append("\t").append(frequencies.get(form).mostFrequent()).append("\n");
                }

                lWriter.close();
            }

            if (outputFilePersons != null) {
                BufferedWriter lWriter = new BufferedWriter(new FileWriter(outputFilePersons));
                for (String person : allPersons) {
                    lWriter.append(person).append("\n");

                }
                lWriter.close();
            }

            for (Set<String> cluster : clusters.keySet()) {
                Integer size = clusters.get(cluster);

                Set<Set<String>> pairs = new HashSet<>();
                for (String s1 : cluster) {
                    for (String s2 : cluster) {
                        if (s1.equals(s2)) {
                            continue;
                        }

                        if (blacklist.contains(s1) || blacklist.contains(s2)) {
                            continue;
                        }

                        pairs.add(Sets.newHashSet(s1, s2));
                    }

                }

                for (Set<String> pair : pairs) {
                    connections.add(pair, size);
                }
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            for (Map.Entry<Set<String>, Integer> entry : connections.getSorted()) {
                Set<String> set = entry.getKey();
                Integer frequency = entry.getValue();
                for (String name : set) {
                    writer.append(name).append(",");
                }
                writer.append(Integer.toString(frequency)).append("\n");
            }
            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
