package eu.fbk.dkm.cliques;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;
import eu.fbk.utils.core.CommandLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;
import java.util.Set;

/**
 * Created by alessio on 26/01/16.
 */

public class CompareSets {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompareSets.class);

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./compare-sets")
                    .withHeader(
                            "Compare sets in a list of files and get the common sets")
                    .withOption("i", "input", "Input files", "FILES", CommandLine.Type.FILE_EXISTING, true, true, true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            List<File> inputFiles = cmd.getOptionValues("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);

            HashMultimap<Integer, Set<String>> sets = null;

            for (File file : inputFiles) {
                FileReader in = new FileReader(file);
                boolean add = false;
                if (sets == null) {
                    sets = HashMultimap.create();
                    add = true;
                }

                HashMultimap<Integer, Set<String>> newSets = HashMultimap.create();

                for (CSVRecord record : CSVFormat.newFormat('\t').parse(in)) {
                    Set<String> set = Sets.newHashSet(record);
                    if (add) {
                        sets.put(set.size(), set);
                    } else {
                        Set<Set<String>> thisSizeSet = sets.get(set.size());
                        for (Set<String> strings : thisSizeSet) {
                            if (Sets.symmetricDifference(strings, set).isEmpty()) {
                                newSets.put(set.size(), set);
                            }
                        }

                    }
                }
                in.close();

                if (!add) {
                    sets = newSets;
                }
            }

            int total = 0;
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            for (Integer size : sets.keySet()) {
                LOGGER.debug("Size: {}", size);
                for (Set<String> clique : sets.get(size)) {
                    LOGGER.debug(clique.toString());
                    StringBuffer row = new StringBuffer();
                    for (String c : clique) {
                        row.append(c).append('\t');
                    }
                    writer.append(row.toString().trim()).append('\n');
                    total++;
                }
            }

            LOGGER.info("Total cliques: {}", total);
            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
