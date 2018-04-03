package capp7507;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

class KnowledgeUtil {

    /**
     * The current policy for the team
     */
    private KnowledgeChromosome currentPolicy;

    /**
     * The current population (either being built or being evaluated)
     */
    private KnowledgePopulation population;
    private PopulationCollection populationCollection;

    private int steps = 0;
    private final String KNOWLEDGE_FILE;
    private static final String COLLECTION_FILE = "capp7507/knowledge_collection.xml.gz";
    private Map<UUID, SessionCollection> sessions;
    private static final int POPULATION_SIZE = 100; // Prof: no lower than a hundred
    private XStream xStream;

    KnowledgeUtil(String knowledgeFile) {
        xStream = new XStream();
        xStream.alias("KnowledgePopulation", KnowledgePopulation.class);
        xStream.alias("PopulationCollection", PopulationCollection.class);
        sessions = new HashMap<>();
        this.KNOWLEDGE_FILE = knowledgeFile;
        loadKnowledge();
    }

    SessionCollection getSessionsFor(UUID shipUuid) {
        if (!sessions.containsKey(shipUuid)) {
            // We gettin' litty up in here boiz, let's start learnin'
            sessions.put(shipUuid, new SessionCollection());
        }
        return sessions.get(shipUuid);
    }

    void think() {
        population.evaluateFitnessForCurrentMember(sessions.values());

        // move to the next member of the population
        currentPolicy = population.getNextMember();

        if (population.isGenerationFinished()) {
            populationCollection.add(population.deepCopy());
            population.makeNextGeneration();
            currentPolicy = population.getNextMember();
        }
        sessions.clear();
    }

    private void loadKnowledge() {
        sessions = new HashMap<>();

        // try to load the population from the existing saved file.  If that fails, start from scratch
        try {
            population = loadFile(xStream);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it other than a null pointer because
            // the error will happen the first time you run
            System.out.println("No existing population found - starting a new one from scratch");
            population = new KnowledgePopulation(POPULATION_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            populationCollection = loadCollectionFile(xStream);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it other than a null pointer because
            // the error will happen the first time you run
            System.out.println("No existing population collection found - starting a new one from scratch");
            populationCollection = new PopulationCollection();
        } catch (IOException e) {
            e.printStackTrace();
        }

        currentPolicy = population.getFirstMember();
    }

    private KnowledgePopulation loadFile(XStream xStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(KNOWLEDGE_FILE);
             GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            System.out.println("Loaded KnowledgePopulation from " + KNOWLEDGE_FILE);
            return (KnowledgePopulation) xStream.fromXML(gzipInputStream);
        }
    }

    private PopulationCollection loadCollectionFile(XStream xStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(COLLECTION_FILE);
             GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            System.out.println("Loaded PopulationCollection from " + COLLECTION_FILE);
            return (PopulationCollection) xStream.fromXML(gzipInputStream);
        }
    }

    public void shutDown() {
        try {
            createFile(xStream, population);
            createCollectionFile(xStream, populationCollection);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it somehow as it means your knowledge didn't save
            System.out.println("Can't save knowledge file in shutdown");
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createFile(XStream xStream, KnowledgePopulation population) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(KNOWLEDGE_FILE);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            xStream.toXML(population, gzipOutputStream);
            System.out.println("Saved KnowledgePopulation to " + KNOWLEDGE_FILE);
        }
    }

    private void createCollectionFile(XStream xStream, PopulationCollection collection) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(COLLECTION_FILE);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            xStream.toXML(collection, gzipOutputStream);
            System.out.println("Saved PopulationCollection to " + COLLECTION_FILE);
        }
    }

    public KnowledgeChromosome getCurrentPolicy() {
        return currentPolicy;
    }
}
