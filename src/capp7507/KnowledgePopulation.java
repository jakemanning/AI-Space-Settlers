package capp7507;

import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.stream.DoubleStream;


/**
 * Stores a whole population of individuals for genetic algorithms / evolutionary computation
 */
public class KnowledgePopulation {
    private Random random;
    private KnowledgeChromosome[] population;

    private int currentPopulationCounter;

    private double[] fitnessScores;

    /**
     * Make a new empty population
     */
    public KnowledgePopulation(int populationSize) {
        super();
        random = new Random();

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
    void evaluateFitnessForCurrentMember(Toroidal2DPhysics space, Collection<SessionCollection> sessions) {
        KnowledgeChromosome currentMember = population[currentPopulationCounter % population.length];
        double fitness = sessions.stream()
                .mapToDouble(SessionCollection::averageFitness)
                .average()
                .orElse(0);
        fitnessScores[currentPopulationCounter % population.length] = fitness;
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
        KnowledgeChromosome[] parents = parentSelection(population);
        KnowledgeChromosome[] crossed = crossover(parents);
        KnowledgeChromosome[] mutated = mutate(crossed);
        population = mutated;
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

    private KnowledgeChromosome[] parentSelection(KnowledgeChromosome[] population) {
        double s = DoubleStream.of(fitnessScores).sum();
        KnowledgeChromosome[] newPopulation = new KnowledgeChromosome[population.length];
        for (int i = 0; i < population.length; i++) {
            double p = random.nextDouble() * s;
            int j = 0;
            while (p <= s) {
                p += fitnessScores[j];
                j++;
            }
            newPopulation[i] = population[j];
        }
        return newPopulation;
    }

    private KnowledgeChromosome[] crossover(KnowledgeChromosome[] parents) {
        KnowledgeChromosome[] newPopulation = Arrays.copyOf(parents, parents.length);
        for (int i = 0; i < newPopulation.length - 1; i++) {
            KnowledgeChromosome mom = newPopulation[i];
            KnowledgeChromosome dad = newPopulation[i + 1];
            int crossoverPoint = random.nextInt(mom.getCoefficients().length);
            for (int j = 0; j < crossoverPoint; j++) {
                double temp = mom.getCoefficients()[j];
                mom.getCoefficients()[j] = dad.getCoefficients()[j];
                dad.getCoefficients()[j] = temp;
            }
        }
        return newPopulation;
    }

    private KnowledgeChromosome[] mutate(KnowledgeChromosome[] population) {
        KnowledgeChromosome[] newPopulation = Arrays.copyOf(population, population.length);
        for (KnowledgeChromosome chromosome : newPopulation) {
            if (random.nextDouble() < 0.05) {
                for (int j = 0; j < chromosome.getCoefficients().length; j++) {
                    double mutation = random.nextDouble();
                    if (mutation < 0.25) {
                        chromosome.getCoefficients()[j] = chromosome.resetCoefficient(j, random);
                    }
                }
            }
        }
        return newPopulation;
    }
}
