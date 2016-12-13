package eu.fbk.dkm.cliques;

import eu.fbk.utils.core.CommandLine;
import ixa.kaflib.Entity;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.Span;
import ixa.kaflib.Term;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created by alessio on 15/01/16.
 */

public class ExtractPersons {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractPersons.class);

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./extract-persons")
                    .withHeader(
                            "Extract person names in a NAF set of files")
                    .withOption("i", "input", "Input folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true, false,
                            true)
                    .withOption("o", "output", "Output file", "FILE", CommandLine.Type.FILE, true, false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File outputFile = cmd.getOptionValue("output", File.class);

            ArrayList<String> list = new ArrayList<String>();

            Iterator<File> fileIterator = FileUtils.iterateFiles(inputFolder, new String[] { "naf.gz" }, true);
            while (fileIterator.hasNext()) {
                File file = fileIterator.next();
                GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file));
                KAFDocument document = KAFDocument.createFromStream(new InputStreamReader(gis, "UTF-8"));

                List<Entity> entities = document.getEntities();
                for (Entity entity : entities) {
                    if (entity.getType().equals("PERSON")) {
                        for (Span<Term> termSpan : entity.getSpans()) {
                            String name = termSpan.getStr();
                            if (name == null) {
                                continue;
                            }
                            list.add(name);
                        }

                    }
                }
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            for (String name : list) {
                writer.append(name).append("\n");
            }

            writer.close();

        } catch (Exception e) {
            CommandLine.fail(e);
        }

    }
}
