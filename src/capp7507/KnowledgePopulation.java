package capp7507;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.DoubleStream;


/**
 * Stores a whole population of individuals for genetic algorithms / evolutionary computation
 */
public class KnowledgePopulation {
    private Random random;
    private KnowledgeChromosome[] population;
    private final static int ELITES_TO_TAKE = 5;
    private final static double MUTATION_RATE = 0.05;

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
    void evaluateFitnessForCurrentMember(Collection<SessionCollection> sessions) {
        double fitness = sessions.stream()
                .mapToDouble(SessionCollection::averageFitness)
                .sum();
        System.out.println(String.format("eval: %f", fitness));
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
        TreeSet<ChromosomeFitness> fitnessTreeSet = new TreeSet<>(Comparator.comparingDouble(chromosome -> chromosome.fitnessScore));
        for(int i = 0; i < population.length; ++i) {
            fitnessTreeSet.add(new ChromosomeFitness(population[i], fitnessScores[i], i));
        }
        return fitnessTreeSet;
    }

    private KnowledgeChromosome[] carryElitesOverTo(KnowledgeChromosome[] mutated, TreeSet<ChromosomeFitness> eliteTree) {
        Iterator<ChromosomeFitness> worstIterator = eliteTree.iterator();
        Iterator<ChromosomeFitness> bestIterator = eliteTree.descendingIterator();

        for(int i = 0; i < ELITES_TO_TAKE && worstIterator.hasNext() && bestIterator.hasNext(); ++i) {
            ChromosomeFitness best = bestIterator.next();
            ChromosomeFitness worst = worstIterator.next();
            mutated[worst.index] = best.chromosome.deepCopy(); // Replace worst with best
        }
        return mutated;
    }

    /**
     * Return the first member of the popualtion
     *
     * @return
     */
    KnowledgeChromosome getCurrentMember() {
        return population[currentPopulationCounter];
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
     * Ensures all values are shifted to (near) zero.
     * Near zero to ensure the worst chromosome has a non-zero chance of
     * getting picked
     * @param fitnessScores scores to shift
     * @return shifted scores
     */
    private double[] shiftMinToZero(double[] fitnessScores) {
        double min = Double.MAX_VALUE;
        double sum = 0;
        for (double fitness : fitnessScores) {
            if (fitness < min) {
                min = fitness;
            }
            sum += fitness;
        }
        double minFitnessPossible = sum * 0.01; // A 1% chance of being picked

        double[] result = new double[fitnessScores.length];
        for (int i = 0; i < fitnessScores.length; i++) {
            result[i] = fitnessScores[i] - min + minFitnessPossible;
        }
        return result;
    }

    /**
     * Uses single-point crossover
     * @param parents chromosomes to act on
     * @return chromosomes switched on crossover
     */
    private KnowledgeChromosome[] crossover(KnowledgeChromosome[] parents) {
        KnowledgeChromosome[] newPopulation = deepCopyOfPopulation(parents);

        for (int i = 0; i < newPopulation.length; i += 2) {
            KnowledgeChromosome mom = newPopulation[i];
            KnowledgeChromosome dad = newPopulation[(i + 1) % newPopulation.length];

            int crossPoint = random.nextInt(mom.getCoefficients().length);
            for (int j = crossPoint; j < mom.getCoefficients().length; ++j) {
                double momCoeff = mom.getCoefficients()[j];
                double dadCoeff = dad.getCoefficients()[j];

                mom.getCoefficients()[j] = dadCoeff;
                dad.getCoefficients()[j] = momCoeff;
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
                if (random.nextDouble() < MUTATION_RATE) {
                    chromosome.getCoefficients()[j] = chromosome.modifyCoefficient(j, random);
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
    ChromosomeFitness getBestMember() {
        KnowledgeChromosome best = population[0];
        double highestFitness = fitnessScores[0];
        int number = 0;
        for (int i = 1; i < population.length; i++) {
            KnowledgeChromosome chromosome = population[i];
            double fitness = fitnessScores[i];
            if (fitness > highestFitness) {
                best = chromosome;
                highestFitness = fitness;
                number = i;
            }
        }
        return new ChromosomeFitness(best, highestFitness, number);
    }

     class ChromosomeFitness {
        KnowledgeChromosome chromosome;
        double fitnessScore;
        int index;

        ChromosomeFitness(KnowledgeChromosome chromosome, double fitnessScore, int index) {
            this.chromosome = chromosome;
            this.fitnessScore = fitnessScore;
            this.index = index;
        }
    }

    public int getCurrentPopulationCounter() {
        return currentPopulationCounter;
    }
}
