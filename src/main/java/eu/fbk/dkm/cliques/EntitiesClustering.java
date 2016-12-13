package eu.fbk.dkm.cliques;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * dh.fbk.eu.dh_entities_analizer class
 *
 * @author Giovanni Moretti - DH Group FBK.
 */
public class EntitiesClustering {

    /**
     * Enum Type that specifies the names list to be chosen
     * It is possible to choose among:
     * <ul>
     * <li>Italian - integrated Italian names</li>
     * <li>English - integrated English names</li>
     * <li>Custom_English - external English names list contained in custom_name_list.txt file</li>
     * </ul>
     *
     * @author Giovanni Moretti - DH Group FBK.
     */
    public enum Names {
        Italian, English, Custom_English
    }

    /**
     * This method performs the clusterization of a file containing a list of entities
     *
     * @param file_path  file path of the entities list
     * @param names_list enum for the names list specification
     * @return a StringBuffer containing a tab-separated list with new clusters and original entities
     * @author Giovanni Moretti - DH Group FBK.
     */

    public static StringBuffer clusterize(String file_path, Names names_list) {
        StringBuffer out = new StringBuffer();
        File fileDir = new File(file_path);

        ArrayList<ClusterEntity> complexName = new ArrayList<ClusterEntity>();
        ArrayList<ClusterEntity> simpleName = new ArrayList<ClusterEntity>();
        try {

            // open file descriptor
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(fileDir), "UTF8"));

            String str;

            ClusterEntity ent = null;

            ArrayList<String> names = new ArrayList<String>();
            switch (names_list) {
            case Italian:
                names = compileItalianNameList();
                break;
            case English:
                names = compileEnglishNameList();
                break;
            case Custom_English:
                names = compileEnglishCustomNameList();
                break;
            }

            String original_entities = "";
            String file_name = "";

            // read file by line
            while ((str = in.readLine()) != null) {

                original_entities = str.trim().replaceAll("\\s+", " ");
                String[] entityItems = original_entities.split(" ");

                // Capitalized character normalization
                for (int i = 0; i < entityItems.length; i++) {
                    if (StringUtils.isAllUpperCase(entityItems[i].trim())) {
                        entityItems[i] = StringUtils.capitalize(entityItems[i]);
                    }
                }

                // entities classification (complex or simple)
                if (entityItems.length > 1) {
                    ent = new ClusterEntity(original_entities.split(" "));
                    if (complexName.contains(ent)) {
                        ent = complexName.get(complexName.indexOf(ent));
                        complexName.remove(ent);
                    }
                    ent.incrementFrequency();
                    ent.addFileReference(file_name);

                    if (ent.toString().compareToIgnoreCase(original_entities) != 0) {
                        ent.alternativeForms.add(original_entities);
                        if (!ent.alternativeFormsWithFrequency.containsKey(original_entities)) {
                            ent.alternativeFormsWithFrequency.put(original_entities, 1);
                        } else {
                            ent.alternativeFormsWithFrequency.put(original_entities,
                                    ent.alternativeFormsWithFrequency.get(original_entities) + 1);
                        }
                    }

                    complexName.add(ent);
                } else {
                    if (!names.contains(original_entities)) {
                        ent = new ClusterEntity(original_entities.split(" "));
                        simpleName.add(ent);

                    }
                }
            }

            ArrayList<ClusterEntity> candidateEntities = null;

            Set<ClusterEntity> simpleName_dup = new HashSet<ClusterEntity>();
            simpleName_dup.addAll(simpleName);

            // loop on simple entities and try to pick up a complex candidate
            for (ClusterEntity se : simpleName) {
                candidateEntities = new ArrayList<ClusterEntity>();
                String fileReference = "";

                for (String fr : se.fileReference) {
                    fileReference = fr;
                    break;
                }

                for (ClusterEntity ce : complexName) {
                    if (se.equals(ce)) {
                        candidateEntities.add(ce);
                    }
                }
                if (candidateEntities.size() == 1) {
                    ent = complexName.get(complexName.indexOf(se));
                    // Not Ambigous -> This Simple ent fits just one complex entity;

                    complexName.remove(ent);
                    ent.incrementFrequency();
                    for (String fr : se.fileReference) {
                        ent.addFileReference(fr);
                    }
                    ent.addToFrequency(se.Frequency);
                    ent.alternativeForms.add(se.toString());
                    if (!ent.alternativeFormsWithFrequency.containsKey(se.toString())) {
                        ent.alternativeFormsWithFrequency.put(se.toString(), 1);
                        simpleName_dup.remove(se);
                    } else {
                        ent.alternativeFormsWithFrequency
                                .put(se.toString(), ent.alternativeFormsWithFrequency.get(se.toString()) + 1);
                        simpleName_dup.remove(se);
                    }
                    complexName.add(ent);
                } else if (candidateEntities.size() > 1) {
                    //Ambigous -> This Simple ent have more than one condidate

                    int containedInDocs = 0;
                    int sameFreqEnt = 0;
                    int maxFreq = 0;
                    ClusterEntity mostFrequent = new ClusterEntity();
                    ClusterEntity containedRef = new ClusterEntity();

                    for (ClusterEntity candidate : candidateEntities) {
                        if (candidate.fileReference.contains(fileReference)) {
                            containedInDocs++;
                            containedRef = candidate;
                        }
                        if (mostFrequent.Frequency < candidate.Frequency) {
                            mostFrequent = candidate;
                            sameFreqEnt = 0;
                        } else if (mostFrequent.Frequency == candidate.Frequency) {
                            sameFreqEnt++;
                        }
                    }

                    if (containedInDocs > 1) {
                        //System.out.println("Ambigous -> More complex with same reference");
                        if (sameFreqEnt > 1) {
                            //System.out.println("Ambigous -> More complex with same freq");
                        } else {
                            //Resolved with frequency! Most frequent is in mostFrequent
                            complexName.remove(mostFrequent);
                            mostFrequent.incrementFrequency();
                            for (String fr : se.fileReference) {
                                mostFrequent.addFileReference(fr);
                            }
                            mostFrequent.addToFrequency(se.Frequency);
                            mostFrequent.alternativeForms.add(se.toString());
                            if (!mostFrequent.alternativeFormsWithFrequency.containsKey(se.toString())) {
                                mostFrequent.alternativeFormsWithFrequency.put(se.toString(), 1);
                                simpleName_dup.remove(se);
                            } else {
                                mostFrequent.alternativeFormsWithFrequency.put(se.toString(),
                                        mostFrequent.alternativeFormsWithFrequency.get(se.toString()) + 1);
                                simpleName_dup.remove(se);
                            }
                            complexName.add(mostFrequent);
                        }
                    } else if (containedInDocs == 0) {
                        //  Ambigous -> No common reference
                        if (sameFreqEnt > 1) {
                            //Ambigous -> More complex with same freq
                        } else {
                            //Resolved with frequency! Most frequent is in mostFrequent
                            complexName.remove(mostFrequent);
                            mostFrequent.incrementFrequency();
                            for (String fr : se.fileReference) {
                                mostFrequent.addFileReference(fr);
                            }
                            mostFrequent.addToFrequency(se.Frequency);
                            mostFrequent.alternativeForms.add(se.toString());
                            if (!mostFrequent.alternativeFormsWithFrequency.containsKey(se.toString())) {
                                mostFrequent.alternativeFormsWithFrequency.put(se.toString(), 1);
                                simpleName_dup.remove(se);
                            } else {
                                mostFrequent.alternativeFormsWithFrequency.put(se.toString(),
                                        mostFrequent.alternativeFormsWithFrequency.get(se.toString()) + 1);
                                simpleName_dup.remove(se);
                            }
                            complexName.add(mostFrequent);
                        }
                    } else if (containedInDocs == 1) {
                        //Resolved with context!
                        complexName.remove(containedRef);
                        containedRef.incrementFrequency();
                        for (String fr : se.fileReference) {
                            containedRef.addFileReference(fr);
                        }
                        containedRef.addToFrequency(se.Frequency);
                        containedRef.alternativeForms.add(se.toString());
                        if (!containedRef.alternativeFormsWithFrequency.containsKey(se.toString())) {
                            containedRef.alternativeFormsWithFrequency.put(se.toString(), 1);
                            simpleName_dup.remove(se);
                        } else {
                            containedRef.alternativeFormsWithFrequency.put(se.toString(),
                                    containedRef.alternativeFormsWithFrequency.get(se.toString()) + 1);
                            simpleName_dup.remove(se);
                        }
                        complexName.add(containedRef);
                    }

                }

            }

            for (ClusterEntity com : complexName) {

                for (String form : com.getAllForms()) {

                    out.append(com.getMostFrequentCompositeForm() + "\t" + form + "\n");

                }

            }

            for (ClusterEntity com : simpleName_dup) {
                out.append(com.toString() + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return out;

    }

    static ArrayList<String> compileItalianNameList() {
        return new ArrayList<String>(
                Arrays.asList("Abaco", "Abbondanza", "Abbondanzio", "Abbondio", "Abdone", "Abelardo", "Abele",
                        "Abenzio", "Abibo", "Abramio", "Abramo", "Acacio", "Acario", "Accursio", "Achille", "Acilia",
                        "Acilio", "Aciscolo", "Acrisio", "Ada", "Adalardo", "Adalberta", "Adalberto", "Adalfredo",
                        "Adalgisa", "Adalgiso", "Adalrico", "Adamo", "Addo", "Addolorata", "Adelaide", "Adelardo",
                        "Adelasia", "Adelberto", "Adelchi", "Adele", "Adelfo", "Adelgardo", "Adelina", "Adelmo",
                        "Adeodato", "Adina", "Adolfo", "Adone", "Adria", "Adriana", "Adriano", "Adrione", "Afro",
                        "Agabio", "Agamennone", "Agape", "Agapito", "Agata", "Agazio", "Agenore", "Agesilao", "Agnese",
                        "Agostina", "Agostino", "Agrippa", "Aiace", "Aida", "Aidano", "Aimone", "Aladino", "Alamanno",
                        "Alano", "Alarico", "Alba", "Albano", "Alberico", "Alberta", "Alberto", "Albina", "Albino",
                        "Alboino", "Albrico", "Alceo", "Alceste", "Alcibiade", "Alcide", "Alcina", "Alcino", "Alda",
                        "Aldo", "Aldobrando", "Aleandro", "Aleardo", "Aleramo", "Alessandra", "Alessandro", "Alessia",
                        "Alessio", "Alfiero", "Alfio", "Alfonsa", "Alfonso", "Alfreda", "Alfredo", "Algiso", "Alice",
                        "Alida", "Alighiero", "Alina", "Allegra", "Alma", "Almerigo", "Almiro", "Aloisio", "Altea",
                        "Alvaro", "Alviero", "Alvise", "Amabile", "Amadeo", "Amalia", "Amanda", "Amando", "Amanzio",
                        "Amaranto", "Amata", "Amato", "Amatore", "Amauri", "Ambra", "Ambrogio", "Ambrosiano", "Amedeo",
                        "Amelia", "Amelio", "Amerigo", "Amico", "Amilcare", "Amina", "Amintore", "Amleto", "Amone",
                        "Amore", "Amos", "Ampelio", "Anacleto", "Anastasia", "Anatolia", "Ancilla", "Andrea",
                        "Andromeda", "Angela", "Angelica", "Angelo", "Aniceto", "Aniello", "Anita", "Anna", "Annabella",
                        "Annagrazia", "Annamaria", "Annibale", "Annunziata", "Ansaldo", "Anselmo", "Ansovino", "Antea",
                        "Antelmo", "Antero", "Antimo", "Antino", "Antioco", "Antonella", "Antonello", "Antonia",
                        "Antonio", "Apollina", "Apollinare", "Apollo", "Apollonia", "Appia", "Apuleio", "Aquilino",
                        "Arabella", "Araldo", "Aratone", "Arcadio", "Archimede", "Archippo", "Arcibaldo", "Ardito",
                        "Arduino", "Aresio", "Argelia", "Argimiro", "Argo", "Arialdo", "Arianna", "Ariberto", "Ariele",
                        "Ariosto", "Aris", "Aristarco", "Aristeo", "Aristide", "Aristione", "Aristo", "Aristofane",
                        "Aristotele", "Armando", "Armida", "Arminio", "Arnaldo", "Aronne", "Arrigo", "Artemisa",
                        "Arturo", "Ascanio", "Asdrubale", "Asella", "Asia", "Asimodeo", "Assunta", "Assunto", "Asterio",
                        "Astianatte", "Astrid", "Ataleo", "Atanasia", "Atanasio", "Athos", "Attila", "Attilano",
                        "Attilio", "Auberto", "Audace", "Augusto", "Aurelia", "Aureliano", "Aurelio", "Auro", "Aurora",
                        "Ausilia", "Ausiliatrice", "Ausilio", "Ave", "Averardo", "Aza", "Azeglio", "Azelia", "Azelio",
                        "Azzurra", "Babila", "Bacco", "Baldassarre", "Balderico", "Baldo", "Baldomero", "Baldovino",
                        "Bambina", "Barbara", "Barbarigo", "Bardo", "Bardomiano", "Barnaba", "Barsaba", "Barsimeo",
                        "Bartolo", "Bartolomea", "Bartolomeo", "Basileo", "Basilia", "Basilio", "Bassiano", "Bassilla",
                        "Bastiano", "Batilda", "Battista", "Beata", "Beato", "Beatrice", "Belina", "Bellino",
                        "Beltramo", "Benedetta", "Benedetto", "Beniamina", "Beniamino", "Benigna", "Benigno", "Benito",
                        "Benvenuta", "Benvenuto", "Berardo", "Berengario", "Berenice", "Bernadetta", "Bernardo",
                        "Beronico", "Bertoldo", "Bertolfo", "Betta", "Biagio", "Bianca", "Bibiana", "Bibiano", "Bice",
                        "Bindo", "Bino", "Birino", "Bonagiunta", "Bonaldo", "Bonaventura", "Bonavita", "Bonifacio",
                        "Bonito", "Boris", "Bortolo", "Brancaleone", "Brando", "Brigida", "Brigitta", "Bruna",
                        "Brunilde", "Bruno", "Bruto", "Caino", "Caio", "Calanico", "Callisto", "Calogera", "Calogero",
                        "Calpurnia", "Camelia", "Camilla", "Camillo", "Candida", "Candido", "Cantidio", "Canziano",
                        "Capitolina", "Carina", "Carla", "Carlo", "Carlotta", "Carmela", "Carmelo", "Carmen", "Carmine",
                        "Carola", "Carolina", "Caronte", "Carponio", "Casilda", "Casimira", "Casimiro", "Cassandra",
                        "Cassiano", "Cassio", "Cassiopea", "Casto", "Cataldo", "Catena", "Caterina", "Catullo", "Cecco",
                        "Cecilia", "Cecilio", "Celeste", "Celinia", "Celso", "Cesare", "Cesario", "Cherubino",
                        "Chiaffredo", "Chiara", "Cino", "Cinzia", "Cipriano", "Cirano", "Ciriaco", "Cirilla", "Cirillo",
                        "Cirino", "Ciro", "Clara", "Clarenzio", "Claudia", "Claudio", "Cleandro", "Clelia", "Clemente",
                        "Clemenzia", "Cleo", "Cleofe", "Cleonico", "Cleopatra", "Climaco", "Clinio", "Clodomiro",
                        "Clodoveo", "Cloe", "Clorinda", "Cointa", "Colmanno", "Colmazio", "Colomba", "Colombano",
                        "Colombo", "Concetta", "Concetto", "Concordio", "Consolata", "Cora", "Corbiniano", "Cordelia",
                        "Coreno", "Corinna", "Coriolano", "Cornelia", "Cornelio", "Coronato", "Corrado", "Cosimo",
                        "Cosma", "Costante", "Costantino", "Costanza", "Costanzo", "Cremenzio", "Crescente",
                        "Crescenzia", "Crescenzio", "Crespignano", "Crispino", "Cristaldo", "Cristiana", "Cristiano",
                        "Cristina", "Cristoforo", "Crocefissa", "Crocefisso", "Cronida", "Cunegonda", "Cuniberto",
                        "Cupido", "Cuzia", "Daciano", "Dacio", "Dafne",
                        "Dagoberto", "Dalida", "Dalila", "Dalmazio", "Damaso", "Damiana", "Damiano", "Damocle",
                        "Daniela", "Daniele", "Danilo", "Danio", "Dante", "Daria", "Dario", "Davide", "Davino",
                        "Deanna", "Debora", "Decimo", "Degna", "Delfina", "Delfino", "Delia", "Delinda", "Delizia",
                        "Demetria", "Demetrio", "Democrito", "Demostene", "Deodata", "Deodato", "Desdemona",
                        "Desiderata", "Desiderato", "Desiderio", "Devota", "Diamante", "Diana", "Dianora", "Didimo",
                        "Diego", "Diletta", "Dina", "Dino", "Diocleziano", "Diodata", "Diodoro", "Diogene", "Diomede",
                        "Dione", "Dionigi", "Dionisia", "Dionisio", "Divo", "Doda", "Dodato", "Dolores", "Domenica",
                        "Domenico", "Domezio", "Domiziano", "Donata", "Donatella", "Donatello", "Donato", "Donna",
                        "Dora", "Doriano", "Dorotea", "Doroteo", "Druina", "Duccio", "Duilio", "Dulina", "Durante",
                        "Ebe", "Eberardo", "Ecclesio", "Edda", "Edelberga", "Edgardo", "Edilberto", "Editta", "Edmondo",
                        "Edoardo", "Edvige", "Efisio", "Efrem", "Egeo", "Egidio", "Eginardo", "Egisto", "Egizia",
                        "Egle", "Elaide", "Elda", "Elena", "Eleonora", "Elettra", "Eleuterio", "Elia", "Eliana",
                        "Eliano", "Elide", "Elifio", "Eligio", "Elimena", "Elio", "Eliodoro", "Elisa", "Elisabetta",
                        "Elisea", "Eliseo", "Elita", "Ella", "Elmo", "Elogio", "Eloisa", "Elpidio", "Elsa", "Elvezio",
                        "Elvia", "Elvino", "Elvira", "Emanuela", "Emanuele", "Emidio", "Emilia", "Emiliana", "Emiliano",
                        "Emilio", "Emma", "Emmerico", "Empirio", "Endrigo", "Enea", "Enecone", "Enimia", "Ennio",
                        "Enrica", "Enrico", "Enzo", "Eracla", "Eraclide", "Eraldo", "Erardo", "Erasmo", "Erberto",
                        "Ercolano", "Ercole", "Erenia", "Eriberto", "Erico", "Ermanno", "Ermelinda", "Ermenegarda",
                        "Ermenegilda", "Ermenegildo", "Ermes", "Ermete", "Ermilo", "Erminia", "Erminio", "Ernesta",
                        "Ernesto", "Eros", "Ersilia", "Esaù", "Esmeralda", "Estella", "Ester", "Esterina", "Esuperio",
                        "Eterie", "Ettore", "Euclide", "Eufebio", "Eufemia", "Eufemio", "Eufrasia", "Eufronio",
                        "Eugenia", "Eugenio", "Eulalia", "Euridice", "Eusebia", "Eusebio", "Euseo", "Eustorgio",
                        "Eustosio", "Eutalia", "Eutalio", "Eva", "Evaldo", "Evandro", "Evangelina", "Evaristo",
                        "Evasio", "Evelina", "Everardo", "Evidio", "Evodio", "Evremondo", "Ezechiele", "Ezio",
                        "Fabiana", "Fabiano", "Fabio", "Fabiola", "Fabrizio", "Famiano", "Fatima", "Fausta", "Fausto",
                        "Fedele", "Federica", "Federico", "Fedora", "Fedro", "Felice", "Felicia", "Feliciano",
                        "Felicita", "Ferdinando", "Fermiano", "Fermo", "Fernanda", "Fernando", "Ferruccio", "Festo",
                        "Fiammetta", "Fidenziano", "Fidenzio", "Filiberto", "Filippa", "Filippo", "Filomena",
                        "Filomeno", "Fiordaliso", "Fiore", "Fiorella", "Fiorenza", "Fiorenziano", "Fiorenzo",
                        "Flaminia", "Flavia", "Flaviana", "Flaviano", "Flavio", "Fleano", "Flora", "Floriana",
                        "Floriano", "Floridia", "Florina", "Foca", "Folco", "Fortunata", "Fortunato", "Fosca", "Fosco",
                        "Franca", "Francesca", "Francesco", "Frido", "Frontiniano", "Fulberto", "Fulgenzio", "Fulvia",
                        "Fulvio", "Furio", "Furseo", "Fuscolo", "Gabino", "Gabriele", "Gabriella", "Gaetano",
                        "Gaglioffo", "Gaia", "Gaio", "Galatea", "Galdino", "Galeazzo", "Galileo", "Gallicano",
                        "Gandolfo", "Garimberto", "Gaspare", "Gastone", "Gaudenzia", "Gaudenzio", "Gaudino", "Gautiero",
                        "Gavino", "Gedeone", "Gelsomina", "Geltrude", "Geminiano", "Gemma", "Generosa", "Generoso",
                        "Genesia", "Genesio", "Gennaro", "Genoveffa", "Gentile", "Genziano", "Gerardo", "Gerasimo",
                        "Geremia", "Gerino", "Germana", "Germano", "Gerolamo", "Geronimo", "Geronzio", "Gertrude",
                        "Gervasio", "Gesualdo", "Gherardo", "Ghita", "Giacinta", "Giacinto", "Giacobbe", "Giacomo",
                        "Giada", "Giadero", "Giambattista", "Gianbattista", "Giancarlo", "Giandomenico", "Gianfranco",
                        "Gianluca", "Gianluigi", "Gianmarco", "Gianmaria", "Gianmario", "Gianni", "Gianpaolo",
                        "Gianpiero", "Gianpietro", "Gianuario", "Giasone", "Gigliola", "Gilberto", "Gilda", "Gildo",
                        "Giliola", "Gillo", "Gineto", "Ginevra", "Gino", "Gioacchina", "Gioacchino", "Giobbe",
                        "Gioberto", "Gioconda", "Giocondo", "Gioele", "Gioia", "Giona", "Gionata", "Giordano",
                        "Giorgia", "Giorgio", "Giosuè", "Giosuele", "Giotto", "Giovanna", "Giovanni", "Giove",
                        "Gioventino", "Giovenzio", "Girardo", "Girolamo", "Gisella", "Giuda", "Giuditta", "Giulia",
                        "Giuliana", "Giuliano", "Giulio", "Giulitta", "Giuseppe", "Giusta", "Giustiniano", "Giusto",
                        "Glauco", "Glenda", "Gloria", "Godeberta", "Godiva", "Goffredo", "Golia", "Gomberto",
                        "Gondulfo", "Gonerio", "Gonzaga", "Gordiano", "Gosto", "Gottardo", "Graciliano", "Grato",
                        "Grazia", "Graziana", "Graziano", "Graziella", "Gregorio", "Greta", "Grimaldo", "Griselda",
                        "Gualberto", "Gualtiero", "Guelfo", "Guenda", "Guendalina", "Guerrino", "Guglielmo", "Guiberto",
                        "Guido", "Guiscardo", "Gumesindo", "Gundelinda", "Gustavo", "Iacopo", "Iacopone", "Ianira",
                        "Icaro", "Icilio", "Ida", "Idea", "Ido", "Iginia", "Iginio", "Igino", "Ignazio", "Igor",
                        "Ilaria", "Ilario", "Ilda", "Ildebrando", "Ildefonso", "Ildegarda", "Ildegonda", "Ileana",
                        "Ilenia", "Ilia", "Illidio", "Illuminato", "Ilva", "Imelda",
                        "Immacolata", "Immacolato", "Incoronata", "Indro", "Ines", "Innocente", "Innocenza",
                        "Innocenzo", "Iolanda", "Iole", "Iona", "Ippocrate", "Ippolito", "Irene", "Ireneo", "Iride",
                        "Iris", "Irma", "Irmina", "Isa", "Isabella", "Isacco", "Isaia", "Ischirione", "Iside",
                        "Isidora", "Isidoro", "Isotta", "Italia", "Italo", "Ivan", "Ivano", "Ivanoe", "Ivetta", "Ivo",
                        "Ivone", "Ladislao", "Lamberto", "Lancilotto", "Landolfo", "Lanfranco", "Lapo", "Lara", "Laura",
                        "Laurentino", "Lauriano", "Lautone", "Lavinia", "Lavinio", "Lazzaro", "Lea", "Leandro", "Leda",
                        "Lelia", "Lena", "Leo", "Leonardo", "Leone", "Leonia", "Leonida", "Leonilda", "Leonio",
                        "Leontina", "Leonzio", "Leopardo", "Leopoldo", "Letizia", "Letterio", "Lia", "Liana",
                        "Liberata", "Liberato", "Liberatore", "Liberio", "Libero", "Liberto", "Liboria", "Liborio",
                        "Licia", "Lidania", "Lidia", "Lidio", "Lieto", "Liliana", "Linda", "Lino", "Lisa", "Lisandro",
                        "Livia", "Liviana", "Livino", "Livio", "Lodovica", "Lodovico", "Loredana", "Lorella", "Lorena",
                        "Loreno", "Lorenza", "Lorenzo", "Loretta", "Loriana", "Loris", "Luana", "Luca", "Luce", "Lucia",
                        "Luciana", "Luciano", "Lucilla", "Lucio", "Lucrezia", "Ludano", "Ludovica", "Ludovico", "Luigi",
                        "Luigia", "Luisa", "Luminosa", "Luna", "Macaria", "Macario", "Maccabeo", "Maddalena", "Mafalda",
                        "Maffeo", "Magda", "Maggiorino", "Magno", "Maida", "Maiorico", "Malco", "Mamante", "Mancio",
                        "Manetto", "Manfredo", "Manilio", "Manlio", "Mansueto", "Manuela", "Manuele", "Mara", "Marana",
                        "Marcella", "Marcello", "Marciano", "Marco", "Mareta", "Margherita", "Maria", "Marianna",
                        "Mariano", "Marica", "Mariella", "Marilena", "Marina", "Marinella", "Marinetta", "Marino",
                        "Mario", "Marisa", "Marita", "Marolo", "Marta", "Martina", "Martino", "Maruta", "Marzia",
                        "Marzio", "Massima", "Massimiliano", "Massimo", "Matilde", "Matroniano", "Matteo", "Mattia",
                        "Maura", "Maurilio", "Maurizio", "Mauro", "Medardo", "Medoro", "Melania", "Melanio",
                        "Melchiade", "Melchiorre", "Melezio", "Melissa", "Melitina", "Menardo", "Menelao", "Meneo",
                        "Mennone", "Menodora", "Mercede", "Mercurio", "Messalina", "Metello", "Metrofane", "Mia",
                        "Michela", "Michelangelo", "Michele", "Milena", "Milo", "Mimma", "Mina", "Minerva", "Minervina",
                        "Minervino", "Miranda", "Mirco", "Mirella", "Miriam", "Mirko", "Mirocleto", "Mirta", "Misaele",
                        "Modesto", "Moira", "Monaldo", "Monica", "Monitore", "Morena", "Moreno", "Morgana", "Mosè",
                        "Muziano", "Nadia", "Namazio", "Napoleone", "Narciso", "Narseo", "Narsete", "Natale", "Natalia",
                        "Natalina", "Nazario", "Nazzareno", "Nazzaro", "Neiva", "Neopolo", "Neoterio", "Nerea", "Nereo",
                        "Neri", "Nestore", "Nicarete", "Nicea", "Niceforo", "Niceto", "Nicezio", "Nicla", "Nico",
                        "Nicodemo", "Nicola", "Nicoletta", "Nicolò", "Nilde", "Nina", "Ninfa", "Niniano", "Nino",
                        "Nives", "Noè", "Noemi", "Norberto", "Norina", "Norma", "Nostriano", "Novella", "Nuccia",
                        "Nunziata", "Nunzio", "Oddone", "Oderico", "Odetta", "Odidone", "Odilia", "Odorico", "Ofelia",
                        "Olga", "Olimpia", "Olimpio", "Olinda", "Olindo", "Olivia", "Oliviera", "Oliviero", "Omar",
                        "Ombretta", "Omero", "Ondina", "Onesta", "Onesto", "Onofrio", "Onorata", "Onorina", "Onorino",
                        "Onorio", "Orazio", "Orchidea", "Orenzio", "Oreste", "Orfeo", "Oriana", "Orietta", "Orio",
                        "Orlando", "Ornella", "Oronzo", "Orsino", "Orso", "Orsola", "Orsolina", "Ortensia", "Ortensio",
                        "Osanna", "Oscar", "Osmondo", "Osvaldo", "Otello", "Otilia", "Ottaviano", "Ottavio", "Ottilia",
                        "Ottone", "Ovidio", "Paciano", "Pacifico", "Pacomio", "Palatino", "Palladia", "Palladio",
                        "Palmazio", "Palmira", "Pamela", "Pammachio", "Pancario", "Pancrazio", "Panfilo", "Pantaleo",
                        "Pantaleone", "Paola", "Paolo", "Pardo", "Paride", "Parmenio", "Pasquale", "Paterniano",
                        "Patrizia", "Patrizio", "Patroclo", "Pauside", "Pelagia", "Peleo", "Pellegrino", "Penelope",
                        "Pericle", "Perla", "Perseo", "Petronilla", "Petronio", "Pia", "Pier", "Piera", "Pierangelo",
                        "Piergiorgio", "Pierluigi", "Piermarco", "Piero", "Piersilvio", "Pietro", "Pio", "Pippo",
                        "Placida", "Placido", "Platone", "Plinio", "Plutarco", "Polidoro", "Polifemo", "Polissena",
                        "Pollione", "Pompeo", "Pomponio", "Ponziano", "Ponzio", "Porfirio", "Porzia", "Porziano",
                        "Postumio", "Prassede", "Priamo", "Primo", "Prisca", "Priscilla", "Prisco", "Procopio",
                        "Proserpina", "Prospera", "Prospero", "Protasio", "Proteo", "Prudenzia", "Prudenzio", "Publio",
                        "Pupolo", "Pusicio", "Quartilla", "Quarto", "Quasimodo", "Querano", "Quieta", "Quintiliano",
                        "Quintilio", "Quintino", "Quinziano", "Quinzio", "Quirino", "Quiteria", "Rachele", "Radolfo",
                        "Raffaele", "Raffaella", "Raide", "Raimondo", "Rainaldo", "Rainelda", "Ramiro", "Raniero",
                        "Ranolfo", "Rebecca", "Regina", "Reginaldo", "Regolo", "Remigio", "Remo", "Remondo", "Renata",
                        "Renato", "Renzo", "Respicio", "Ricario", "Riccarda", "Riccardo", "Richelmo", "Rina", "Rinaldo",
                        "Rino", "Rita", "Robaldo", "Roberta", "Roberto", "Rocco", "Rodiano", "Rodolfo", "Rodrigo",
                        "Rolando", "Rolfo", "Romana", "Romano", "Romeo", "Romero", "Romilda",
                        "Romina", "Romoaldo", "Romola", "Romolo", "Romualdo", "Rosa", "Rosalia", "Rosalinda",
                        "Rosamunda", "Rosanna", "Rosario", "Rosita", "Rosmunda", "Rossana", "Rossella", "Rubiano",
                        "Rufina", "Rufino", "Rufo", "Ruggero", "Ruperto", "Rutilo", "Saba", "Sabato", "Sabazio",
                        "Sabele", "Sabina", "Sabino", "Sabrina", "Saffiro", "Saffo", "Saladino", "Salomè", "Salomone",
                        "Salustio", "Salvatore", "Salvo", "Samanta", "Samona", "Samuele", "Sandra", "Sandro", "Sansone",
                        "Sante", "Santina", "Santo", "Sapiente", "Sara", "Sarbello", "Saturniano", "Saturnino", "Saul",
                        "Saverio", "Savina", "Savino", "Scolastica", "Sebastiana", "Sebastiano", "Seconda",
                        "Secondiano", "Secondina", "Secondo", "Sefora", "Selene", "Selvaggia", "Semiramide",
                        "Semplicio", "Sempronio", "Senesio", "Senofonte", "Serafina", "Serafino", "Serapione", "Serena",
                        "Sergio", "Servidio", "Serviliano", "Sesto", "Settimio", "Settimo", "Severa", "Severiano",
                        "Severino", "Severo", "Sibilla", "Sico", "Sicuro", "Sidonia", "Sidonio", "Sigfrido",
                        "Sigismondo", "Silvana", "Silvano", "Silverio", "Silvestro", "Silvia", "Silvio", "Simeone",
                        "Simona", "Simone", "Simonetta", "Sinesio", "Sinfronio", "Sireno", "Siriano", "Siricio", "Siro",
                        "Sisto", "Soave", "Soccorso", "Socrate", "Sofia", "Sofronia", "Solange", "Solocone", "Sonia",
                        "Sostene", "Sosteneo", "Sostrato", "Spano", "Spartaco", "Speranzio", "Stanislao", "Stefania",
                        "Stefano", "Stella", "Stiliano", "Stiriaco", "Surano", "Susanna", "Sveva", "Sviturno", "Tabita",
                        "Taddeo", "Taide", "Tamara", "Tammaro", "Tancredi", "Tarcisio", "Tarquinia", "Tarsilla",
                        "Tarso", "Taziana", "Taziano", "Tazio", "Tea", "Tecla", "Telchide", "Telemaco", "Telica",
                        "Temistocle", "Teobaldo", "Teodata", "Teodolinda", "Teodora", "Teodoro", "Teodosio", "Teodoto",
                        "Teogene", "Terenzio", "Teresa", "Terzo", "Tesauro", "Tesifonte", "Teudosia", "Tibaldo",
                        "Tiberio", "Tiburzio", "Ticone", "Timoteo", "Tina", "Tirone", "Tito", "Tiziana", "Tiziano",
                        "Tizio", "Tobia", "Tolomeo", "Tommaso", "Torquato", "Tosca", "Tosco", "Tranquillo", "Trasea",
                        "Tristano", "Tullia", "Tulliano", "Tullio", "Turi", "Turibio", "Tussio", "Ubaldo", "Ubertino",
                        "Uberto", "Ugo", "Ugolina", "Ugolino", "Uguccione", "Ulberto", "Ulderico", "Ulfa", "Ulfo",
                        "Ulisse", "Uliva", "Ulpiano", "Ulrico", "Ulstano", "Ultimo", "Umberto", "Umile", "Unna",
                        "Uranio", "Urbano", "Urdino", "Uriele", "Ursicio", "Ursino", "Ursmaro", "Vala", "Valente",
                        "Valentina", "Valentino", "Valeria", "Valeriana", "Valeriano", "Valerico", "Valerio",
                        "Valfredo", "Valfrido", "Valtena", "Valter", "Vanda", "Vanessa", "Vanna", "Varo", "Vasco",
                        "Vedasto", "Velio", "Venanzio", "Venceslao", "Venera", "Veneranda", "Venerando", "Venerio",
                        "Ventura", "Venusta", "Venustiano", "Venusto", "Vera", "Verano", "Verdiana", "Verecondo",
                        "Verenzio", "Veriana", "Veridiana", "Veronica", "Verulo", "Vespasiano", "Vezio", "Vidiano",
                        "Vidone", "Vilfredo", "Viliana", "Viliberto", "Vilma", "Vincenzo", "Vindonio", "Vinebaldo",
                        "Vinfrido", "Vinicio", "Viola", "Violante", "Virgilio", "Virginia", "Virginio", "Virone",
                        "Vissia", "Vitale", "Vitalico", "Vito", "Vittore", "Vittoria", "Vittorio", "Vivaldo", "Viviana",
                        "Viviano", "Vladimiro", "Vodingo", "Volfango", "Vulmaro", "Vulpiano", "Vunibaldo", "Walter",
                        "Wanda", "Zabedeo", "Zabina", "Zaccaria", "Zaccheo", "Zaira", "Zama", "Zanita", "Zanobi",
                        "Zarina", "Zefiro", "Zelinda", "Zena", "Zenaide", "Zenebio", "Zeno", "Zenobia", "Zenobio",
                        "Zenone", "Zetico", "Zita", "Zoe", "Zoilo", "Zosima", "Zosimo"));
    }

    static ArrayList<String> compileEnglishNameList() {
        return new ArrayList<String>(
                Arrays.asList("Aaron", "Abbey", "Abbie", "Abby", "Abdul", "Abe", "Abel", "Abigail", "Abraham", "Abram",
                        "Ada", "Adah", "Adalberto", "Adaline", "Adam", "Adan", "Addie", "Adela", "Adelaida", "Adelaide",
                        "Adele", "Adelia", "Adelina", "Adeline", "Adell", "Adella", "Adelle", "Adena", "Adina",
                        "Adolfo", "Adolph", "Adria", "Adrian", "Adriana", "Adriane", "Adrianna", "Adrianne", "Adrien",
                        "Adriene", "Adrienne", "Afton", "Agatha", "Agnes", "Agnus", "Agripina", "Agueda", "Agustin",
                        "Agustina", "Ahmad", "Ahmed", "Ai", "Aida", "Aide", "Aiko", "Aileen", "Ailene", "Aimee",
                        "Aisha", "Aja", "Akiko", "Akilah", "Al", "Alaina", "Alaine", "Alan", "Alana", "Alane", "Alanna",
                        "Alayna", "Alba", "Albert", "Alberta", "Albertha", "Albertina", "Albertine", "Alberto",
                        "Albina", "Alda", "Alden", "Aldo", "Alease", "Alec", "Alecia", "Aleen", "Aleida", "Aleisha",
                        "Alejandra", "Alejandrina", "Alejandro", "Alena", "Alene", "Alesha", "Aleshia", "Alesia",
                        "Alessandra", "Aleta", "Aletha", "Alethea", "Alethia", "Alex", "Alexa", "Alexander",
                        "Alexandra", "Alexandria", "Alexia", "Alexis", "Alfonso", "Alfonzo", "Alfred", "Alfreda",
                        "Alfredia", "Alfredo", "Ali", "Alia", "Alica", "Alice", "Alicia", "Alida", "Alina", "Aline",
                        "Alisa", "Alise", "Alisha", "Alishia", "Alisia", "Alison", "Alissa", "Alita", "Alix", "Aliza",
                        "Alla", "Allan", "Alleen", "Allegra", "Allen", "Allena", "Allene", "Allie", "Alline", "Allison",
                        "Allyn", "Allyson", "Alma", "Almeda", "Almeta", "Alona", "Alonso", "Alonzo", "Alpha",
                        "Alphonse", "Alphonso", "Alta", "Altagracia", "Altha", "Althea", "Alton", "Alva", "Alvaro",
                        "Alvera", "Alverta", "Alvin", "Alvina", "Alyce", "Alycia", "Alysa", "Alyse", "Alysha", "Alysia",
                        "Alyson", "Alyssa", "Amada", "Amado", "Amal", "Amalia", "Amanda", "Amber", "Amberly", "Ambrose",
                        "Amee", "Amelia", "America", "Ami", "Amie", "Amiee", "Amina", "Amira", "Ammie", "Amos",
                        "Amparo", "Amy", "An", "Ana", "Anabel", "Analisa", "Anamaria", "Anastacia", "Anastasia",
                        "Andera", "Anderson", "Andra", "Andre", "Andrea", "Andreas", "Andree", "Andres", "Andrew",
                        "Andria", "Andy", "Anette", "Angel", "Angela", "Angele", "Angelena", "Angeles", "Angelia",
                        "Angelic", "Angelica", "Angelika", "Angelina", "Angeline", "Angelique", "Angelita", "Angella",
                        "Angelo", "Angelyn", "Angie", "Angila", "Angla", "Angle", "Anglea", "Anh", "Anibal", "Anika",
                        "Anisa", "Anisha", "Anissa", "Anita", "Anitra", "Anja", "Anjanette", "Anjelica", "Ann", "Anna",
                        "Annabel", "Annabell", "Annabelle", "Annalee", "Annalisa", "Annamae", "Annamaria", "Annamarie",
                        "Anne", "Anneliese", "Annelle", "Annemarie", "Annett", "Annetta", "Annette", "Annice", "Annie",
                        "Annika", "Annis", "Annita", "Annmarie", "Anthony", "Antione", "Antionette", "Antoine",
                        "Antoinette", "Anton", "Antone", "Antonetta", "Antonette", "Antonia", "Antonietta", "Antonina",
                        "Antonio", "Antony", "Antwan", "Anya", "Apolonia", "April", "Apryl", "Ara", "Araceli",
                        "Aracelis", "Aracely", "Arcelia", "Archie", "Ardath", "Ardelia", "Ardell", "Ardella", "Ardelle",
                        "Arden", "Ardis", "Ardith", "Aretha", "Argelia", "Argentina", "Ariana", "Ariane", "Arianna",
                        "Arianne", "Arica", "Arie", "Ariel", "Arielle", "Arla", "Arlean", "Arleen", "Arlen", "Arlena",
                        "Arlene", "Arletha", "Arletta", "Arlette", "Arlie", "Arlinda", "Arline", "Arlyne", "Armand",
                        "Armanda", "Armandina", "Armando", "Armida", "Arminda", "Arnetta", "Arnette", "Arnita",
                        "Arnold", "Arnoldo", "Arnulfo", "Aron", "Arron", "Art", "Arthur", "Artie", "Arturo", "Arvilla",
                        "Asa", "Asha", "Ashanti", "Ashely", "Ashlea", "Ashlee", "Ashleigh", "Ashley", "Ashli", "Ashlie",
                        "Ashly", "Ashlyn", "Ashton", "Asia", "Asley", "Assunta", "Astrid", "Asuncion", "Athena",
                        "Aubrey", "Audie", "Audra", "Audrea", "Audrey", "Audria", "Audrie", "Audry", "August",
                        "Augusta", "Augustina", "Augustine", "Augustus", "Aundrea", "Aura", "Aurea", "Aurelia",
                        "Aurelio", "Aurora", "Aurore", "Austin", "Autumn", "Ava", "Avelina", "Avery", "Avis", "Avril",
                        "Awilda", "Ayako", "Ayana", "Ayanna", "Ayesha", "Azalee", "Azucena", "Azzie", "Babara",
                        "Babette", "Bailey", "Bambi", "Bao", "Barabara", "Barb", "Barbar", "Barbara", "Barbera",
                        "Barbie", "Barbra", "Bari", "Barney", "Barrett", "Barrie", "Barry", "Bart", "Barton", "Basil",
                        "Basilia", "Bea", "Beata", "Beatrice", "Beatris", "Beatriz", "Beau", "Beaulah", "Bebe", "Becki",
                        "Beckie", "Becky", "Bee", "Belen", "Belia", "Belinda", "Belkis", "Bell", "Bella", "Belle",
                        "Belva", "Ben", "Benedict", "Benita", "Benito", "Benjamin", "Bennett", "Bennie", "Benny",
                        "Benton", "Berenice", "Berna", "Bernadette", "Bernadine", "Bernard", "Bernarda", "Bernardina",
                        "Bernardine", "Bernardo", "Berneice", "Bernetta", "Bernice", "Bernie", "Berniece", "Bernita",
                        "Berry", "Bert", "Berta", "Bertha", "Bertie", "Bertram", "Beryl", "Bess", "Bessie", "Beth",
                        "Bethanie", "Bethann", "Bethany", "Bethel", "Betsey", "Betsy", "Bette", "Bettie", "Bettina",
                        "Betty", "Bettyann", "Bettye", "Beula", "Beulah", "Bev", "Beverlee", "Beverley", "Beverly",
                        "Bianca", "Bibi", "Bill", "Billi", "Billie", "Billy",
                        "Billye", "Birdie", "Birgit", "Blaine", "Blair", "Blake", "Blanca", "Blanch", "Blanche",
                        "Blondell", "Blossom", "Blythe", "Bo", "Bob", "Bobbi", "Bobbie", "Bobby", "Bobbye", "Bobette",
                        "Bok", "Bong", "Bonita", "Bonnie", "Bonny", "Booker", "Boris", "Boyce", "Boyd", "Brad",
                        "Bradford", "Bradley", "Bradly", "Brady", "Brain", "Branda", "Brande", "Brandee", "Branden",
                        "Brandi", "Brandie", "Brandon", "Brandy", "Brant", "Breana", "Breann", "Breanna", "Breanne",
                        "Bree", "Brenda", "Brendan", "Brendon", "Brenna", "Brent", "Brenton", "Bret", "Brett", "Brian",
                        "Briana", "Brianna", "Brianne", "Brice", "Bridget", "Bridgett", "Bridgette", "Brigette",
                        "Brigid", "Brigida", "Brigitte", "Brinda", "Britany", "Britney", "Britni", "Britt", "Britta",
                        "Brittaney", "Brittani", "Brittanie", "Brittany", "Britteny", "Brittney", "Brittni", "Brittny",
                        "Brock", "Broderick", "Bronwyn", "Brook", "Brooke", "Brooks", "Bruce", "Bruna", "Brunilda",
                        "Bruno", "Bryan", "Bryanna", "Bryant", "Bryce", "Brynn", "Bryon", "Buck", "Bud", "Buddy",
                        "Buena", "Buffy", "Buford", "Bula", "Bulah", "Bunny", "Burl", "Burma", "Burt", "Burton",
                        "Buster", "Byron", "Caitlin", "Caitlyn", "Calandra", "Caleb", "Calista", "Callie", "Calvin",
                        "Camelia", "Camellia", "Cameron", "Cami", "Camie", "Camila", "Camilla", "Camille", "Cammie",
                        "Cammy", "Candace", "Candance", "Candelaria", "Candi", "Candice", "Candida", "Candie", "Candis",
                        "Candra", "Candy", "Candyce", "Caprice", "Cara", "Caren", "Carey", "Cari", "Caridad", "Carie",
                        "Carin", "Carina", "Carisa", "Carissa", "Carita", "Carl", "Carla", "Carlee", "Carleen",
                        "Carlena", "Carlene", "Carletta", "Carley", "Carli", "Carlie", "Carline", "Carlita", "Carlo",
                        "Carlos", "Carlota", "Carlotta", "Carlton", "Carly", "Carlyn", "Carma", "Carman", "Carmel",
                        "Carmela", "Carmelia", "Carmelina", "Carmelita", "Carmella", "Carmelo", "Carmen", "Carmina",
                        "Carmine", "Carmon", "Carol", "Carola", "Carolann", "Carole", "Carolee", "Carolin", "Carolina",
                        "Caroline", "Caroll", "Carolyn", "Carolyne", "Carolynn", "Caron", "Caroyln", "Carri", "Carrie",
                        "Carrol", "Carroll", "Carry", "Carson", "Carter", "Cary", "Caryl", "Carylon", "Caryn",
                        "Casandra", "Casey", "Casie", "Casimira", "Cassandra", "Cassaundra", "Cassey", "Cassi",
                        "Cassidy", "Cassie", "Cassondra", "Cassy", "Catalina", "Catarina", "Caterina", "Catharine",
                        "Catherin", "Catherina", "Catherine", "Cathern", "Catheryn", "Cathey", "Cathi", "Cathie",
                        "Cathleen", "Cathrine", "Cathryn", "Cathy", "Catina", "Catrice", "Catrina", "Cayla", "Cecelia",
                        "Cecil", "Cecila", "Cecile", "Cecilia", "Cecille", "Cecily", "Cedric", "Cedrick", "Celena",
                        "Celesta", "Celeste", "Celestina", "Celestine", "Celia", "Celina", "Celinda", "Celine", "Celsa",
                        "Ceola", "Cesar", "Chad", "Chadwick", "Chae", "Chan", "Chana", "Chance", "Chanda", "Chandra",
                        "Chanel", "Chanell", "Chanelle", "Chang", "Chantal", "Chantay", "Chante", "Chantel", "Chantell",
                        "Chantelle", "Chara", "Charis", "Charise", "Charissa", "Charisse", "Charita", "Charity",
                        "Charla", "Charleen", "Charlena", "Charlene", "Charles", "Charlesetta", "Charlette", "Charley",
                        "Charlie", "Charline", "Charlott", "Charlotte", "Charlsie", "Charlyn", "Charmain", "Charmaine",
                        "Charolette", "Chas", "Chase", "Chasidy", "Chasity", "Chassidy", "Chastity", "Chau", "Chauncey",
                        "Chaya", "Chelsea", "Chelsey", "Chelsie", "Cher", "Chere", "Cheree", "Cherelle", "Cheri",
                        "Cherie", "Cherilyn", "Cherise", "Cherish", "Cherly", "Cherlyn", "Cherri", "Cherrie", "Cherry",
                        "Cherryl", "Chery", "Cheryl", "Cheryle", "Cheryll", "Chester", "Chet", "Cheyenne", "Chi",
                        "Chia", "Chieko", "Chin", "China", "Ching", "Chiquita", "Chloe", "Chong", "Chris", "Chrissy",
                        "Christa", "Christal", "Christeen", "Christel", "Christen", "Christena", "Christene", "Christi",
                        "Christia", "Christian", "Christiana", "Christiane", "Christie", "Christin", "Christina",
                        "Christine", "Christinia", "Christoper", "Christopher", "Christy", "Chrystal", "Chu", "Chuck",
                        "Chun", "Chung", "Ciara", "Cicely", "Ciera", "Cierra", "Cinda", "Cinderella", "Cindi", "Cindie",
                        "Cindy", "Cinthia", "Cira", "Clair", "Claire", "Clara", "Clare", "Clarence", "Claretha",
                        "Claretta", "Claribel", "Clarice", "Clarinda", "Clarine", "Claris", "Clarisa", "Clarissa",
                        "Clarita", "Clark", "Classie", "Claud", "Claude", "Claudette", "Claudia", "Claudie", "Claudine",
                        "Claudio", "Clay", "Clayton", "Clelia", "Clemencia", "Clement", "Clemente", "Clementina",
                        "Clementine", "Clemmie", "Cleo", "Cleopatra", "Cleora", "Cleotilde", "Cleta", "Cletus",
                        "Cleveland", "Cliff", "Clifford", "Clifton", "Clint", "Clinton", "Clora", "Clorinda",
                        "Clotilde", "Clyde", "Codi", "Cody", "Colby", "Cole", "Coleen", "Coleman", "Colene", "Coletta",
                        "Colette", "Colin", "Colleen", "Collen", "Collene", "Collette", "Collin", "Colton", "Columbus",
                        "Concepcion", "Conception", "Concetta", "Concha", "Conchita", "Connie", "Conrad", "Constance",
                        "Consuela", "Consuelo", "Contessa", "Cora", "Coral", "Coralee", "Coralie", "Corazon",
                        "Cordelia", "Cordell", "Cordia", "Cordie", "Coreen", "Corene",
                        "Coretta", "Corey", "Cori", "Corie", "Corina", "Corine", "Corinna", "Corinne", "Corliss",
                        "Cornelia", "Cornelius", "Cornell", "Corrie", "Corrin", "Corrina", "Corrine", "Corrinne",
                        "Cortez", "Cortney", "Cory", "Courtney", "Coy", "Craig", "Creola", "Cris", "Criselda", "Crissy",
                        "Crista", "Cristal", "Cristen", "Cristi", "Cristie", "Cristin", "Cristina", "Cristine",
                        "Cristobal", "Cristopher", "Cristy", "Cruz", "Crysta", "Crystal", "Crystle", "Cuc", "Curt",
                        "Curtis", "Cyndi", "Cyndy", "Cynthia", "Cyril", "Cyrstal", "Cyrus", "Cythia", "Dacia", "Dagmar",
                        "Dagny", "Dahlia", "Daina", "Daine", "Daisey", "Daisy", "Dakota", "Dale", "Dalene", "Dalia",
                        "Dalila", "Dallas", "Dalton", "Damaris", "Damian", "Damien", "Damion", "Damon", "Dan", "Dana",
                        "Danae", "Dane", "Danelle", "Danette", "Dani", "Dania", "Danial", "Danica", "Daniel", "Daniela",
                        "Daniele", "Daniell", "Daniella", "Danielle", "Danika", "Danille", "Danilo", "Danita", "Dann",
                        "Danna", "Dannette", "Dannie", "Dannielle", "Danny", "Dante", "Danuta", "Danyel", "Danyell",
                        "Danyelle", "Daphine", "Daphne", "Dara", "Darby", "Darcel", "Darcey", "Darci", "Darcie",
                        "Darcy", "Darell", "Daren", "Daria", "Darin", "Dario", "Darius", "Darla", "Darleen", "Darlena",
                        "Darlene", "Darline", "Darnell", "Daron", "Darrel", "Darrell", "Darren", "Darrick", "Darrin",
                        "Darron", "Darryl", "Darwin", "Daryl", "Dave", "David", "Davida", "Davina", "Davis", "Dawn",
                        "Dawna", "Dawne", "Dayle", "Dayna", "Daysi", "Deadra", "Dean", "Deana", "Deandra", "Deandre",
                        "Deandrea", "Deane", "Deangelo", "Deann", "Deanna", "Deanne", "Deb", "Debbi", "Debbie",
                        "Debbra", "Debby", "Debera", "Debi", "Debora", "Deborah", "Debra", "Debrah", "Debroah", "Dede",
                        "Dedra", "Dee", "Deeann", "Deeanna", "Deedee", "Deedra", "Deena", "Deetta", "Deidra", "Deidre",
                        "Deirdre", "Deja", "Del", "Delaine", "Delana", "Delbert", "Delcie", "Delena", "Delfina",
                        "Delia", "Delicia", "Delila", "Delilah", "Delinda", "Delisa", "Dell", "Della", "Delma",
                        "Delmar", "Delmer", "Delmy", "Delois", "Deloise", "Delora", "Deloras", "Delores", "Deloris",
                        "Delorse", "Delpha", "Delphia", "Delphine", "Delsie", "Delta", "Demarcus", "Demetra",
                        "Demetria", "Demetrice", "Demetrius", "Dena", "Denae", "Deneen", "Denese", "Denice", "Denis",
                        "Denise", "Denisha", "Denisse", "Denita", "Denna", "Dennis", "Dennise", "Denny", "Denver",
                        "Denyse", "Deon", "Deonna", "Derek", "Derick", "Derrick", "Deshawn", "Desirae", "Desire",
                        "Desiree", "Desmond", "Despina", "Dessie", "Destiny", "Detra", "Devin", "Devon", "Devona",
                        "Devora", "Devorah", "Dewayne", "Dewey", "Dewitt", "Dexter", "Dia", "Diamond", "Dian", "Diana",
                        "Diane", "Diann", "Dianna", "Dianne", "Dick", "Diedra", "Diedre", "Diego", "Dierdre", "Digna",
                        "Dillon", "Dimple", "Dina", "Dinah", "Dino", "Dinorah", "Dion", "Dione", "Dionna", "Dionne",
                        "Dirk", "Divina", "Dixie", "Dodie", "Dollie", "Dolly", "Dolores", "Doloris", "Domenic",
                        "Domenica", "Dominga", "Domingo", "Dominic", "Dominica", "Dominick", "Dominique", "Dominque",
                        "Domitila", "Domonique", "Don", "Dona", "Donald", "Donella", "Donetta", "Donette", "Dong",
                        "Donita", "Donn", "Donna", "Donnell", "Donnetta", "Donnette", "Donnie", "Donny", "Donovan",
                        "Donte", "Donya", "Dora", "Dorathy", "Dorcas", "Doreatha", "Doreen", "Dorene", "Doretha",
                        "Dorethea", "Doretta", "Dori", "Doria", "Dorian", "Dorie", "Dorinda", "Dorine", "Doris",
                        "Dorla", "Dorotha", "Dorothea", "Dorothy", "Dorris", "Dorsey", "Dortha", "Dorthea", "Dorthey",
                        "Dorthy", "Dot", "Dottie", "Dotty", "Doug", "Douglas", "Douglass", "Dovie", "Doyle", "Dreama",
                        "Drema", "Drew", "Drucilla", "Drusilla", "Duane", "Dudley", "Dulce", "Dulcie", "Duncan", "Dung",
                        "Dusti", "Dustin", "Dusty", "Dwain", "Dwana", "Dwayne", "Dwight", "Dyan", "Dylan", "Earl",
                        "Earle", "Earlean", "Earleen", "Earlene", "Earlie", "Earline", "Earnest", "Earnestine",
                        "Eartha", "Easter", "Eboni", "Ebonie", "Ebony", "Echo", "Ed", "Eda", "Edda", "Eddie", "Eddy",
                        "Edelmira", "Eden", "Edgar", "Edgardo", "Edie", "Edison", "Edith", "Edmond", "Edmund",
                        "Edmundo", "Edna", "Edra", "Edris", "Eduardo", "Edward", "Edwardo", "Edwin", "Edwina", "Edyth",
                        "Edythe", "Effie", "Efrain", "Efren", "Ehtel", "Eileen", "Eilene", "Ela", "Eladia", "Elaina",
                        "Elaine", "Elana", "Elane", "Elanor", "Elayne", "Elba", "Elbert", "Elda", "Elden", "Eldon",
                        "Eldora", "Eldridge", "Eleanor", "Eleanora", "Eleanore", "Elease", "Elena", "Elene", "Eleni",
                        "Elenor", "Elenora", "Elenore", "Eleonor", "Eleonora", "Eleonore", "Elfreda", "Elfrieda",
                        "Elfriede", "Eli", "Elia", "Eliana", "Elias", "Elicia", "Elida", "Elidia", "Elijah", "Elin",
                        "Elina", "Elinor", "Elinore", "Elisa", "Elisabeth", "Elise", "Eliseo", "Elisha", "Elissa",
                        "Eliz", "Eliza", "Elizabet", "Elizabeth", "Elizbeth", "Elizebeth", "Elke", "Ella", "Ellamae",
                        "Ellan", "Ellen", "Ellena", "Elli", "Ellie", "Elliot", "Elliott", "Ellis", "Ellsworth", "Elly",
                        "Ellyn", "Elma", "Elmer", "Elmira", "Elmo", "Elna", "Elnora", "Elodia", "Elois", "Eloisa",
                        "Eloise", "Elouise", "Eloy", "Elroy", "Elsa", "Else", "Elsie", "Elsy", "Elton",
                        "Elva", "Elvera", "Elvia", "Elvie", "Elvin", "Elvina", "Elvira", "Elvis", "Elwanda", "Elwood",
                        "Elyse", "Elza", "Ema", "Emanuel", "Emelda", "Emelia", "Emelina", "Emeline", "Emely", "Emerald",
                        "Emerita", "Emerson", "Emery", "Emiko", "Emil", "Emile", "Emilee", "Emilia", "Emilie", "Emilio",
                        "Emily", "Emma", "Emmaline", "Emmanuel", "Emmett", "Emmie", "Emmitt", "Emmy", "Emogene",
                        "Emory", "Ena", "Enda", "Enedina", "Eneida", "Enid", "Enoch", "Enola", "Enrique", "Enriqueta",
                        "Epifania", "Era", "Erasmo", "Eric", "Erica", "Erich", "Erick", "Ericka", "Erik", "Erika",
                        "Erin", "Erinn", "Erlene", "Erlinda", "Erline", "Erma", "Ermelinda", "Erminia", "Erna",
                        "Ernest", "Ernestina", "Ernestine", "Ernesto", "Ernie", "Errol", "Ervin", "Erwin", "Eryn",
                        "Esmeralda", "Esperanza", "Essie", "Esta", "Esteban", "Estefana", "Estela", "Estell", "Estella",
                        "Estelle", "Ester", "Esther", "Estrella", "Etha", "Ethan", "Ethel", "Ethelene", "Ethelyn",
                        "Ethyl", "Etsuko", "Etta", "Ettie", "Eufemia", "Eugena", "Eugene", "Eugenia", "Eugenie",
                        "Eugenio", "Eula", "Eulah", "Eulalia", "Eun", "Euna", "Eunice", "Eura", "Eusebia", "Eusebio",
                        "Eustolia", "Eva", "Evalyn", "Evan", "Evangelina", "Evangeline", "Eve", "Evelia", "Evelin",
                        "Evelina", "Eveline", "Evelyn", "Evelyne", "Evelynn", "Everett", "Everette", "Evette", "Evia",
                        "Evie", "Evita", "Evon", "Evonne", "Ewa", "Exie", "Ezekiel", "Ezequiel", "Ezra", "Fabian",
                        "Fabiola", "Fae", "Fairy", "Faith", "Fallon", "Fannie", "Fanny", "Farah", "Farrah", "Fatima",
                        "Fatimah", "Faustina", "Faustino", "Fausto", "Faviola", "Fawn", "Fay", "Faye", "Fe", "Federico",
                        "Felecia", "Felica", "Felice", "Felicia", "Felicidad", "Felicita", "Felicitas", "Felipa",
                        "Felipe", "Felisa", "Felisha", "Felix", "Felton", "Ferdinand", "Fermin", "Fermina", "Fern",
                        "Fernanda", "Fernande", "Fernando", "Ferne", "Fidel", "Fidela", "Fidelia", "Filiberto",
                        "Filomena", "Fiona", "Flavia", "Fleta", "Fletcher", "Flo", "Flor", "Flora", "Florance",
                        "Florence", "Florencia", "Florencio", "Florene", "Florentina", "Florentino", "Floretta",
                        "Floria", "Florida", "Florinda", "Florine", "Florrie", "Flossie", "Floy", "Floyd", "Fonda",
                        "Forest", "Forrest", "Foster", "Fran", "France", "Francene", "Frances", "Francesca",
                        "Francesco", "Franchesca", "Francie", "Francina", "Francine", "Francis", "Francisca",
                        "Francisco", "Francoise", "Frank", "Frankie", "Franklin", "Franklyn", "Fransisca", "Fred",
                        "Freda", "Fredda", "Freddie", "Freddy", "Frederic", "Frederica", "Frederick", "Fredericka",
                        "Fredia", "Fredric", "Fredrick", "Fredricka", "Freeda", "Freeman", "Freida", "Frida", "Frieda",
                        "Fritz", "Fumiko", "Gabriel", "Gabriela", "Gabriele", "Gabriella", "Gabrielle", "Gail", "Gala",
                        "Gale", "Galen", "Galina", "Garfield", "Garland", "Garnet", "Garnett", "Garret", "Garrett",
                        "Garry", "Garth", "Gary", "Gaston", "Gavin", "Gay", "Gaye", "Gayla", "Gayle", "Gaylene",
                        "Gaylord", "Gaynell", "Gaynelle", "Gearldine", "Gema", "Gemma", "Gena", "Genaro", "Gene",
                        "Genesis", "Geneva", "Genevie", "Genevieve", "Genevive", "Genia", "Genie", "Genna", "Gennie",
                        "Genny", "Genoveva", "Geoffrey", "Georgann", "George", "Georgeann", "Georgeanna", "Georgene",
                        "Georgetta", "Georgette", "Georgia", "Georgiana", "Georgiann", "Georgianna", "Georgianne",
                        "Georgie", "Georgina", "Georgine", "Gerald", "Geraldine", "Geraldo", "Geralyn", "Gerard",
                        "Gerardo", "Gerda", "Geri", "Germaine", "German", "Gerri", "Gerry", "Gertha", "Gertie",
                        "Gertrud", "Gertrude", "Gertrudis", "Gertude", "Ghislaine", "Gia", "Gianna", "Gidget", "Gigi",
                        "Gil", "Gilbert", "Gilberte", "Gilberto", "Gilda", "Gillian", "Gilma", "Gina", "Ginette",
                        "Ginger", "Ginny", "Gino", "Giovanna", "Giovanni", "Gisela", "Gisele", "Giselle", "Gita",
                        "Giuseppe", "Giuseppina", "Gladis", "Glady", "Gladys", "Glayds", "Glen", "Glenda", "Glendora",
                        "Glenn", "Glenna", "Glennie", "Glennis", "Glinda", "Gloria", "Glory", "Glynda", "Glynis",
                        "Golda", "Golden", "Goldie", "Gonzalo", "Gordon", "Grace", "Gracia", "Gracie", "Graciela",
                        "Grady", "Graham", "Graig", "Grant", "Granville", "Grayce", "Grazyna", "Greg", "Gregg",
                        "Gregoria", "Gregorio", "Gregory", "Greta", "Gretchen", "Gretta", "Gricelda", "Grisel",
                        "Griselda", "Grover", "Guadalupe", "Gudrun", "Guillermina", "Guillermo", "Gus", "Gussie",
                        "Gustavo", "Guy", "Gwen", "Gwenda", "Gwendolyn", "Gwenn", "Gwyn", "Gwyneth", "Ha", "Hae", "Hai",
                        "Hailey", "Hal", "Haley", "Halina", "Halley", "Hallie", "Han", "Hana", "Hang", "Hanh", "Hank",
                        "Hanna", "Hannah", "Hannelore", "Hans", "Harlan", "Harland", "Harley", "Harmony", "Harold",
                        "Harriet", "Harriett", "Harriette", "Harris", "Harrison", "Harry", "Harvey", "Hassan", "Hassie",
                        "Hattie", "Haydee", "Hayden", "Hayley", "Haywood", "Hazel", "Heath", "Heather", "Hector",
                        "Hedwig", "Hedy", "Hee", "Heide", "Heidi", "Heidy", "Heike", "Helaine", "Helen", "Helena",
                        "Helene", "Helga", "Hellen", "Henrietta", "Henriette", "Henry", "Herb", "Herbert", "Heriberto",
                        "Herlinda", "Herma", "Herman", "Hermelinda", "Hermila", "Hermina", "Hermine", "Herminia",
                        "Herschel", "Hershel",
                        "Herta", "Hertha", "Hester", "Hettie", "Hiedi", "Hien", "Hilaria", "Hilario", "Hilary", "Hilda",
                        "Hilde", "Hildegard", "Hildegarde", "Hildred", "Hillary", "Hilma", "Hilton", "Hipolito",
                        "Hiram", "Hiroko", "Hisako", "Hoa", "Hobert", "Holley", "Holli", "Hollie", "Hollis", "Holly",
                        "Homer", "Honey", "Hong", "Hope", "Horace", "Horacio", "Hortencia", "Hortense", "Hortensia",
                        "Hosea", "Houston", "Howard", "Hoyt", "Hsiu", "Hubert", "Hue", "Huey", "Hugh", "Hugo", "Hui",
                        "Hulda", "Humberto", "Hung", "Hunter", "Huong", "Hwa", "Hyacinth", "Hye", "Hyman", "Hyo",
                        "Hyon", "Hyun", "Ian", "Ida", "Idalia", "Idell", "Idella", "Iesha", "Ignacia", "Ignacio", "Ike",
                        "Ila", "Ilana", "Ilda", "Ileana", "Ileen", "Ilene", "Iliana", "Illa", "Ilona", "Ilse",
                        "Iluminada", "Ima", "Imelda", "Imogene", "In", "Ina", "India", "Indira", "Inell", "Ines",
                        "Inez", "Inga", "Inge", "Ingeborg", "Inger", "Ingrid", "Inocencia", "Iola", "Iona", "Ione",
                        "Ira", "Iraida", "Irena", "Irene", "Irina", "Iris", "Irish", "Irma", "Irmgard", "Irvin",
                        "Irving", "Irwin", "Isa", "Isaac", "Isabel", "Isabell", "Isabella", "Isabelle", "Isadora",
                        "Isaiah", "Isaias", "Isaura", "Isela", "Isiah", "Isidra", "Isidro", "Isis", "Ismael", "Isobel",
                        "Israel", "Isreal", "Issac", "Iva", "Ivan", "Ivana", "Ivelisse", "Ivette", "Ivey", "Ivonne",
                        "Ivory", "Ivy", "Izetta", "Izola", "Ja", "Jacalyn", "Jacelyn", "Jacinda", "Jacinta", "Jacinto",
                        "Jack", "Jackeline", "Jackelyn", "Jacki", "Jackie", "Jacklyn", "Jackqueline", "Jackson",
                        "Jaclyn", "Jacob", "Jacqualine", "Jacque", "Jacquelin", "Jacqueline", "Jacquelyn", "Jacquelyne",
                        "Jacquelynn", "Jacques", "Jacquetta", "Jacqui", "Jacquie", "Jacquiline", "Jacquline",
                        "Jacqulyn", "Jada", "Jade", "Jadwiga", "Jae", "Jaime", "Jaimee", "Jaimie", "Jake", "Jaleesa",
                        "Jalisa", "Jama", "Jamaal", "Jamal", "Jamar", "Jame", "Jamee", "Jamel", "James", "Jamey",
                        "Jami", "Jamie", "Jamika", "Jamila", "Jamison", "Jammie", "Jan", "Jana", "Janae", "Janay",
                        "Jane", "Janean", "Janee", "Janeen", "Janel", "Janell", "Janella", "Janelle", "Janene",
                        "Janessa", "Janet", "Janeth", "Janett", "Janetta", "Janette", "Janey", "Jani", "Janice",
                        "Janie", "Janiece", "Janina", "Janine", "Janis", "Janise", "Janita", "Jann", "Janna", "Jannet",
                        "Jannette", "Jannie", "January", "Janyce", "Jaqueline", "Jaquelyn", "Jared", "Jarod", "Jarred",
                        "Jarrett", "Jarrod", "Jarvis", "Jasmin", "Jasmine", "Jason", "Jasper", "Jaunita", "Javier",
                        "Jay", "Jaye", "Jayme", "Jaymie", "Jayna", "Jayne", "Jayson", "Jazmin", "Jazmine", "Jc", "Jean",
                        "Jeana", "Jeane", "Jeanelle", "Jeanene", "Jeanett", "Jeanetta", "Jeanette", "Jeanice", "Jeanie",
                        "Jeanine", "Jeanmarie", "Jeanna", "Jeanne", "Jeannetta", "Jeannette", "Jeannie", "Jeannine",
                        "Jed", "Jeff", "Jefferey", "Jefferson", "Jeffery", "Jeffie", "Jeffrey", "Jeffry", "Jen", "Jena",
                        "Jenae", "Jene", "Jenee", "Jenell", "Jenelle", "Jenette", "Jeneva", "Jeni", "Jenice", "Jenifer",
                        "Jeniffer", "Jenine", "Jenise", "Jenna", "Jennefer", "Jennell", "Jennette", "Jenni", "Jennie",
                        "Jennifer", "Jenniffer", "Jennine", "Jenny", "Jerald", "Jeraldine", "Jeramy", "Jere",
                        "Jeremiah", "Jeremy", "Jeri", "Jerica", "Jerilyn", "Jerlene", "Jermaine", "Jerold", "Jerome",
                        "Jeromy", "Jerrell", "Jerri", "Jerrica", "Jerrie", "Jerrod", "Jerrold", "Jerry", "Jesenia",
                        "Jesica", "Jess", "Jesse", "Jessenia", "Jessi", "Jessia", "Jessica", "Jessie", "Jessika",
                        "Jestine", "Jesus", "Jesusa", "Jesusita", "Jetta", "Jettie", "Jewel", "Jewell", "Ji", "Jill",
                        "Jillian", "Jim", "Jimmie", "Jimmy", "Jin", "Jina", "Jinny", "Jo", "Joan", "Joana", "Joane",
                        "Joanie", "Joann", "Joanna", "Joanne", "Joannie", "Joaquin", "Joaquina", "Jocelyn", "Jodee",
                        "Jodi", "Jodie", "Jody", "Joe", "Joeann", "Joel", "Joella", "Joelle", "Joellen", "Joesph",
                        "Joetta", "Joette", "Joey", "Johana", "Johanna", "Johanne", "John", "Johna", "Johnathan",
                        "Johnathon", "Johnetta", "Johnette", "Johnie", "Johnna", "Johnnie", "Johnny", "Johnsie",
                        "Johnson", "Joi", "Joie", "Jolanda", "Joleen", "Jolene", "Jolie", "Joline", "Jolyn", "Jolynn",
                        "Jon", "Jona", "Jonah", "Jonas", "Jonathan", "Jonathon", "Jone", "Jonell", "Jonelle", "Jong",
                        "Joni", "Jonie", "Jonna", "Jonnie", "Jordan", "Jordon", "Jorge", "Jose", "Josef", "Josefa",
                        "Josefina", "Josefine", "Joselyn", "Joseph", "Josephina", "Josephine", "Josette", "Josh",
                        "Joshua", "Josiah", "Josie", "Joslyn", "Jospeh", "Josphine", "Josue", "Jovan", "Jovita", "Joy",
                        "Joya", "Joyce", "Joycelyn", "Joye", "Juan", "Juana", "Juanita", "Jude", "Judi", "Judie",
                        "Judith", "Judson", "Judy", "Jule", "Julee", "Julene", "Jules", "Juli", "Julia", "Julian",
                        "Juliana", "Juliane", "Juliann", "Julianna", "Julianne", "Julie", "Julieann", "Julienne",
                        "Juliet", "Julieta", "Julietta", "Juliette", "Julio", "Julissa", "Julius", "June", "Jung",
                        "Junie", "Junior", "Junita", "Junko", "Justa", "Justin", "Justina", "Justine", "Jutta", "Ka",
                        "Kacey", "Kaci", "Kacie", "Kacy", "Kai", "Kaila", "Kaitlin", "Kaitlyn", "Kala", "Kaleigh",
                        "Kaley", "Kali", "Kallie", "Kalyn", "Kam", "Kamala", "Kami", "Kamilah", "Kandace",
                        "Kandi", "Kandice", "Kandis", "Kandra", "Kandy", "Kanesha", "Kanisha", "Kara", "Karan",
                        "Kareem", "Kareen", "Karen", "Karena", "Karey", "Kari", "Karie", "Karima", "Karin", "Karina",
                        "Karine", "Karisa", "Karissa", "Karl", "Karla", "Karleen", "Karlene", "Karly", "Karlyn",
                        "Karma", "Karmen", "Karol", "Karole", "Karoline", "Karolyn", "Karon", "Karren", "Karri",
                        "Karrie", "Karry", "Kary", "Karyl", "Karyn", "Kasandra", "Kasey", "Kasha", "Kasi", "Kasie",
                        "Kassandra", "Kassie", "Kate", "Katelin", "Katelyn", "Katelynn", "Katerine", "Kathaleen",
                        "Katharina", "Katharine", "Katharyn", "Kathe", "Katheleen", "Katherin", "Katherina",
                        "Katherine", "Kathern", "Katheryn", "Kathey", "Kathi", "Kathie", "Kathleen", "Kathlene",
                        "Kathline", "Kathlyn", "Kathrin", "Kathrine", "Kathryn", "Kathryne", "Kathy", "Kathyrn", "Kati",
                        "Katia", "Katie", "Katina", "Katlyn", "Katrice", "Katrina", "Kattie", "Katy", "Kay", "Kayce",
                        "Kaycee", "Kaye", "Kayla", "Kaylee", "Kayleen", "Kayleigh", "Kaylene", "Kazuko", "Kecia",
                        "Keeley", "Keely", "Keena", "Keenan", "Keesha", "Keiko", "Keila", "Keira", "Keisha", "Keith",
                        "Keitha", "Keli", "Kelle", "Kellee", "Kelley", "Kelli", "Kellie", "Kelly", "Kellye", "Kelsey",
                        "Kelsi", "Kelsie", "Kelvin", "Kemberly", "Ken", "Kena", "Kenda", "Kendal", "Kendall", "Kendra",
                        "Kendrick", "Keneth", "Kenia", "Kenisha", "Kenna", "Kenneth", "Kennith", "Kenny", "Kent",
                        "Kenton", "Kenya", "Kenyatta", "Kenyetta", "Kera", "Keren", "Keri", "Kermit", "Kerri", "Kerrie",
                        "Kerry", "Kerstin", "Kesha", "Keshia", "Keturah", "Keva", "Keven", "Kevin", "Khadijah",
                        "Khalilah", "Kia", "Kiana", "Kiara", "Kiera", "Kiersten", "Kiesha", "Kieth", "Kiley", "Kim",
                        "Kimber", "Kimberely", "Kimberlee", "Kimberley", "Kimberli", "Kimberlie", "Kimberly", "Kimbery",
                        "Kimbra", "Kimi", "Kimiko", "Kina", "Kindra", "King", "Kip", "Kira", "Kirby", "Kirk", "Kirsten",
                        "Kirstie", "Kirstin", "Kisha", "Kit", "Kittie", "Kitty", "Kiyoko", "Kizzie", "Kizzy", "Klara",
                        "Korey", "Kori", "Kortney", "Kory", "Kourtney", "Kraig", "Kris", "Krishna", "Krissy", "Krista",
                        "Kristal", "Kristan", "Kristeen", "Kristel", "Kristen", "Kristi", "Kristian", "Kristie",
                        "Kristin", "Kristina", "Kristine", "Kristle", "Kristofer", "Kristopher", "Kristy", "Kristyn",
                        "Krysta", "Krystal", "Krysten", "Krystin", "Krystina", "Krystle", "Krystyna", "Kum", "Kurt",
                        "Kurtis", "Kyla", "Kyle", "Kylee", "Kylie", "Kym", "Kymberly", "Kyoko", "Kyong", "Kyra",
                        "Kyung", "Lacey", "Lachelle", "Laci", "Lacie", "Lacresha", "Lacy", "Ladawn", "Ladonna", "Lady",
                        "Lael", "Lahoma", "Lai", "Laila", "Laine", "Lajuana", "Lakeesha", "Lakeisha", "Lakendra",
                        "Lakenya", "Lakesha", "Lakeshia", "Lakia", "Lakiesha", "Lakisha", "Lakita", "Lala", "Lamar",
                        "Lamonica", "Lamont", "Lan", "Lana", "Lance", "Landon", "Lane", "Lanell", "Lanelle", "Lanette",
                        "Lang", "Lani", "Lanie", "Lanita", "Lannie", "Lanny", "Lanora", "Laquanda", "Laquita", "Lara",
                        "Larae", "Laraine", "Laree", "Larhonda", "Larisa", "Larissa", "Larita", "Laronda", "Larraine",
                        "Larry", "Larue", "Lasandra", "Lashanda", "Lashandra", "Lashaun", "Lashaunda", "Lashawn",
                        "Lashawna", "Lashawnda", "Lashay", "Lashell", "Lashon", "Lashonda", "Lashunda", "Lasonya",
                        "Latanya", "Latarsha", "Latasha", "Latashia", "Latesha", "Latia", "Laticia", "Latina",
                        "Latisha", "Latonia", "Latonya", "Latoria", "Latosha", "Latoya", "Latoyia", "Latrice",
                        "Latricia", "Latrina", "Latrisha", "Launa", "Laura", "Lauralee", "Lauran", "Laure", "Laureen",
                        "Laurel", "Lauren", "Laurena", "Laurence", "Laurene", "Lauretta", "Laurette", "Lauri",
                        "Laurice", "Laurie", "Laurinda", "Laurine", "Lauryn", "Lavada", "Lavelle", "Lavenia", "Lavera",
                        "Lavern", "Laverna", "Laverne", "Laveta", "Lavette", "Lavina", "Lavinia", "Lavon", "Lavona",
                        "Lavonda", "Lavone", "Lavonia", "Lavonna", "Lavonne", "Lawana", "Lawanda", "Lawanna",
                        "Lawerence", "Lawrence", "Layla", "Layne", "Lazaro", "Le", "Lea", "Leah", "Lean", "Leana",
                        "Leandra", "Leandro", "Leann", "Leanna", "Leanne", "Leanora", "Leatha", "Leatrice", "Lecia",
                        "Leda", "Lee", "Leeann", "Leeanna", "Leeanne", "Leena", "Leesa", "Leia", "Leida", "Leif",
                        "Leigh", "Leigha", "Leighann", "Leila", "Leilani", "Leisa", "Leisha", "Lekisha", "Lela",
                        "Lelah", "Leland", "Lelia", "Lemuel", "Len", "Lena", "Lenard", "Lenita", "Lenna", "Lennie",
                        "Lenny", "Lenora", "Lenore", "Leo", "Leola", "Leoma", "Leon", "Leona", "Leonard", "Leonarda",
                        "Leonardo", "Leone", "Leonel", "Leonia", "Leonida", "Leonie", "Leonila", "Leonor", "Leonora",
                        "Leonore", "Leontine", "Leopoldo", "Leora", "Leota", "Lera", "Leroy", "Les", "Lesa", "Lesha",
                        "Lesia", "Leslee", "Lesley", "Lesli", "Leslie", "Lessie", "Lester", "Leta", "Letha", "Leticia",
                        "Letisha", "Letitia", "Lettie", "Letty", "Levi", "Lewis", "Lexie", "Lezlie", "Li", "Lia",
                        "Liana", "Liane", "Lianne", "Libbie", "Libby", "Liberty", "Librada", "Lida", "Lidia", "Lien",
                        "Lieselotte", "Ligia", "Lila", "Lili", "Lilia", "Lilian", "Liliana", "Lilla", "Lilli", "Lillia",
                        "Lilliam", "Lillian", "Lilliana", "Lillie", "Lilly", "Lily", "Lin", "Lina", "Lincoln",
                        "Linda", "Lindsay", "Lindsey", "Lindsy", "Lindy", "Linette", "Ling", "Linh", "Linn", "Linnea",
                        "Linnie", "Lino", "Linsey", "Linwood", "Lionel", "Lisa", "Lisabeth", "Lisandra", "Lisbeth",
                        "Lise", "Lisette", "Lisha", "Lissa", "Lissette", "Lita", "Livia", "Liz", "Liza", "Lizabeth",
                        "Lizbeth", "Lizeth", "Lizette", "Lizzette", "Lizzie", "Lloyd", "Loan", "Logan", "Loida", "Lois",
                        "Loise", "Lola", "Lolita", "Loma", "Lon", "Lona", "Londa", "Long", "Loni", "Lonna", "Lonnie",
                        "Lonny", "Lora", "Loraine", "Loralee", "Lore", "Lorean", "Loree", "Loreen", "Lorelei", "Loren",
                        "Lorena", "Lorene", "Lorenza", "Lorenzo", "Loreta", "Loretta", "Lorette", "Lori", "Loria",
                        "Loriann", "Lorie", "Lorilee", "Lorina", "Lorinda", "Lorine", "Loris", "Lorita", "Lorna",
                        "Lorraine", "Lorretta", "Lorri", "Lorriane", "Lorrie", "Lorrine", "Lory", "Lottie", "Lou",
                        "Louann", "Louanne", "Louella", "Louetta", "Louie", "Louis", "Louisa", "Louise", "Loura",
                        "Lourdes", "Lourie", "Louvenia", "Love", "Lovella", "Lovetta", "Lovie", "Lowell", "Loyce",
                        "Loyd", "Lu", "Luana", "Luann", "Luanna", "Luanne", "Luba", "Lucas", "Luci", "Lucia", "Luciana",
                        "Luciano", "Lucie", "Lucien", "Lucienne", "Lucila", "Lucile", "Lucilla", "Lucille", "Lucina",
                        "Lucinda", "Lucio", "Lucius", "Lucrecia", "Lucretia", "Lucy", "Ludie", "Ludivina", "Lue",
                        "Luella", "Luetta", "Luigi", "Luis", "Luisa", "Luise", "Luke", "Lula", "Lulu", "Luna", "Lupe",
                        "Lupita", "Lura", "Lurlene", "Lurline", "Luther", "Luvenia", "Luz", "Lyda", "Lydia", "Lyla",
                        "Lyle", "Lyman", "Lyn", "Lynda", "Lyndia", "Lyndon", "Lyndsay", "Lyndsey", "Lynell", "Lynelle",
                        "Lynetta", "Lynette", "Lynn", "Lynna", "Lynne", "Lynnette", "Lynsey", "Lynwood", "Ma", "Mabel",
                        "Mabelle", "Mable", "Mac", "Machelle", "Macie", "Mack", "Mackenzie", "Macy", "Madalene",
                        "Madaline", "Madalyn", "Maddie", "Madelaine", "Madeleine", "Madelene", "Madeline", "Madelyn",
                        "Madge", "Madie", "Madison", "Madlyn", "Madonna", "Mae", "Maegan", "Mafalda", "Magali",
                        "Magaly", "Magan", "Magaret", "Magda", "Magdalen", "Magdalena", "Magdalene", "Magen", "Maggie",
                        "Magnolia", "Mahalia", "Mai", "Maia", "Maida", "Maile", "Maira", "Maire", "Maisha", "Maisie",
                        "Major", "Majorie", "Makeda", "Malcolm", "Malcom", "Malena", "Malia", "Malik", "Malika",
                        "Malinda", "Malisa", "Malissa", "Malka", "Mallie", "Mallory", "Malorie", "Malvina", "Mamie",
                        "Mammie", "Man", "Mana", "Manda", "Mandi", "Mandie", "Mandy", "Manie", "Manual", "Manuel",
                        "Manuela", "Many", "Mao", "Maple", "Mara", "Maragaret", "Maragret", "Maranda", "Marc", "Marcel",
                        "Marcela", "Marcelene", "Marcelina", "Marceline", "Marcelino", "Marcell", "Marcella",
                        "Marcelle", "Marcellus", "Marcelo", "Marcene", "Marchelle", "Marci", "Marcia", "Marcie",
                        "Marco", "Marcos", "Marcus", "Marcy", "Mardell", "Maren", "Marg", "Margaret", "Margareta",
                        "Margarete", "Margarett", "Margaretta", "Margarette", "Margarita", "Margarite", "Margarito",
                        "Margart", "Marge", "Margene", "Margeret", "Margert", "Margery", "Marget", "Margherita",
                        "Margie", "Margit", "Margo", "Margorie", "Margot", "Margret", "Margrett", "Marguerita",
                        "Marguerite", "Margurite", "Margy", "Marhta", "Mari", "Maria", "Mariah", "Mariam", "Marian",
                        "Mariana", "Marianela", "Mariann", "Marianna", "Marianne", "Mariano", "Maribel", "Maribeth",
                        "Marica", "Maricela", "Maricruz", "Marie", "Mariel", "Mariela", "Mariella", "Marielle",
                        "Marietta", "Mariette", "Mariko", "Marilee", "Marilou", "Marilu", "Marilyn", "Marilynn",
                        "Marin", "Marina", "Marinda", "Marine", "Mario", "Marion", "Maris", "Marisa", "Marisela",
                        "Marisha", "Marisol", "Marissa", "Marita", "Maritza", "Marivel", "Marjorie", "Marjory", "Mark",
                        "Marketta", "Markita", "Markus", "Marla", "Marlana", "Marleen", "Marlen", "Marlena", "Marlene",
                        "Marlin", "Marline", "Marlo", "Marlon", "Marlyn", "Marlys", "Marna", "Marni", "Marnie",
                        "Marquerite", "Marquetta", "Marquis", "Marquita", "Marquitta", "Marry", "Marsha", "Marshall",
                        "Marta", "Marth", "Martha", "Marti", "Martin", "Martina", "Martine", "Marty", "Marva", "Marvel",
                        "Marvella", "Marvin", "Marvis", "Marx", "Mary", "Marya", "Maryalice", "Maryam", "Maryann",
                        "Maryanna", "Maryanne", "Marybelle", "Marybeth", "Maryellen", "Maryetta", "Maryjane", "Maryjo",
                        "Maryland", "Marylee", "Marylin", "Maryln", "Marylou", "Marylouise", "Marylyn", "Marylynn",
                        "Maryrose", "Masako", "Mason", "Matha", "Mathew", "Mathilda", "Mathilde", "Matilda", "Matilde",
                        "Matt", "Matthew", "Mattie", "Maud", "Maude", "Maudie", "Maura", "Maureen", "Maurice",
                        "Mauricio", "Maurine", "Maurita", "Mauro", "Mavis", "Max", "Maxie", "Maxima", "Maximina",
                        "Maximo", "Maxine", "Maxwell", "May", "Maya", "Maybell", "Maybelle", "Maye", "Mayme", "Maynard",
                        "Mayola", "Mayra", "Mazie", "Mckenzie", "Mckinley", "Meagan", "Meaghan", "Mechelle", "Meda",
                        "Mee", "Meg", "Megan", "Meggan", "Meghan", "Meghann", "Mei", "Mel", "Melaine", "Melani",
                        "Melania", "Melanie", "Melany", "Melba", "Melda", "Melia", "Melida", "Melina", "Melinda",
                        "Melisa", "Melissa", "Melissia", "Melita", "Mellie", "Mellisa", "Mellissa", "Melodee",
                        "Melodi", "Melodie", "Melody", "Melonie", "Melony", "Melva", "Melvin", "Melvina", "Melynda",
                        "Mendy", "Mercedes", "Mercedez", "Mercy", "Meredith", "Meri", "Merideth", "Meridith", "Merilyn",
                        "Merissa", "Merle", "Merlene", "Merlin", "Merlyn", "Merna", "Merri", "Merrie", "Merrilee",
                        "Merrill", "Merry", "Mertie", "Mervin", "Meryl", "Meta", "Mi", "Mia", "Mica", "Micaela",
                        "Micah", "Micha", "Michael", "Michaela", "Michaele", "Michal", "Michale", "Micheal", "Michel",
                        "Michele", "Michelina", "Micheline", "Michell", "Michelle", "Michiko", "Mickey", "Micki",
                        "Mickie", "Miesha", "Migdalia", "Mignon", "Miguel", "Miguelina", "Mika", "Mikaela", "Mike",
                        "Mikel", "Miki", "Mikki", "Mila", "Milagro", "Milagros", "Milan", "Milda", "Mildred", "Miles",
                        "Milford", "Milissa", "Millard", "Millicent", "Millie", "Milly", "Milo", "Milton", "Mimi",
                        "Min", "Mina", "Minda", "Mindi", "Mindy", "Minerva", "Ming", "Minh", "Minna", "Minnie", "Minta",
                        "Miquel", "Mira", "Miranda", "Mireille", "Mirella", "Mireya", "Miriam", "Mirian", "Mirna",
                        "Mirta", "Mirtha", "Misha", "Miss", "Missy", "Misti", "Mistie", "Misty", "Mitch", "Mitchel",
                        "Mitchell", "Mitsue", "Mitsuko", "Mittie", "Mitzi", "Mitzie", "Miyoko", "Modesta", "Modesto",
                        "Mohamed", "Mohammad", "Mohammed", "Moira", "Moises", "Mollie", "Molly", "Mona", "Monet",
                        "Monica", "Monika", "Monique", "Monnie", "Monroe", "Monserrate", "Monte", "Monty", "Moon",
                        "Mora", "Morgan", "Moriah", "Morris", "Morton", "Mose", "Moses", "Moshe", "Mozell", "Mozella",
                        "Mozelle", "Mui", "Muoi", "Muriel", "Murray", "My", "Myesha", "Myles", "Myong", "Myra",
                        "Myriam", "Myrl", "Myrle", "Myrna", "Myron", "Myrta", "Myrtice", "Myrtie", "Myrtis", "Myrtle",
                        "Myung", "Na", "Nada", "Nadene", "Nadia", "Nadine", "Naida", "Nakesha", "Nakia", "Nakisha",
                        "Nakita", "Nam", "Nan", "Nana", "Nancee", "Nancey", "Nanci", "Nancie", "Nancy", "Nanette",
                        "Nannette", "Nannie", "Naoma", "Naomi", "Napoleon", "Narcisa", "Natacha", "Natalia", "Natalie",
                        "Natalya", "Natasha", "Natashia", "Nathalie", "Nathan", "Nathanael", "Nathanial", "Nathaniel",
                        "Natisha", "Natividad", "Natosha", "Neal", "Necole", "Ned", "Neda", "Nedra", "Neely", "Neida",
                        "Neil", "Nelda", "Nelia", "Nelida", "Nell", "Nella", "Nelle", "Nellie", "Nelly", "Nelson",
                        "Nena", "Nenita", "Neoma", "Neomi", "Nereida", "Nerissa", "Nery", "Nestor", "Neta", "Nettie",
                        "Neva", "Nevada", "Neville", "Newton", "Nga", "Ngan", "Ngoc", "Nguyet", "Nia", "Nichelle",
                        "Nichol", "Nicholas", "Nichole", "Nicholle", "Nick", "Nicki", "Nickie", "Nickolas", "Nickole",
                        "Nicky", "Nicol", "Nicola", "Nicolas", "Nicolasa", "Nicole", "Nicolette", "Nicolle", "Nida",
                        "Nidia", "Niesha", "Nieves", "Nigel", "Niki", "Nikia", "Nikita", "Nikki", "Nikole", "Nila",
                        "Nilda", "Nilsa", "Nina", "Ninfa", "Nisha", "Nita", "Noah", "Noble", "Nobuko", "Noe", "Noel",
                        "Noelia", "Noella", "Noelle", "Noemi", "Nohemi", "Nola", "Nolan", "Noma", "Nona", "Nora",
                        "Norah", "Norbert", "Norberto", "Noreen", "Norene", "Noriko", "Norine", "Norma", "Norman",
                        "Normand", "Norris", "Nova", "Novella", "Nu", "Nubia", "Numbers", "Nydia", "Nyla", "Obdulia",
                        "Ocie", "Octavia", "Octavio", "Oda", "Odelia", "Odell", "Odessa", "Odette", "Odilia", "Odis",
                        "Ofelia", "Ok", "Ola", "Olen", "Olene", "Oleta", "Olevia", "Olga", "Olimpia", "Olin", "Olinda",
                        "Oliva", "Olive", "Oliver", "Olivia", "Ollie", "Olympia", "Oma", "Omar", "Omega", "Omer", "Ona",
                        "Oneida", "Onie", "Onita", "Opal", "Ophelia", "Ora", "Oralee", "Oralia", "Oren", "Oretha",
                        "Orlando", "Orpha", "Orval", "Orville", "Oscar", "Ossie", "Osvaldo", "Oswaldo", "Otelia",
                        "Otha", "Otilia", "Otis", "Otto", "Ouida", "Owen", "Ozell", "Ozella", "Ozie", "Pa", "Pablo",
                        "Page", "Paige", "Palma", "Palmer", "Palmira", "Pam", "Pamala", "Pamela", "Pamelia", "Pamella",
                        "Pamila", "Pamula", "Pandora", "Pansy", "Paola", "Paris", "Parker", "Parthenia", "Particia",
                        "Pasquale", "Pasty", "Pat", "Patience", "Patria", "Patrica", "Patrice", "Patricia", "Patrick",
                        "Patrina", "Patsy", "Patti", "Pattie", "Patty", "Paul", "Paula", "Paulene", "Pauletta",
                        "Paulette", "Paulina", "Pauline", "Paulita", "Paz", "Pearl", "Pearle", "Pearlene", "Pearlie",
                        "Pearline", "Pearly", "Pedro", "Peg", "Peggie", "Peggy", "Pei", "Penelope", "Penney", "Penni",
                        "Pennie", "Penny", "Percy", "Perla", "Perry", "Pete", "Peter", "Petra", "Petrina", "Petronila",
                        "Phebe", "Phil", "Philip", "Phillip", "Phillis", "Philomena", "Phoebe", "Phung", "Phuong",
                        "Phylicia", "Phylis", "Phyliss", "Phyllis", "Pia", "Piedad", "Pierre", "Pilar", "Ping",
                        "Pinkie", "Piper", "Pok", "Polly", "Porfirio", "Porsche", "Porsha", "Porter", "Portia",
                        "Precious", "Preston", "Pricilla", "Prince", "Princess", "Priscila", "Priscilla", "Providencia",
                        "Prudence", "Pura", "Qiana", "Queen", "Queenie", "Quentin", "Quiana", "Quincy", "Quinn",
                        "Quintin", "Quinton", "Quyen", "Rachael", "Rachal", "Racheal", "Rachel", "Rachele", "Rachell",
                        "Rachelle", "Racquel", "Rae", "Raeann", "Raelene", "Rafael", "Rafaela", "Raguel", "Raina",
                        "Raisa", "Raleigh", "Ralph", "Ramiro", "Ramon", "Ramona",
                        "Ramonita", "Rana", "Ranae", "Randa", "Randal", "Randall", "Randee", "Randell", "Randi",
                        "Randolph", "Randy", "Ranee", "Raphael", "Raquel", "Rashad", "Rasheeda", "Rashida", "Raul",
                        "Raven", "Ray", "Raye", "Rayford", "Raylene", "Raymon", "Raymond", "Raymonde", "Raymundo",
                        "Rayna", "Rea", "Reagan", "Reanna", "Reatha", "Reba", "Rebbeca", "Rebbecca", "Rebeca",
                        "Rebecca", "Rebecka", "Rebekah", "Reda", "Reed", "Reena", "Refugia", "Refugio", "Regan",
                        "Regena", "Regenia", "Reggie", "Regina", "Reginald", "Regine", "Reginia", "Reid", "Reiko",
                        "Reina", "Reinaldo", "Reita", "Rema", "Remedios", "Remona", "Rena", "Renae", "Renaldo",
                        "Renata", "Renate", "Renato", "Renay", "Renda", "Rene", "Renea", "Renee", "Renetta", "Renita",
                        "Renna", "Ressie", "Reta", "Retha", "Retta", "Reuben", "Reva", "Rex", "Rey", "Reyes", "Reyna",
                        "Reynalda", "Reynaldo", "Rhea", "Rheba", "Rhett", "Rhiannon", "Rhoda", "Rhona", "Rhonda", "Ria",
                        "Ricarda", "Ricardo", "Rich", "Richard", "Richelle", "Richie", "Rick", "Rickey", "Ricki",
                        "Rickie", "Ricky", "Rico", "Rigoberto", "Rikki", "Riley", "Rima", "Rina", "Risa", "Rita",
                        "Riva", "Rivka", "Rob", "Robbi", "Robbie", "Robbin", "Robby", "Robbyn", "Robena", "Robert",
                        "Roberta", "Roberto", "Robin", "Robt", "Robyn", "Rocco", "Rochel", "Rochell", "Rochelle",
                        "Rocio", "Rocky", "Rod", "Roderick", "Rodger", "Rodney", "Rodolfo", "Rodrick", "Rodrigo",
                        "Rogelio", "Roger", "Roland", "Rolanda", "Rolande", "Rolando", "Rolf", "Rolland", "Roma",
                        "Romaine", "Roman", "Romana", "Romelia", "Romeo", "Romona", "Ron", "Rona", "Ronald", "Ronda",
                        "Roni", "Ronna", "Ronni", "Ronnie", "Ronny", "Roosevelt", "Rory", "Rosa", "Rosalba", "Rosalee",
                        "Rosalia", "Rosalie", "Rosalina", "Rosalind", "Rosalinda", "Rosaline", "Rosalva", "Rosalyn",
                        "Rosamaria", "Rosamond", "Rosana", "Rosann", "Rosanna", "Rosanne", "Rosaria", "Rosario",
                        "Rosaura", "Roscoe", "Rose", "Roseann", "Roseanna", "Roseanne", "Roselee", "Roselia",
                        "Roseline", "Rosella", "Roselle", "Roselyn", "Rosemarie", "Rosemary", "Rosena", "Rosenda",
                        "Rosendo", "Rosetta", "Rosette", "Rosia", "Rosie", "Rosina", "Rosio", "Rosita", "Roslyn",
                        "Ross", "Rossana", "Rossie", "Rosy", "Rowena", "Roxana", "Roxane", "Roxann", "Roxanna",
                        "Roxanne", "Roxie", "Roxy", "Roy", "Royal", "Royce", "Rozanne", "Rozella", "Ruben", "Rubi",
                        "Rubie", "Rubin", "Ruby", "Rubye", "Rudolf", "Rudolph", "Rudy", "Rueben", "Rufina", "Rufus",
                        "Rupert", "Russ", "Russel", "Russell", "Rusty", "Ruth", "Rutha", "Ruthann", "Ruthanne", "Ruthe",
                        "Ruthie", "Ryan", "Ryann", "Sabina", "Sabine", "Sabra", "Sabrina", "Sacha", "Sachiko", "Sade",
                        "Sadie", "Sadye", "Sage", "Sal", "Salena", "Salina", "Salley", "Sallie", "Sally", "Salome",
                        "Salvador", "Salvatore", "Sam", "Samantha", "Samara", "Samatha", "Samella", "Samira", "Sammie",
                        "Sammy", "Samual", "Samuel", "Sana", "Sanda", "Sandee", "Sandi", "Sandie", "Sandra", "Sandy",
                        "Sanford", "Sang", "Sanjuana", "Sanjuanita", "Sanora", "Santa", "Santana", "Santiago",
                        "Santina", "Santo", "Santos", "Sara", "Sarah", "Sarai", "Saran", "Sari", "Sarina", "Sarita",
                        "Sasha", "Saturnina", "Sau", "Saul", "Saundra", "Savanna", "Savannah", "Scarlet", "Scarlett",
                        "Scot", "Scott", "Scottie", "Scotty", "Sean", "Season", "Sebastian", "Sebrina", "See", "Seema",
                        "Selena", "Selene", "Selina", "Selma", "Sena", "Senaida", "September", "Serafina", "Serena",
                        "Sergio", "Serina", "Serita", "Seth", "Setsuko", "Seymour", "Sha", "Shad", "Shae", "Shaina",
                        "Shakia", "Shakira", "Shakita", "Shala", "Shalanda", "Shalon", "Shalonda", "Shameka", "Shamika",
                        "Shan", "Shana", "Shanae", "Shanda", "Shandi", "Shandra", "Shane", "Shaneka", "Shanel",
                        "Shanell", "Shanelle", "Shani", "Shanice", "Shanika", "Shaniqua", "Shanita", "Shanna",
                        "Shannan", "Shannon", "Shanon", "Shanta", "Shantae", "Shantay", "Shante", "Shantel", "Shantell",
                        "Shantelle", "Shanti", "Shaquana", "Shaquita", "Shara", "Sharan", "Sharda", "Sharee", "Sharell",
                        "Sharen", "Shari", "Sharice", "Sharie", "Sharika", "Sharilyn", "Sharita", "Sharla", "Sharleen",
                        "Sharlene", "Sharmaine", "Sharolyn", "Sharon", "Sharonda", "Sharri", "Sharron", "Sharyl",
                        "Sharyn", "Shasta", "Shaun", "Shauna", "Shaunda", "Shaunna", "Shaunta", "Shaunte", "Shavon",
                        "Shavonda", "Shavonne", "Shawana", "Shawanda", "Shawanna", "Shawn", "Shawna", "Shawnda",
                        "Shawnee", "Shawnna", "Shawnta", "Shay", "Shayla", "Shayna", "Shayne", "Shea", "Sheba",
                        "Sheena", "Sheila", "Sheilah", "Shela", "Shelba", "Shelby", "Sheldon", "Shelia", "Shella",
                        "Shelley", "Shelli", "Shellie", "Shelly", "Shelton", "Shemeka", "Shemika", "Shena", "Shenika",
                        "Shenita", "Shenna", "Shera", "Sheree", "Sherell", "Sheri", "Sherice", "Sheridan", "Sherie",
                        "Sherika", "Sherill", "Sherilyn", "Sherise", "Sherita", "Sherlene", "Sherley", "Sherly",
                        "Sherlyn", "Sherman", "Sheron", "Sherrell", "Sherri", "Sherrie", "Sherril", "Sherrill",
                        "Sherron", "Sherry", "Sherryl", "Sherwood", "Shery", "Sheryl", "Sheryll", "Shiela", "Shila",
                        "Shiloh", "Shin", "Shira", "Shirely", "Shirl", "Shirlee", "Shirleen", "Shirlene", "Shirley",
                        "Shirly", "Shizue",
                        "Shizuko", "Shon", "Shona", "Shonda", "Shondra", "Shonna", "Shonta", "Shoshana", "Shu", "Shyla",
                        "Sibyl", "Sid", "Sidney", "Sierra", "Signe", "Sigrid", "Silas", "Silva", "Silvana", "Silvia",
                        "Sima", "Simon", "Simona", "Simone", "Simonne", "Sina", "Sindy", "Siobhan", "Sirena", "Siu",
                        "Sixta", "Skye", "Slyvia", "So", "Socorro", "Sofia", "Soila", "Sol", "Solange", "Soledad",
                        "Solomon", "Somer", "Sommer", "Son", "Sona", "Sondra", "Song", "Sonia", "Sonja", "Sonny",
                        "Sonya", "Soo", "Sook", "Soon", "Sophia", "Sophie", "Soraya", "Sparkle", "Spencer", "Spring",
                        "Stacee", "Stacey", "Staci", "Stacia", "Stacie", "Stacy", "Stan", "Stanford", "Stanley",
                        "Stanton", "Star", "Starla", "Starr", "Stasia", "Stefan", "Stefani", "Stefania", "Stefanie",
                        "Stefany", "Steffanie", "Stella", "Stepanie", "Stephaine", "Stephan", "Stephane", "Stephani",
                        "Stephania", "Stephanie", "Stephany", "Stephen", "Stephenie", "Stephine", "Stephnie",
                        "Sterling", "Steve", "Steven", "Stevie", "Stewart", "Stormy", "Stuart", "Su", "Suanne", "Sudie",
                        "Sue", "Sueann", "Suellen", "Suk", "Sulema", "Sumiko", "Summer", "Sun", "Sunday", "Sung",
                        "Sunni", "Sunny", "Sunshine", "Susan", "Susana", "Susann", "Susanna", "Susannah", "Susanne",
                        "Susie", "Susy", "Suzan", "Suzann", "Suzanna", "Suzanne", "Suzette", "Suzi", "Suzie", "Suzy",
                        "Svetlana", "Sybil", "Syble", "Sydney", "Sylvester", "Sylvia", "Sylvie", "Synthia", "Syreeta",
                        "Ta", "Tabatha", "Tabetha", "Tabitha", "Tad", "Tai", "Taina", "Taisha", "Tajuana", "Takako",
                        "Takisha", "Talia", "Talisha", "Talitha", "Tam", "Tama", "Tamala", "Tamar", "Tamara", "Tamatha",
                        "Tambra", "Tameika", "Tameka", "Tamekia", "Tamela", "Tamera", "Tamesha", "Tami", "Tamica",
                        "Tamie", "Tamika", "Tamiko", "Tamisha", "Tammara", "Tammera", "Tammi", "Tammie", "Tammy",
                        "Tamra", "Tana", "Tandra", "Tandy", "Taneka", "Tanesha", "Tangela", "Tania", "Tanika",
                        "Tanisha", "Tanja", "Tanna", "Tanner", "Tanya", "Tara", "Tarah", "Taren", "Tari", "Tarra",
                        "Tarsha", "Taryn", "Tasha", "Tashia", "Tashina", "Tasia", "Tatiana", "Tatum", "Tatyana",
                        "Taunya", "Tawana", "Tawanda", "Tawanna", "Tawna", "Tawny", "Tawnya", "Taylor", "Tayna", "Ted",
                        "Teddy", "Teena", "Tegan", "Teisha", "Telma", "Temeka", "Temika", "Tempie", "Temple", "Tena",
                        "Tenesha", "Tenisha", "Tennie", "Tennille", "Teodora", "Teodoro", "Teofila", "Tequila", "Tera",
                        "Tereasa", "Terence", "Teresa", "Terese", "Teresia", "Teresita", "Teressa", "Teri", "Terica",
                        "Terina", "Terisa", "Terra", "Terrance", "Terrell", "Terrence", "Terresa", "Terri", "Terrie",
                        "Terrilyn", "Terry", "Tesha", "Tess", "Tessa", "Tessie", "Thad", "Thaddeus", "Thalia", "Thanh",
                        "Thao", "Thea", "Theda", "Thelma", "Theo", "Theodora", "Theodore", "Theola", "Theresa",
                        "Therese", "Theresia", "Theressa", "Theron", "Thersa", "Thi", "Thomas", "Thomasena",
                        "Thomasina", "Thomasine", "Thora", "Thresa", "Thu", "Thurman", "Thuy", "Tia", "Tiana", "Tianna",
                        "Tiara", "Tien", "Tiera", "Tierra", "Tiesha", "Tifany", "Tiffaney", "Tiffani", "Tiffanie",
                        "Tiffany", "Tiffiny", "Tijuana", "Tilda", "Tillie", "Tim", "Timika", "Timmy", "Timothy", "Tina",
                        "Tinisha", "Tiny", "Tisa", "Tish", "Tisha", "Titus", "Tobi", "Tobias", "Tobie", "Toby",
                        "Toccara", "Tod", "Todd", "Toi", "Tom", "Tomas", "Tomasa", "Tomeka", "Tomi", "Tomika", "Tomiko",
                        "Tommie", "Tommy", "Tommye", "Tomoko", "Tona", "Tonda", "Tonette", "Toney", "Toni", "Tonia",
                        "Tonie", "Tonisha", "Tonita", "Tonja", "Tony", "Tonya", "Tora", "Tori", "Torie", "Torri",
                        "Torrie", "Tory", "Tosha", "Toshia", "Toshiko", "Tova", "Towanda", "Toya", "Tracee", "Tracey",
                        "Traci", "Tracie", "Tracy", "Tran", "Trang", "Travis", "Treasa", "Treena", "Trena", "Trent",
                        "Trenton", "Tresa", "Tressa", "Tressie", "Treva", "Trevor", "Trey", "Tricia", "Trina", "Trinh",
                        "Trinidad", "Trinity", "Trish", "Trisha", "Trista", "Tristan", "Troy", "Trudi", "Trudie",
                        "Trudy", "Trula", "Truman", "Tu", "Tuan", "Tula", "Tuyet", "Twana", "Twanda", "Twanna", "Twila",
                        "Twyla", "Ty", "Tyesha", "Tyisha", "Tyler", "Tynisha", "Tyra", "Tyree", "Tyrell", "Tyron",
                        "Tyrone", "Tyson", "Ula", "Ulrike", "Ulysses", "Un", "Una", "Ursula", "Usha", "Ute", "Vada",
                        "Val", "Valarie", "Valda", "Valencia", "Valene", "Valentin", "Valentina", "Valentine", "Valeri",
                        "Valeria", "Valerie", "Valery", "Vallie", "Valorie", "Valrie", "Van", "Vance", "Vanda",
                        "Vanesa", "Vanessa", "Vanetta", "Vania", "Vanita", "Vanna", "Vannesa", "Vannessa", "Vashti",
                        "Vasiliki", "Vaughn", "Veda", "Velda", "Velia", "Vella", "Velma", "Velva", "Velvet", "Vena",
                        "Venessa", "Venetta", "Venice", "Venita", "Vennie", "Venus", "Veola", "Vera", "Verda",
                        "Verdell", "Verdie", "Verena", "Vergie", "Verla", "Verlene", "Verlie", "Verline", "Vern",
                        "Verna", "Vernell", "Vernetta", "Vernia", "Vernice", "Vernie", "Vernita", "Vernon", "Verona",
                        "Veronica", "Veronika", "Veronique", "Versie", "Vertie", "Vesta", "Veta", "Vi", "Vicenta",
                        "Vicente", "Vickey", "Vicki", "Vickie", "Vicky", "Victor", "Victoria", "Victorina", "Vida",
                        "Viki", "Vikki", "Vilma", "Vina", "Vince", "Vincent", "Vincenza", "Vincenzo",
                        "Vinita", "Vinnie", "Viola", "Violet", "Violeta", "Violette", "Virgen", "Virgie", "Virgil",
                        "Virgilio", "Virgina", "Virginia", "Vita", "Vito", "Viva", "Vivan", "Vivian", "Viviana",
                        "Vivien", "Vivienne", "Von", "Voncile", "Vonda", "Vonnie", "Wade", "Wai", "Waldo", "Walker",
                        "Wallace", "Wally", "Walter", "Walton", "Waltraud", "Wan", "Wanda", "Waneta", "Wanetta",
                        "Wanita", "Ward", "Warner", "Warren", "Wava", "Waylon", "Wayne", "Wei", "Weldon", "Wen",
                        "Wendell", "Wendi", "Wendie", "Wendolyn", "Wendy", "Wenona", "Werner", "Wes", "Wesley",
                        "Weston", "Whitley", "Whitney", "Wilber", "Wilbert", "Wilbur", "Wilburn", "Wilda", "Wiley",
                        "Wilford", "Wilfred", "Wilfredo", "Wilhelmina", "Wilhemina", "Will", "Willa", "Willard",
                        "Willena", "Willene", "Willetta", "Willette", "Willia", "William", "Williams", "Willian",
                        "Willie", "Williemae", "Willis", "Willodean", "Willow", "Willy", "Wilma", "Wilmer", "Wilson",
                        "Wilton", "Windy", "Winford", "Winfred", "Winifred", "Winnie", "Winnifred", "Winona", "Winston",
                        "Winter", "Wm", "Wonda", "Woodrow", "Wyatt", "Wynell", "Wynona", "Xavier", "Xenia", "Xiao",
                        "Xiomara", "Xochitl", "Xuan", "Yadira", "Yaeko", "Yael", "Yahaira", "Yajaira", "Yan", "Yang",
                        "Yanira", "Yasmin", "Yasmine", "Yasuko", "Yee", "Yelena", "Yen", "Yer", "Yesenia", "Yessenia",
                        "Yetta", "Yevette", "Yi", "Ying", "Yoko", "Yolanda", "Yolande", "Yolando", "Yolonda", "Yon",
                        "Yong", "Yoshie", "Yoshiko", "Youlanda", "Young", "Yu", "Yuette", "Yuk", "Yuki", "Yukiko",
                        "Yuko", "Yulanda", "Yun", "Yung", "Yuonne", "Yuri", "Yuriko", "Yvette", "Yvone", "Yvonne",
                        "Zachariah", "Zachary", "Zachery", "Zack", "Zackary", "Zada", "Zaida", "Zana", "Zandra", "Zane",
                        "Zelda", "Zella", "Zelma", "Zena", "Zenaida", "Zenia", "Zenobia", "Zetta", "Zina", "Zita",
                        "Zoe", "Zofia", "Zoila", "Zola", "Zona", "Zonia", "Zora", "Zoraida", "Zula", "Zulema",
                        "Zulma"));
    }

    static ArrayList<String> compileEnglishCustomNameList() {
        ArrayList<String> names = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(new FileInputStream("custom_name_list.txt"), "UTF8"));
            String str;
            while ((str = in.readLine()) != null) {
                names.add(str);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return names;
    }

    public static void main(String[] args) {
        try {
            StringBuffer clustered = EntitiesClustering.clusterize(args[0], Names.Italian);
            System.out.println(clustered.toString());
        } catch (Exception e) {
            System.err.println("Usage: jar <name of file>");
        }

    }
}

