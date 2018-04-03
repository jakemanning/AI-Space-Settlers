package capp7507;

import java.util.ArrayList;
import java.util.List;

public class PopulationCollection {
    private List<KnowledgePopulation> populations;

    public PopulationCollection() {
        populations = new ArrayList<>();
    }

    public void add(KnowledgePopulation population) {
        populations.add(population);
    }
}
