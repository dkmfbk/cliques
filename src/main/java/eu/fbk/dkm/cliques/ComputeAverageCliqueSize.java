package eu.fbk.dkm.cliques;

import eu.fbk.dkm.utils.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Created by alessio on 03/02/16.
 */

public class ComputeAverageCliqueSize {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeAverageCliqueSize.class);

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./average-size")
                    .withHeader(
                            "Compute the average size of the cliques")
                    .withOption("i", "input", "Input files", "FILE", CommandLine.Type.FILE_EXISTING, true, true, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            List<File> fileList = cmd.getOptionValues("input", File.class);

            for (File inputFile : fileList) {
                List<String> lines = Files.readAllLines(inputFile.toPath());

                int lineCount = 0;
                int entityCount = 0;
                for (String line : lines) {
                    line = line.trim();
                    if (line.length() == 0) {
                        continue;
                    }

                    lineCount++;
                    String[] parts = line.split("\t");
                    entityCount += parts.length;
                }

                LOGGER.info(String.format("%s - %d - %d - %f", inputFile.getName(), lineCount, entityCount,
                        (double) entityCount / lineCount));
            }

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
