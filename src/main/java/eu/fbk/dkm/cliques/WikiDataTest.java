package eu.fbk.dkm.cliques;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.EntityTimerProcessor;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by alessio on 14/01/16.
 */

public class WikiDataTest implements EntityDocumentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDataTest.class);
    BufferedWriter writer;

    public WikiDataTest(String outputFile) throws IOException {
        writer = new BufferedWriter(new FileWriter(outputFile));
    }

    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    public static void main(String[] args) {
        String wikiDataPath = args[0];
        String outputPath = args[1];

        try {
            MwLocalDumpFile mwDumpFile = new MwLocalDumpFile(wikiDataPath);

            WikiDataTest test = new WikiDataTest(outputPath);

            DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
            dumpProcessingController.registerEntityDocumentProcessor(test, null, true);
            EntityTimerProcessor entityTimerProcessor = new EntityTimerProcessor(0);
            dumpProcessingController.registerEntityDocumentProcessor(entityTimerProcessor, null, true);

            dumpProcessingController.processDump(mwDumpFile);

            entityTimerProcessor.close();
            test.close();

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    public void processItemDocument(ItemDocument itemDocument) {

        Long birthDate = null;
        Long deathDate = null;

        String enwikiPageName;
        try {
            enwikiPageName = itemDocument.getSiteLinks().get("enwiki").getPageTitle();
        } catch (NullPointerException e) {
            return;
        }

        for (StatementGroup sg : itemDocument.getStatementGroups()) {
            if ("P570".equals(sg.getProperty().getId())) {
                for (Statement statement : sg.getStatements()) {
                    Snak mainSnak = statement.getClaim().getMainSnak();
                    if (mainSnak instanceof ValueSnak) {
                        Value value = ((ValueSnak) mainSnak).getValue();
                        if (value instanceof TimeValue) {
                            deathDate = ((TimeValue) value).getYear();
                        }
                    }
                }
            }
            if ("P569".equals(sg.getProperty().getId())) {
                for (Statement statement : sg.getStatements()) {
                    Snak mainSnak = statement.getClaim().getMainSnak();
                    if (mainSnak instanceof ValueSnak) {
                        Value value = ((ValueSnak) mainSnak).getValue();
                        if (value instanceof TimeValue) {
                            birthDate = ((TimeValue) value).getYear();
                        }
                    }
                }
            }
        }

        if (birthDate != null || deathDate != null) {
            try {
                String birthDateString = birthDate == null ? "null" : birthDate.toString();
                String deathDateString = deathDate == null ? "null" : deathDate.toString();

                writer
                        .append(itemDocument.getItemId().getId()).append("\t")
                        .append(enwikiPageName).append("\t")
                        .append(birthDateString).append("\t")
                        .append(deathDateString).append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void processPropertyDocument(PropertyDocument propertyDocument) {

    }
}
