package capp7507;

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

    public void evaluateFitnessForCurrentMember(Collection<SessionCollection> sessions) {
        double fitness = sessions.stream()
                .mapToDouble(SessionCollection::averageFitness)
                .average()
                .orElse(0);
        System.out.println("eval: " + fitness);
        fitnessScores[currentPopulationCounter] = fitness;
    }

    /**
     * Return true if we have reached the end of this generation and false otherwise
     *
     * @return
     */
    boolean isGenerationFinished() {
        return currentPopulationCounter == population.length;
    }

    /**
     * Return the next member of the population (handles wrapping around by going
     * back to the start but then the assumption is that you will reset with crossover/selection/mutation
     *
     * @return
     */
    KnowledgeChromosome getNextMember() {
        KnowledgeChromosome member = population[currentPopulationCounter];
        currentPopulationCounter++;
        return member;
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
    KnowledgeChromosome getFirstMember() {
        return population[0];
    }

    private KnowledgeChromosome[] parentSelection(KnowledgeChromosome[] population) {
        double s = DoubleStream.of(fitnessScores).sum();
        KnowledgeChromosome[] newPopulation = new KnowledgeChromosome[population.length];
        for (int i = 0; i < population.length; i++) {
            double p = random.nextDouble() * s;
            int j = 0;
            while (p < s) {
                j++;
                p += fitnessScores[j];
            }
            newPopulation[i] = population[j].deepCopy();
        }
        return newPopulation;
    }

    /**
     * Uses whole arithmetic crossover with 𝜶 = 0.5
     * Child 1: 𝜶 * x + (1 - a) * y
     * Child 2: 𝜶 * y + (1 - a) * x
     * @param parents chromosomes to act on
     * @return chromosomes with whole arithmetic crossover calculation
     */
    private KnowledgeChromosome[] crossover(KnowledgeChromosome[] parents) {
        final double alpha = 0.5; // Some value a ϵ [0, 1]
        KnowledgeChromosome[] newPopulation = deepCopyOfPopulation(parents);
        for (int i = 0; i < newPopulation.length; i++) {
            KnowledgeChromosome mom = newPopulation[i];
            KnowledgeChromosome dad = newPopulation[i + 1];
            for(int j = 0; j < mom.getCoefficients().length; ++j) {
                double momCoeff = mom.getCoefficients()[j];
                double dadCoeff = dad.getCoefficients()[j];
                mom.getCoefficients()[j] = alpha * momCoeff + (1 - alpha) * dadCoeff;
                dad.getCoefficients()[j] = alpha * dadCoeff + (1 - alpha) * momCoeff;
            }
        }
        return newPopulation;
    }

    private KnowledgeChromosome[] mutate(KnowledgeChromosome[] population) {
        KnowledgeChromosome[] newPopulation = deepCopyOfPopulation(population);
        for (KnowledgeChromosome chromosome : newPopulation) {
            for (int j = 0; j < chromosome.getCoefficients().length; j++) {
                if (random.nextDouble() < (0.1)) {
                    chromosome.getCoefficients()[j] = chromosome.resetCoefficient(j, random);
                }
            }
        }
        return newPopulation;
    }

    private KnowledgeChromosome[] deepCopyOfPopulation(KnowledgeChromosome[] population) {
        KnowledgeChromosome[] copy = new KnowledgeChromosome[population.length];
        for (int i = 0; i < population.length; i++) {
            copy[i] = population[i].deepCopy();
        }
        return copy;
    }

    public KnowledgePopulation deepCopy() {
        KnowledgePopulation copy = new KnowledgePopulation(population.length);
        for (int i = 0; i < population.length; i++) {
            copy.population[i] = population[i].deepCopy();
        }
        copy.random = random;
        copy.fitnessScores = Arrays.copyOf(fitnessScores, fitnessScores.length);
        copy.currentPopulationCounter = currentPopulationCounter;
        return copy;
    }
}
