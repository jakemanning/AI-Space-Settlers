package capp7507;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of populations (we store this in 'knowledge_collection.xml.gz)
 * So we can keep track of populations over time (and plot them)
 */
public class PopulationCollection {
    private List<KnowledgePopulation> populations;

    PopulationCollection() {
        populations = new ArrayList<>();
    }

    public void add(KnowledgePopulation population) {
        populations.add(population);
    }

    public int size() {
        return populations.size();
    }

    public KnowledgePopulation getPopulation(int i) {
        return populations.get(i);
    }
}
