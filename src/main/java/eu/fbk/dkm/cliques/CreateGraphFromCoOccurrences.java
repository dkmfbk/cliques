package eu.fbk.dkm.cliques;

import com.google.common.io.Files;
import eu.fbk.utils.core.CommandLine;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BronKerboschCliqueFinder;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by alessio on 16/11/16.
 */

public class CreateGraphFromCoOccurrences {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateGraphFromCoOccurrences.class);
    private static final Integer DEFAULT_MIN_CLIQUE_SIZE = 3;
    private static final Integer DEFAULT_MIN_EDGE_WEIGHT = 2;

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./extract-graph")
                    .withHeader(
                            "Extract nodes connection")
                    .withOption("i", "input", "Input file", "FILE",
                            CommandLine.Type.FILE_EXISTING, true, false, true)
                    .withOption(null, "min-clique-size",
                            String.format("Min size for a clique (default %d)", DEFAULT_MIN_CLIQUE_SIZE), "value",
                            CommandLine.Type.INTEGER, true, false, false)
                    .withOption(null, "min-edge-weight",
                            String.format("Min weight for an edge (default %d)", DEFAULT_MIN_EDGE_WEIGHT), "value",
                            CommandLine.Type.INTEGER, true, false, false)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
//                    .withOption("b", "blacklist", "Blacklist file (one person per line)", "FILE",
//                            CommandLine.Type.FILE_EXISTING, true, false, false)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFile = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);
            Integer minCliqueSize = cmd.getOptionValue("min-clique-size", Integer.class, DEFAULT_MIN_CLIQUE_SIZE);
            Integer minEdgeWeight = cmd.getOptionValue("min-edge-weight", Integer.class, DEFAULT_MIN_EDGE_WEIGHT);

            UndirectedGraph<String, DefaultEdge> graph = new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);

            List<String> lines = Files.readLines(inputFile, Charset.defaultCharset());
            for (String line : lines) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    continue;
                }

                String name1 = parts[0];
                String name2 = parts[1];
                Integer weight = Integer.parseInt(parts[2]);
                if (weight < minEdgeWeight) {
                    continue;
                }

                graph.addVertex(name1);
                graph.addVertex(name2);

                graph.addEdge(name1, name2);
            }

            BronKerboschCliqueFinder<String, DefaultEdge> cliqueFinder = new BronKerboschCliqueFinder<>(graph);
            Collection<Set<String>> cliques = cliqueFinder.getAllMaximalCliques();

            List<String> rows = new ArrayList<>();
            for (Set<String> clique : cliques) {
                if (clique.size() < minCliqueSize) {
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                for (String s : clique) {
                    builder.append(s).append("\t");
                }
                rows.add(builder.toString().trim());
            }

            Collections.shuffle(rows);

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            for (String row : rows) {
                writer.append(row).append("\n");
            }
            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }
}
