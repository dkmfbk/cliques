package eu.fbk.dkm.cliques;

import com.google.common.collect.Sets;
import eu.fbk.utils.core.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by alessio on 28/01/16.
 */

public class ExtractCliquesForAnnotation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractCliquesForAnnotation.class);

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./extract-cliques")
                    .withHeader(
                            "Extract cliques from annotation")
                    .withOption("i", "input", "Input file", "FILE", CommandLine.Type.FILE_EXISTING, true, false, true)
                    .withOption("a", "annotated", "Already annotated file", "FILE", CommandLine.Type.FILE_EXISTING,
                            true, false, true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFile = cmd.getOptionValue("input", File.class);
            File annotatedFile = cmd.getOptionValue("annotated", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);

            List<Set<String>> annotatedSets = new ArrayList<>();

            List<String> lines;

            lines = Files.readAllLines(annotatedFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith("-")) {
                    continue;
                }

                line = line.substring(1).trim();
                String[] parts = line.split("\t");
                Set<String> thisSet = new HashSet<>();
                for (int i = 1; i < parts.length; i++) {
                    thisSet.add(parts[i].replaceAll("\\|.*", "").trim());
                }

                annotatedSets.add(thisSet);
            }

            int count = 0;
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            lines = Files.readAllLines(inputFile.toPath());
            for (String line : lines) {
                line = line.trim();

                String[] parts = line.split("\t");
                if (parts.length < 4 || parts.length > 7) {
                    continue;
                }

                Set<String> thisSet = new HashSet<>();

                boolean skip = false;
                for (String part : parts) {
                    part = part.trim();
                    String noSpaces = part.replaceAll("\\s+", "");

                    thisSet.add(part);

                    if (part.toLowerCase().contains("kennedy")) {
                        skip = true;
                    }
                    if (part.toLowerCase().contains("nixon")) {
                        skip = true;
                    }

                    if (part.length() == noSpaces.length()) {
                        skip = true;
                    }
                }

                for (Set<String> annotatedSet : annotatedSets) {
                    if (Sets.symmetricDifference(annotatedSet, thisSet).isEmpty()) {
                        skip = true;
                    }
                }

                if (skip) {
                    continue;
                }

                count++;
                writer.append(line).append('\n');
            }

            writer.close();
            LOGGER.info("Written {} cliques", count);

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
