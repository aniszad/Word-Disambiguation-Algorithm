import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.dictionary.Dictionary;
import org.apache.poi.xwpf.usermodel.*;
import rita.RiTa;
import org.w3c.dom.Element;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;


public class DisambiguationAlgorithm {

    private static Double overAllScore = 0.0;
    private static int semcorSentencesNumber = 0;
    private static Integer semcorSentenceIndex = 0;
    private static final HashMap<String, Synset> Ec = new HashMap<>(); // Concept retenus
    private static final Set<String> E = new HashSet<>();  // Set pour les mots non ambigus du texte
    private List<String> S;  // Les mots du text

    static XWPFDocument document = new XWPFDocument();
    XWPFTable table = document.createTable();

    private List<String> uneditedSentence;  // Les mots du text
    private final Dictionary dict; // Dictionnaire de wordnet
    private Set<Synset> Ci = new HashSet<>(); // Concepts associés avec le mot
    private Set<Synset> Cnl = new HashSet<>(); // Concepts associés avec le mot voisin a gauche
    private Set<Synset> Cnr = new HashSet<>(); // Concepts associés avec le mot voising a droite
    private Integer pos = 0;

    // Constructor to set the input sentence
    public DisambiguationAlgorithm(List<String> sentence, List<String> uneditedSentence) throws IOException, JWNLException {
        // initialization de la liste des mot obtenus après le traitement (découpage et lemmatisation)
        this.S = sentence;
        this.uneditedSentence = uneditedSentence;
        // Create a table

        // Specify the number of columns in the table
        int numColumns = 5;
        XWPFTableRow headerRow = table.getRow(0); // First row is the header row
        // Add headers to the table with the correct number of columns
        for (int i = 0; i < numColumns; i++) {
            XWPFTableCell cell = headerRow.createCell();
            // Customize header cell content as needed
            cell.setText("Header " + (i + 1));
        }
        headerRow.getCell(0).setText("Words");
        headerRow.getCell(1).setText("Candidates");
        headerRow.getCell(2).setText("Synset chosen by the algo");
        headerRow.getCell(3).setText("correct Synset by semCor");
        headerRow.getCell(4).setText("result");

        // initialization de dectionnaire wordnet - extJWNL
        dict = Dictionary.getDefaultResourceInstance();
        // obtenir les mots non ambigus du texte et les mettre dans E
        addUnambiguousWordsToE(sentence);
    }

    // Main
    public static void main(String[] args) throws IOException, JWNLException {
        BrownCorpus brownCorpus = new BrownCorpus();
        semcorSentencesNumber = brownCorpus.getSemcorList().size();
        List<List<Element>> semcorElementsList = brownCorpus.getSemcorList();
        for (int i = 0; i < semcorElementsList.size(); i++) {
            Ec.clear();
            E.clear();
            // texte de teste
            StringBuilder semcorText = new StringBuilder();
            StringBuilder uneditedSemcorText = new StringBuilder();
            semcorSentenceIndex = i;
            for (Element semcorElement : semcorElementsList.get(i)){
                if (semcorElement.hasAttribute("lexsn")&& semcorElement.hasAttribute("wnsn") && semcorElement.getAttribute("pos").equals("NN")){
                    semcorText.append(semcorElement.getTextContent()).append(" ");
                    uneditedSemcorText.append(semcorElement.getTextContent()).append(":");
                }
            }

           // List<String> uneditedSentence = Arrays.asList(String.valueOf(uneditedSemcorText).split(":"));
            // découpage et lemmatisation de texte en utilisant Rita
            List<String> sentence = processString(String.valueOf(semcorText));
            List<String> uneditedSentence = processOriginalString(String.valueOf(uneditedSemcorText));

            // initialisation de la class Disabiguation Algorithm
            System.out.println(uneditedSentence);
            System.out.println(sentence);
            if (!sentence.isEmpty() && !uneditedSentence.isEmpty()){
                DisambiguationAlgorithm algorithm = new DisambiguationAlgorithm(sentence, uneditedSentence);
                // Démarrer le processus de disambiguation
                algorithm.mainProcedure();

            }else{
                semcorSentencesNumber-=1;
            }

        }
        System.out.println(overAllScore);
        System.out.println("Overall algorithme evaluation score : "+(overAllScore/semcorSentencesNumber)*100+"%");
        // Create a paragraph
        XWPFParagraph paragraph = document.createParagraph();
        // Create a run (a chunk of text) within the paragraph
        XWPFRun run = paragraph.createRun();
        // Set the text content for the run
        run.setText("Overall algorithme evaluation score : "+(overAllScore/semcorSentencesNumber)*100+"%");
        FileOutputStream fos = new FileOutputStream("evaluation_table.docx");
        document.write(fos);
        fos.close();
    }

    private static List<String> processOriginalString(String text) {
        // Tokeniser le texte en mots
        String[] tokens = RiTa.tokenize(text, ":");
        // Créer une liste pour stocker les lemmes
        List<String> words = new ArrayList<>();
        //Lemmatiser et traiter chaque mot, puis l'ajouter à la liste des lemmes
        for (String token : tokens) {
            String lowercaseToken = token.toLowerCase();
            // lemmatisation de mot en utilisant Rita
            String lemma = RiTa.stem(lowercaseToken);
            // vérification si le mot existe dans la base de donnée de wordnet
            Set<Synset> lemmaExists = staticGetSynsetsForWord(lemma);
            if (lemmaExists != null){
                // ajout de lemma à la liste
                words.add(token);
            }
        }
        // returner la liste des mots extraits/traités
        return words;
    }

    private static List<String> removeNonExistantElements(List<String> list) {
        // Tokeniser le texte en mots
        // Créer une liste pour stocker les lemmes
        List<String> lemmas = new ArrayList<>();
        //Lemmatiser et traiter chaque mot, puis l'ajouter à la liste des lemmes
        for (String word : list) {
            String lowercaseToken = word.toLowerCase();
            // vérification si le mot existe dans la base de donnée de wordnet
            Set<Synset> lemmaExists = staticGetSynsetsForWord(lowercaseToken);
            if (lemmaExists != null){
                // ajout de lemma à la liste
                lemmas.add(word);
            }
        }
        // returner la liste des mots extraits/traités
        return lemmas;
    }

    /* Une fonction qui tokenise et lemmatise un texte passé en argument */
    private static List<String> processString(String text) {
        // Tokeniser le texte en mots
        String[] tokens = RiTa.tokenize(text);
        // Créer une liste pour stocker les lemmes
        List<String> lemmas = new ArrayList<>();
        //Lemmatiser et traiter chaque mot, puis l'ajouter à la liste des lemmes
        for (String token : tokens) {
            String lowercaseToken = token.toLowerCase();
            // lemmatisation de mot en utilisant Rita
                String lemma = RiTa.stem(lowercaseToken);
            // vérification si le mot existe dans la base de donnée de wordnet
                Set<Synset> lemmaExists = staticGetSynsetsForWord(lemma);
                if (lemmaExists != null){
                    // ajout de lemma à la liste
                    lemmas.add(lemma);
                }
        }
        // returner la liste des mots extraits/traités
        return lemmas;
    }

    // Fonction principale de disambiguation
    public void mainProcedure() throws JWNLException {
        System.out.println(S.toString());
        // initialisation de l'index d'itération sur la liste des mots
        int k = 0;
        // initialisation de l'index de position
        pos = 0;
        // initialisation de mot a traiter dans l'itération actuelle
        System.out.println(S.get(k));
        String wordToDisambiguate = S.get(k);
        // Vérification si le texte n'a pas de mot non-ambigus (si le texte n'a pas de mot non-ambigus le texte intraitable)
        if (!E.isEmpty()) {
            // vérification si on a arrivé  la fin du texte
            while (k < S.size()) {
                // vérification si le mot est ambigu ou pas (ambigu -> ajout de sysnet directement dans Ec, non-ambigu -> désambiguïsation)
                if (!E.contains(wordToDisambiguate)) {
                    // lever l'ambiguïté du mot dans l'index k
                    disambiguation(k);
                    // incrémenter l'index d'itération
                    k = pos + 1;
                    // incrémenter l'index de position
                    pos = pos + 1;
                } else {
                    // Si le mot n'est pas ambigu -> ajoutez son synset directement à Ec
                    // obtenir les synset du mot dans ll'index k (la liste a toujours un seule mot car le mot est non-ambigu)
                    Synset C = new ArrayList<>(getSynsetsForWord(wordToDisambiguate)).get(0);
                    // on met le mot et son synset (concept) dans Ec
                    Ec.put(wordToDisambiguate,C);
                    // incrémenter l'index de position
                    pos = k + 1;
                    // incrémenter l'index d'itération
                    k = k + 1;
                }
                // obtention de mot suivant à lever l'ambiguïté
                if (k < S.size()) {
                    wordToDisambiguate = S.get(k);
                }
            }
            checkResults();
        } else {
            // si le texte n'a pas de mot non-ambigus le texte intraitable
            semcorSentencesNumber-=1;
            System.out.println("This text has no non-ambiguous words");
        }
    }

    private void checkResults() throws JWNLException {
        double score = 0.0;
        System.out.println("Words"+" || " + "Candidates"+" || "  + "Synset chosen by the algo" +" || " +"correct Synset by semCor"+" || " +"result") ;
        for (String word : uneditedSentence) {
            List<String> lemmaForWord = processString(word);
            if (!lemmaForWord.isEmpty()){
                List<Synset> synsets = new ArrayList<>(getSynsetsForWord(lemmaForWord.get(0)));
                List<String> synsetOffsets = new ArrayList<>();
                // Iterate through the synsets and extract their offsets
                for (Synset synset : synsets) {
                    synsetOffsets.add(String.valueOf(synset.getOffset()));
                }
                List<String> candidatsOffsets = synsetOffsets;
                Synset retrievedSynset = Ec.get(RiTa.stem(RiTa.tokenize(word)[0]).toLowerCase());

                BrownCorpus brownCorpus = new BrownCorpus();
                List<Element> semcorElementsList = brownCorpus.getSemcorList().get(semcorSentenceIndex);
                for (Element semcorElement : semcorElementsList) {
                    if (semcorElement.getTextContent().equals(word)) {
                        Synset correctSynset = getSynsetWithKey(semcorElement);
                        if (correctSynset != null && retrievedSynset != null){
                            if (correctSynset.getOffset() == retrievedSynset.getOffset()) {
                                score++;
                                // Printing the results
                                System.out.print(word + " ||");
                                for (int k =0;k<candidatsOffsets.size();k++){
                                    System.out.print("synset" + k + " ,");
                                }
                                System.out.print("||");
                                System.out.print("synset" + candidatsOffsets.indexOf(String.valueOf(retrievedSynset.getOffset())) + " || ");
                                System.out.print("synset" + candidatsOffsets.indexOf(String.valueOf(correctSynset.getOffset())) + " || ");
                                System.out.print("Success");
                                System.out.println();
                                // Creating a docx file
                                try {
                                    for (String w : uneditedSentence) {
                                        StringBuilder candidatesOffsetsText = new StringBuilder();
                                        for (int k =0;k<candidatsOffsets.size();k++){
                                            candidatesOffsetsText.append(", synset"+k);
                                        }
                                        // Create a new row for each iteration and add data to cells
                                        XWPFTableRow row = table.createRow();
                                        row.getCell(0).setText(w);
                                        // Customize the content of other cells as needed for your logic
                                        row.getCell(1).setText(String.valueOf(candidatesOffsetsText));
                                        row.getCell(2).setText("synset"+String.valueOf(candidatsOffsets.indexOf(String.valueOf(retrievedSynset.getOffset()))));
                                        row.getCell(3).setText("synset"+String.valueOf(candidatsOffsets.indexOf(String.valueOf(correctSynset.getOffset()))));
                                        row.getCell(4).setText("Success");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            } else {
                                // printing results
                                System.out.print(word + " || ");
                                for (int k =0;k<candidatsOffsets.size();k++){
                                    System.out.print("synset" + k + " ,");
                                }
                                System.out.print(" || ");
                                System.out.print("synset" + candidatsOffsets.indexOf(String.valueOf(retrievedSynset.getOffset())) + " || ");
                                System.out.print("synset" + candidatsOffsets.indexOf(String.valueOf(correctSynset.getOffset())) + " || ");
                                System.out.print("Fail");
                                System.out.println();

                                // Creating a docx file
                                try {
                                    for (String w : uneditedSentence) {
                                        StringBuilder candidatesOffsetsText = new StringBuilder();
                                        for (int k =0;k<candidatsOffsets.size();k++){
                                            candidatesOffsetsText.append(", synset"+k);
                                        }
                                        XWPFTableRow row = table.createRow();
                                        row.getCell(0).setText(w);
                                        row.getCell(1).setText(String.valueOf(candidatesOffsetsText));
                                        row.getCell(2).setText("synset"+String.valueOf(candidatsOffsets.indexOf(String.valueOf(retrievedSynset.getOffset()))));
                                        row.getCell(3).setText("synset"+String.valueOf(candidatsOffsets.indexOf(String.valueOf(correctSynset.getOffset()))));
                                        row.getCell(4).setText("Fail");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                    }else{

                        }
                    }
                }
                }
        }
        overAllScore+=score/Ec.size();
        System.out.println("Current sentence evaluation result : "+score/Ec.size()*100+"%");
        System.out.println("__________________________________________________________________________________________________________________________________");
        XWPFTableRow row = table.createRow();
        row.getCell(0).setText("sentence end");
        row.getCell(1).setText("sentence end");
        row.getCell(2).setText("sentence end");
        row.getCell(3).setText("sentence end");
        row.getCell(4).setText("sentence end");

    }

    private Synset getSynsetWithKey(Element semcorElement) throws JWNLException {
            if (semcorElement.hasAttribute("wnsn") && semcorElement.hasAttribute("lexsn")){
                StringBuilder stringBuilder = new StringBuilder();
                String wnsn = String.valueOf(stringBuilder.append(semcorElement.getAttribute("lemma"))
                        .append("%")
                        .append(semcorElement.getAttribute("lexsn")));
                Word correctWord = dict.getWordBySenseKey(wnsn);
                if (correctWord!=null){
                    Synset correctSynset = correctWord.getSynset();
                    return correctSynset;
                }else{
                    return null;
                }
            }else{
                return null;
            }

    }

    /* une fonction qui parcourt la liste des mots et recherche les mots non ambigus */
    private void addUnambiguousWordsToE(List<String> sentence) {
        try {
            // itération sur las liste 'sentence'
            for (String word : sentence) {
                // Récupèrer les synsets pour le mot 'word'
                Set<Synset> synsets = getSynsetsForWord(word);
                // S'il n'y a qu'un seul synset (synsets.size() == 1) il n'y a pas d'ambiguïté donc ajoutez-le au set E
                if (synsets.size() == 1) {
                    // ajout de mot non-ambigu 'word' a E
                    E.add(word);
                    // ajout de mot non-ambigu 'word' et son synset a Ec
                    List<Synset> synsetList = new ArrayList<>(synsets);
                    Ec.put(word, synsetList.get(0));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(E.toString());
    }

    private void disambiguation(int i) {
        int j;
        // récupérer a lever l'ambiguïté
        String wordToDisambiguate = S.get(i);
        // récupérer a le mot voisin a gauche
        String nl = (i > 0) ? S.get(i - 1) : null;
        // récupérer a le mot voisin a droite
        String nr = (i < S.size() - 1) ? S.get(i + 1) : null;

        // récupérer a les synset de mot actuelle
        Ci = getSynsetsForWord(wordToDisambiguate);
        // vérification si le mot voisin a gauche exist
        if (nl != null) {
            // vérification si le mot voisin a gauche est déja traité ou non-ambigu(disambiguated)
            if (Ec.containsKey(nl)){
                // récupération de la synset de mot voisin a gauche déja traité ou non-ambigu
                Synset previouslyObtainedSysnet = Ec.get(nl);
                Set<Synset> newSet = new HashSet<Synset>();
                newSet.add(previouslyObtainedSysnet);
                Cnl = newSet;
            }else{
                // récupérer set des sysnets si le mot n'est pas déja traité ou il est ambigu
                Cnl = getSynsetsForWord(nl);
            }
        } else {
            Cnl.clear(); // pas de mot voisin a gauche, vider
        }
        // vérification si le mot voisin a droite exist
        if (nr != null) {
            // vérification si le mot voisin a droite est déja traité ou non-ambigu(disambiguated)
            if (Ec.containsKey(nr)){
                // récupération de la synset de mot voisin a droite déja traité ou non-ambigu
                Synset previouslyObtainedSysnet = Ec.get(nr);
                Set<Synset> newSet = new HashSet<Synset>();
                newSet.add(previouslyObtainedSysnet);
                Cnr = newSet;
            }else{
                // récupérer set des sysnets si le mot n'est pas déja traité ou il est ambigu
                Cnr = getSynsetsForWord(nr);
            }
        } else {
            Cnr.clear(); // pas de mot voisin a gauche, vider
        }

        // Cas 1 : les deux mots de droite et de gauche exists et ne sont non-ambigus
        if (nl != null && nr != null && E.contains(nl) && E.contains(nr)) {
            // on ajout les deux set des synsets Cnl et Cnr
            Set<Synset> C1 = Cnl;
            C1.addAll(Cnr);
            // trouver le synset la plus proche en fonction du mot à gauche et à droite
            Synset C = findClosestSynsetPair(Ci, C1);
            // on ajoute le mot traité a le set des mots non-ambigus E
            E.add(wordToDisambiguate);
            // on ajoute le mot traité et son synset a le set des concepts retenus Ec
            Ec.put(wordToDisambiguate,C);
        } else
            // Cas 2 : seul le mot voisin à gauche exist et n'est pas ambigu
            if (nl != null && E.contains(nl)) {
                // trouver le synset la plus proche en fonction du mot à gauche  (en utilisant la formule de leacock)
                Synset C = findClosestSynsetPair(Ci, Cnl);
                // on ajoute le mot traité a le set des mots non-ambigus E
                E.add(wordToDisambiguate);
                // on ajoute le mot traité et son synset a le set des concepts retenus Ec
                Ec.put(wordToDisambiguate,C);
        } else
            // Case 3 : seul le mot voisin à droite exist et n'est pas ambigu
            if (nr != null && E.contains(nr)) {
                // trouver le synset la plus proche en fonction du mot à droite (en utilisant la formule de leacock)
                Synset C = findClosestSynsetPair(Ci, Cnr);
                // on ajoute le mot traité a le set des mots non-ambigus E
                E.add(wordToDisambiguate);
                // on ajoute le mot traité et son synset a le set des concepts retenus Ec
                Ec.put(wordToDisambiguate,C);
        } else {    // Cas 3 : il n'y a pas de mot voisin non ambigu
                // checking for the end of the text
                if (i < S.size() - 1) {
                    // incrémenter l'index j
                    j = i + 1;
                    // lever l'ambiguité pour le mot suivant (recursivité)
                    disambiguation(j);
                    // incrémenter l'index de position
                    pos = pos + 1;

               // Désambiguïsation du mot précédent (laissé sans ambiguïté dans la dernière itération récursive car il n'y a pas de mot voisin non ambigu)
                    // on obient le mot précedent a lever l'ambiguité
                    wordToDisambiguate = S.get(j - 1);
                    // on obient les sysnets du mot
                    Set<Synset> Cj = getSynsetsForWord(wordToDisambiguate);
                    // trouver le synset la plus proche en fonction du mot à droite (en utilisant la formule de leacock)
                    Synset C = findClosestSynsetPair(Cj, Cnr);
                    // on ajoute le mot traité a le set des mots non-ambigus E
                    E.add(wordToDisambiguate);
                    // on ajoute le mot traité et son synset a le set des concepts retenus Ec
                    Ec.put(wordToDisambiguate,C);
                // End of the disambiguation of the precedent word
            }
        }
    }

    // funciton to find the closest synset between two sets - Set<Synset>  (the second one is always a set containing one element except in : Case 1)
    public Synset findClosestSynsetPair(Set<Synset> set1, Set<Synset> set2) {
        // décalaration de la variable qui va contenir la synset la plus proche
        Synset closestSynset = null;
        // initialisation de la variable maxSimilarity utilisé pour trouvé la plus grande similarité
        double maxSimilarity = -1;
        // itération dans le set des synset set1
        for (Synset synset1 : set1) {
            // itération dans le set des synset set2
            for (Synset synset2 : set2) {
                try {
                    // calcul de la similarité entre les deux synset
                    double similarity = calculateLeacockSimilarity(synset1, synset2);
                    // si synset1 et synset2 on une similarité plus grande que la similarité précedente dans maxSimilarity
                    if (similarity > maxSimilarity) {
                        // réaffecter la plus grande valeur actuelle de similarité a maxSimilarity
                        maxSimilarity = similarity;
                        // réaffecter le synset avec la plus grande valeur de similarité a closest synset
                        closestSynset = synset1;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
        // returner le synset trouvé
        return closestSynset;
    }

    // function to retrieve the sysnets for a certain word
    private Set<Synset> getSynsetsForWord(String word) {
        try {
            // List of POS possible
            POS[] posToSearch = {POS.NOUN};
            // iterating through the possible POS types to find the word
            for (POS pos : posToSearch) {
                IndexWord indexWord = dict.lookupIndexWord(pos, word);
                if (indexWord != null) {
                    // returning the word synset
                    return new HashSet<>(indexWord.getSenses());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // word synset is not found -- Happens when the word is not in wordnet - remove the word from the text
        return null;
    }

    // la meme fonction que 'getSynsetsForWord' mais static pour l'utiliser dans une fonction static
    private static Set<Synset> staticGetSynsetsForWord(String word) {
        try {
            // List of POS possible
            POS[] posToSearch = {POS.NOUN};

            Dictionary dictionary = Dictionary.getDefaultResourceInstance();
            // iterating through the possible POS types to find the word
            for (POS pos : posToSearch) {
                IndexWord indexWord = dictionary.lookupIndexWord(pos, word);
                if (indexWord != null) {
                    // returning the word synset
                    return new HashSet<>(indexWord.getSenses());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        // word synset is not found -- Happens when the word is not in wordnet - remove the word from the text
        return null;
    }

    // fonction pour calculer la similarité de Leacock
    public double calculateLeacockSimilarity(Synset synset1, Synset synset2) throws JWNLException {
        // on calcule la profondeur de premier hypernym commun entre synset1 et sysnet2
        long commonHyernymDepth = calculateCommonHypernymDepth(synset1, synset2);
        // on calcule la profondeur de la racine (dans wordnet c'est 16)
        long depthRoot = getRootDepth();
        // Calcule de la similarité de Leacock à l'aide de la formule : -log(deepCommonHypernym / (2 * deepRoot))
        double leacockSimilarity = -Math.log10((double) commonHyernymDepth / (2 * depthRoot));
        // returner la valeur de similarité
        return leacockSimilarity;
    }

    // fonction pour calculer la profondeur de l'hypernyme commun entre les mots
    public long calculateCommonHypernymDepth(Synset synset1, Synset synset2) throws JWNLException {

        // récupérer la liste des hypernym pour synset 1 et synset 2
        PointerTargetNodeList hypernymList1 = getHypernymList(synset1);
        PointerTargetNodeList hypernymList2 = getHypernymList(synset2);

        for (PointerTargetNode hypernymm1 : hypernymList1) {
            for (PointerTargetNode hypernymm2 : hypernymList2) {
                // checking if the hypernyms of sysnet1 and sysnet2 are equal
                if (hypernymm1.getSynset().equals(hypernymm2.getSynset())) {
                    // common hypernym found -- return its depth
                    if (calculateDepth(hypernymm1.getSynset())>3){
                        return calculateDepth(hypernymm1.getSynset());
                    }

                }
            }
        }
        // No common hypernym found, return the deepest synset between the two
        return Math.max(calculateDepth(synset1), calculateDepth(synset2));
    }

    // Fonction pour trouver et returner la liste des hypernyms pour un synset donné
    PointerTargetNodeList getHypernymList(Synset synset1) throws JWNLException {
        PointerTargetNodeList hypernymList1 = new PointerTargetNodeList();
        PointerTargetNodeList currentHypernyms = new PointerTargetNodeList();
        // Get the initial hypernyms
        currentHypernyms.addAll(PointerUtils.getDirectHypernyms(synset1));
        while (!currentHypernyms.isEmpty()) {
            PointerTargetNodeList nextHypernyms = new PointerTargetNodeList();
            for (PointerTargetNode hypernymNode : currentHypernyms) {
                // Add the current hypernymNode to the result list
                hypernymList1.add(hypernymNode);
                // Get the direct hypernyms of the current hypernymNode
                PointerTargetNodeList directHypernyms = PointerUtils.getDirectHypernyms(hypernymNode.getSynset());
                // Add the direct hypernyms to the nextHypernyms list for the next iteration
                nextHypernyms.addAll(directHypernyms);
            }
            // Update currentHypernyms for the next iteration
            currentHypernyms = nextHypernyms;
        }
        return hypernymList1;
    }


    // une fonction pour calculer la profondeur d'un synset dans wordnet
    public long calculateDepth(Synset synset) throws JWNLException {
        // initialisation de la valeur de depth par 0
        long depth = 0;
        while (synset != null) {
            //// incrémentation de la profondeur à chaque fois qu'un hyperonyme d'un sysnet est trouvé
            depth++;
            // on avance dans l'hiérarchie de wordnet en récupérant les hypernyms de synset actuelle
            PointerTargetNodeList hypernyms = PointerUtils.getDirectHypernyms(synset);
            // si les hypernyms les hypernyms exist (si on a pas arrivé à la racine de wordnet)
            if (hypernyms != null && !hypernyms.isEmpty()) {
                // réaffecter la variable synset par la synset de hypernym parent trouvé
                synset = hypernyms.get(0).getSynset();
            } else {
                // Y'a pas des hypernyms pour le synset actuelle donc on a arrivé a la racine de wordnet, break loop
                // réaffecter la valeur de sysnet par null pour arreter 'while loop'
                synset = null;
            }
        }
        // returner la profondeur trouvé de la synset
        return depth;
    }

    public Long getRootDepth() {
        return 16L;  // la profondeur de wordnet est 16
    }

}



