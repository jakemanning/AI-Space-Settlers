package capp7507;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import spacesettlers.simulator.Toroidal2DPhysics;

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

    private int steps = 0;
    private final String KNOWLEDGE_FILE;
    private Map<UUID, SessionCollection> sessions;

    KnowledgeUtil(String knowledgeFile) {
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

    /**
     *
     * @param space physics
     */
    void think(Toroidal2DPhysics space) {
        // increment the step counter
        steps++;

        // if the step counter is modulo EVALUATION_STEPS, then evaluate this member and move to the next one
        int EVALUATION_STEPS = 2000;
        if (steps % EVALUATION_STEPS == 0) {
            // note that this method currently scores every policy as zero as this is part of
            // what the student has to do
            population.evaluateFitnessForCurrentMember(space, sessions.values());

            // move to the next member of the population
            currentPolicy = population.getNextMember();

            if (population.isGenerationFinished()) {
                // note that this is also an empty method that a student needs to fill in
                population.makeNextGeneration();

                currentPolicy = population.getNextMember();
            }

        }
    }

    private void loadKnowledge() {
        sessions = new HashMap<>();
        XStream xStream = new XStream();
        xStream.alias("KnowledgePopulation", KnowledgePopulation.class);

        // try to load the population from the existing saved file.  If that fails, start from scratch
        try {
            population = loadFile(xStream);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it other than a null pointer because
            // the error will happen the first time you run
            System.out.println("No existing population found - starting a new one from scratch");
            int POPULATION_SIZE = 25;
            population = new KnowledgePopulation(POPULATION_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        currentPolicy = population.getFirstMember();
    }

    private KnowledgePopulation loadFile(XStream xStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(KNOWLEDGE_FILE); GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            System.out.println("Loaded KnowledgePopulation from " + KNOWLEDGE_FILE);
            return (KnowledgePopulation) xStream.fromXML(gzipInputStream);
        }
    }

    public void shutDown() {
        XStream xStream = new XStream();
        xStream.alias("KnowledgePopulation", KnowledgePopulation.class);

        try {
            createFile(xStream, population);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it somehow as it means your knowledge didn't save
            System.out.println("Can't save knowledge file in shutdown");
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createFile(XStream xStream, KnowledgePopulation population) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(KNOWLEDGE_FILE); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            xStream.toXML(population, gzipOutputStream);
            System.out.println("Saved KnowledgePopulation to " + KNOWLEDGE_FILE);
        }
    }
}
