package capp7507;

import spacesettlers.simulator.Toroidal2DPhysics;


/**
 * Stores a whole population of individuals for genetic algorithms / evolutionary computation
 */
public class KnowledgePopulation {
    private KnowledgeChromosome[] population;

    private int currentPopulationCounter;

    private double[] fitnessScores;

    /**
     * Make a new empty population
     */
    public KnowledgePopulation(int populationSize) {
        super();

        // start at member zero
        currentPopulationCounter = 0;

        // make an empty population
        population = new KnowledgeChromosome[populationSize];

        for (int i = 0; i < populationSize; i++) {
            population[i] = new KnowledgeChromosome();
        }

        // make space for the fitness scores
        fitnessScores = new double[populationSize];
    }

    /**
     * Currently scores all members as zero (the student must implement this!)
     *
     * @param space
     */
    void evaluateFitnessForCurrentMember(Toroidal2DPhysics space) {
        KnowledgeChromosome currentMember = population[currentPopulationCounter % population.length];

        fitnessScores[currentPopulationCounter] = 0;
    }

    /**
     * Return true if we have reached the end of this generation and false otherwise
     *
     * @return
     */
    boolean isGenerationFinished() {
        if (currentPopulationCounter == population.length) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Return the next member of the population (handles wrapping around by going
     * back to the start but then the assumption is that you will reset with crossover/selection/mutation
     *
     * @return
     */
    KnowledgeChromosome getNextMember() {
        currentPopulationCounter++;

        return population[currentPopulationCounter % population.length];
    }

    /**
     * Does crossover, selection, and mutation using our current population.
     * Note, none of this is implemented as it is up to the student to implement it.
     * Right now all it does is reset the counter to the start.
     */
    void makeNextGeneration() {
        currentPopulationCounter = 0;
    }

    /**
     * Return the first member of the popualtion
     *
     * @return
     */
    public KnowledgeChromosome getFirstMember() {
        return population[0];
    }
}


