package eu.fbk.dkm.cliques;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;
import eu.fbk.dkm.cliques.align.DMP;
import eu.fbk.dkm.cliques.cat.Entity;
import eu.fbk.dkm.cliques.cat.EntityMention;
import eu.fbk.dkm.cliques.cat.Token;
import eu.fbk.dkm.utils.CommandLine;
import eu.fbk.dkm.utils.eval.PrecisionRecall;
import ixa.kaflib.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by alessio on 25/01/16.
 */

public class AlignCatWithNaf {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlignCatWithNaf.class);
    private static final Integer DIFF_CLUSTERS = 100000;

    public static void main(String[] args) {
        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./align")
                    .withHeader(
                            "Align CAT files with NAFs")
                    .withOption("c", "cats", "Input CAT folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true,
                            false, true)
                    .withOption("n", "nafs", "Input NAF folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true,
                            false, true)
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File catFolder = cmd.getOptionValue("cats", File.class);
            File nafFolder = cmd.getOptionValue("nafs", File.class);

            HashMap<String, CatDocument> catDocuments = new HashMap<>();
            Iterator<File> fileIterator;
            DMP dmpFactory = new DMP();

            LOGGER.info("Reading CAT documents");
            fileIterator = FileUtils.iterateFiles(catFolder, new String[] { "xml" }, true);
            while (fileIterator.hasNext()) {
                File file = fileIterator.next();
                LOGGER.debug("Parsing {}", file.getAbsolutePath());
                CatDocument document = CatDocument.readFromFile(file);
                catDocuments.put(document.getName(), document);
            }

            Set<Set> finalClusters = new HashSet<>();
            Set<Set> corefSets = new HashSet<>();

            LOGGER.info("Reading NAF documents");
            fileIterator = FileUtils.iterateFiles(nafFolder, new String[] { "naf.gz" }, true);
            int i = 0;
            while (fileIterator.hasNext()) {
                File file = fileIterator.next();
                String id = file.getName().replaceAll("\\..*", "");
                if (!catDocuments.containsKey(id)) {
                    LOGGER.trace("Skipping {}", file.getAbsolutePath());
                    continue;
                }

                i++;
                int diff = i * DIFF_CLUSTERS;

                LOGGER.debug("Parsing {}", file.getAbsolutePath());

                CatDocument catDocument = catDocuments.get(id);

                GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file));
                KAFDocument nafDocument = KAFDocument.createFromStream(new InputStreamReader(gis, "UTF-8"));

                List<String> strings1 = new ArrayList<>();
                List<String> strings2 = new ArrayList<>();

                for (Token token : catDocument.getTokens().values()) {
                    strings1.add(token.getText());
                }
                for (WF wf : nafDocument.getWFs()) {
                    strings2.add(wf.getForm());
                }

                Map<Integer, Integer> tokenMap = dmpFactory.findMapping(strings2, strings1, true);

                Map<Integer, Integer> relations = catDocument.getRelations();
                Map<Integer, Entity> entities = catDocument.getEntities();

                HashMultimap<Integer, Integer> easyClusters = HashMultimap.create();

                for (Integer key : relations.keySet()) {

                    Integer target = relations.get(key);

                    Entity sourceEntity = entities.get(key);

                    if (sourceEntity instanceof EntityMention) {
                        TreeSet<Integer> tokenAnchors = ((EntityMention) sourceEntity).getTokenAnchors();
                        Set<Term> terms = new HashSet<>();
//                        Set<Integer> tokens = new HashSet<>();
                        for (Integer tokenAnchor : tokenAnchors) {
                            Integer newTokenID = tokenMap.get(tokenAnchor - 1);
                            if (newTokenID == null) {
                                continue;
                            }

                            Term term = nafDocument.getTerms().get(newTokenID);
                            terms.add(term);
                        }

                        Term head = nafDocument.getTermsHead(terms);
                        if (head != null) {
                            for (WF wf : head.getWFs()) {
                                Integer tokenID = Integer.parseInt(wf.getId().substring(1));
                                easyClusters.put(target, tokenID + diff);
                            }
                        }
                    }

//                    Entity targetEntity = entities.get(target);
//                    if (targetEntity == null) {
//                        LOGGER.warn("Entity is null");
//                        continue;
//                    }

//                    if (tokens.size() == 0) {
//                        continue;
//                    }
//
//                    clusters.put(target, tokens);
                }

                for (Coref coref : nafDocument.getCorefs()) {
                    HashSet<Integer> set = new HashSet<>();
                    for (Span<Term> termSpan : coref.getSpans()) {
                        Term head = nafDocument.getTermsHead(termSpan.getTargets());
                        if (head == null) {
                            continue;
                        }
                        for (WF wf : head.getWFs()) {
                            Integer tokenID = Integer.parseInt(wf.getId().substring(1));
                            set.add(tokenID + diff);
                        }
                    }

                    boolean intersect = false;
                    for (Integer integer : easyClusters.keys()) {
                        Set<Integer> tokens = easyClusters.get(integer);
                        if (!Sets.intersection(tokens, set).isEmpty()) {
                            intersect = true;
                        }
                    }

                    if (intersect) {
                        corefSets.add(set);
                    }
                }

                for (Integer key : easyClusters.keys()) {
                    Set<Integer> set = easyClusters.get(key);
                    finalClusters.add(set);
                }

                for (Set set : finalClusters) {
                    StringBuffer buffer = new StringBuffer();
                    for (Object o : set) {
                        Integer integer = (Integer) o;
                        integer -= diff;
                        Term term = nafDocument.getTerms().get(integer - 1);
                        buffer.append(term).append(" ");
                    }
                    LOGGER.info("CLUSTER: {}", buffer.toString().trim());
                }

                for (Set set : corefSets) {
                    StringBuffer buffer = new StringBuffer();
                    for (Object o : set) {
                        Integer integer = (Integer) o;
                        integer -= diff;
                        Term term = nafDocument.getTerms().get(integer - 1);
                        buffer.append(term).append(" ");
                    }
                    LOGGER.info("COREF: {}", buffer.toString().trim());
                }

            }

            Map<PrecisionRecall.Measure, Double> precisionRecall = ClusteringEvaluation
                    .pairWise(finalClusters, corefSets);

            System.out.println(precisionRecall);

        } catch (Exception e) {
            CommandLine.fail(e);
        }
    }

}
