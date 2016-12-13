package eu.fbk.dkm.cliques;

import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.FrequencyHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by alessio on 25/01/16.
 */

public class MergeNameSurname {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeNameSurname.class);
    private static int DEFAULT_CUT_FREQUENCY = 5;
    private static Pattern linePattern = Pattern.compile("^([0-9]+)\\s(.*)");

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./collect-names")
                    .withHeader(
                            "Merge name and surname files to collect names")
                    .withOption("n", "names", "Names file", "FILE", CommandLine.Type.FILE_EXISTING, true, false, true)
                    .withOption("s", "surnames", "Surnames file", "FILE", CommandLine.Type.FILE_EXISTING, true, false,
                            true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withOption("c", "cut-frequency",
                            String.format("Frequency threshold, default %d", DEFAULT_CUT_FREQUENCY), "num",
                            CommandLine.Type.INTEGER, true, false, false)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File namesFile = cmd.getOptionValue("names", File.class);
            File surnamesFile = cmd.getOptionValue("surnames", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);
            Integer cutFrequency = cmd.getOptionValue("cut-frequency", Integer.class, DEFAULT_CUT_FREQUENCY);

            HashMap<String, Integer> nameFrequency = new HashMap<>();
            HashMap<String, Integer> surnameFrequency = new HashMap<>();

            FrequencyHashSet<String> names = new FrequencyHashSet<>();

            String line;
            BufferedReader fileReader;

            LOGGER.info("Loading names");
            fileReader = new BufferedReader(new FileReader(namesFile));
            while ((line = fileReader.readLine()) != null) {
                line = line.trim();

                Matcher matcher = linePattern.matcher(line);
                if (!matcher.find()) {
                    continue;
                }

                Integer frequency = Integer.parseInt(matcher.group(1));
                String name = matcher.group(2).trim();

                nameFrequency.put(name, frequency);
            }
            fileReader.close();
            LOGGER.info("Loaded {} names", nameFrequency.size());

            LOGGER.info("Loading surnames");
            fileReader = new BufferedReader(new FileReader(surnamesFile));
            while ((line = fileReader.readLine()) != null) {
                line = line.trim();

                Matcher matcher = linePattern.matcher(line);
                if (!matcher.find()) {
                    continue;
                }

                Integer frequency = Integer.parseInt(matcher.group(1));
                String name = matcher.group(2).trim();

                surnameFrequency.put(name, frequency);
            }
            fileReader.close();
            LOGGER.info("Loaded {} surnames", surnameFrequency.size());

            LOGGER.info("Writing output file");
            for (String name : nameFrequency.keySet()) {
                Integer thisNameFrequency = nameFrequency.get(name);
                Integer thisSurnameFrequency = surnameFrequency.get(name);

                if (thisNameFrequency <= cutFrequency) {
                    continue;
                }
                if (thisSurnameFrequency != null && thisSurnameFrequency > thisNameFrequency) {
                    continue;
                }

                names.add(name, thisNameFrequency);
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            for (Map.Entry<String, Integer> entry : names.getSorted()) {
                String name = entry.getKey();
                name = name.replaceAll("\\s+\\.", ".");
                name = name.replaceAll("\\s+,", ",");
                name = name.replaceAll("\\s+-\\s+", "-");
                name = name.replaceAll("\"\\s+([^\"\\s]+)\\s+\"", "\"$1\"");
                writer.append(name).append('\n');
            }
            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
