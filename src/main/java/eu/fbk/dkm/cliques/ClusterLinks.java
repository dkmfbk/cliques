package eu.fbk.dkm.cliques;

import eu.fbk.dkm.utils.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

/**
 * Created by alessio on 27/01/16.
 */

public class ClusterLinks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLinks.class);

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./cluster-links")
                    .withHeader(
                            "Clusterize the links")
                    .withOption("i", "input", "Input file with links", "FILE", CommandLine.Type.FILE_EXISTING, true,
                            false, true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFile = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            List<String> lines = Files.readAllLines(inputFile.toPath());
            for (String line : lines) {
                line = line.trim();
                String[] parts = line.split("\t");

                String clusterName = parts[0];
                String[] tokens = clusterName.split("\\s+");
                if (tokens.length <= 1) {
                    continue;
                }

                writer.append(clusterName).append('\n');
                for (int i = 1; i < parts.length; i++) {
                    String wikipediaPage = parts[i].replaceAll(".*/([^/]+)$", "$1");
                    String onlyName = wikipediaPage.replaceAll("_\\(.*\\)$", "").replaceAll("_", " ");
                    writer.append(onlyName).append('\n');
                }
            }

            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
