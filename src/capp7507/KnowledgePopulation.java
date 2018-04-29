package capp7507;

import java.util.*;
import java.util.stream.DoubleStream;


/**
 * Stores a whole population of individuals for genetic algorithms / evolutionary computation
 */
public class KnowledgePopulation {
    private Random random;
    private KnowledgeChromosome[] population;
    private final static double ELITE_PERCENTAGE = 0.1;

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
     * Evaluates fitness using all of our avoid sessions
     * @param sessions all of our avoid sessions
     */
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
     * Does crossover, selection, and mutation using our current population
     */
    void makeNextGeneration() {
        TreeSet<ChromosomeFitness> eliteTree = buildEliteTree(population);
        KnowledgeChromosome[] parents = parentSelection(population);
        KnowledgeChromosome[] crossed = crossover(parents);
        KnowledgeChromosome[] mutated = mutate(crossed);
        population = carryElitesOverTo(mutated, eliteTree);
        currentPopulationCounter = 0;
    }

    private TreeSet<ChromosomeFitness> buildEliteTree(KnowledgeChromosome[] population) {
        // Group them together
        ChromosomeFitness chromosomeFitnesses[] = new ChromosomeFitness[population.length];
        for(int i = 0; i < population.length; ++i) {
            chromosomeFitnesses[i] = new ChromosomeFitness(population[i], fitnessScores[i], i);
        }

        return new TreeSet<>(Comparator.comparingDouble(chromosome -> chromosome.fitnessScore));
    }

    private KnowledgeChromosome[] carryElitesOverTo(KnowledgeChromosome[] mutated, TreeSet<ChromosomeFitness> eliteTree) {
        int howManyToTake = (int) (population.length * ELITE_PERCENTAGE) + 1;

        Iterator<ChromosomeFitness> worstIterator = eliteTree.iterator();
        Iterator<ChromosomeFitness> bestIterator = eliteTree.descendingIterator();

        for(int i = 0; i < howManyToTake && worstIterator.hasNext() && bestIterator.hasNext(); ++i) {
            ChromosomeFitness best = bestIterator.next();
            ChromosomeFitness worst = worstIterator.next();
            mutated[worst.index] = best.chromosome; // Replace worst with best
        }
        return mutated;
    }

    /**
     * Return the first member of the popualtion
     *
     * @return
     */
    KnowledgeChromosome getFirstMember() {
        return population[0];
    }

    /**
     * Uses roulette wheel selection. Shifts all chromosomes evaluations to be above zero
     * @param population population to select from
     * @return the chromosomes we want
     */
    private KnowledgeChromosome[] parentSelection(KnowledgeChromosome[] population) {
        double[] fitnesses = shiftMinToZero(fitnessScores);
        double s = DoubleStream.of(fitnesses).sum();
        KnowledgeChromosome[] newPopulation = new KnowledgeChromosome[population.length];
        for (int i = 0; i < population.length; i++) {
            double p = random.nextDouble() * s;
            int j = -1;
            while (p < s) {
                j++;
                p += fitnesses[j];
            }
            newPopulation[i] = population[j].deepCopy();
        }
        return newPopulation;
    }

    /**
     * Necessary for roulette wheel to work (can't work on negative vals)
     * @param fitnessScores scores to shift
     * @return shifted scores
     */
    private double[] shiftMinToZero(double[] fitnessScores) {
        double min = Double.MAX_VALUE;
        for (double fitness : fitnessScores) {
            if (fitness < min) {
                min = fitness;
            }
        }
        double[] result = new double[fitnessScores.length];
        for (int i = 0; i < fitnessScores.length; i++) {
            result[i] = fitnessScores[i] - min;
        }
        return result;
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
            KnowledgeChromosome dad = newPopulation[(i + 1) % newPopulation.length];
            for(int j = 0; j < mom.getCoefficients().length; ++j) {
                double momCoeff = mom.getCoefficients()[j];
                double dadCoeff = dad.getCoefficients()[j];
                mom.getCoefficients()[j] = alpha * momCoeff + (1 - alpha) * dadCoeff;
                dad.getCoefficients()[j] = alpha * dadCoeff + (1 - alpha) * momCoeff;
            }
        }
        return newPopulation;
    }

    /**
     * We mutate ~1.5% of the time
     * @param population population to mutate
     * @return mutated population
     */
    private KnowledgeChromosome[] mutate(KnowledgeChromosome[] population) {
        KnowledgeChromosome[] newPopulation = deepCopyOfPopulation(population);
        for (KnowledgeChromosome chromosome : newPopulation) {
            for (int j = 0; j < chromosome.getCoefficients().length; j++) {
                if (random.nextDouble() < (0.015)) {
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

    /**
     * @return Which chromosome out of our population is actual best
     */
    KnowledgeChromosome getBestMember() {
        KnowledgeChromosome best = population[0];
        double highestFitness = Double.MIN_VALUE;
        for (int i = 0; i < population.length; i++) {
            KnowledgeChromosome chromosome = population[i];
            double fitness = fitnessScores[i];
            if (fitness > highestFitness) {
                best = chromosome;
                highestFitness = fitness;
            }
        }
        return best;
    }

    private class ChromosomeFitness {
        KnowledgeChromosome chromosome;
        double fitnessScore;
        int index;

        ChromosomeFitness(KnowledgeChromosome chromosome, double fitnessScore, int index) {
            this.chromosome = chromosome;
            this.fitnessScore = fitnessScore;
            this.index = index;
        }
    }
}
