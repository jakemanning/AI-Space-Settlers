import math
import xml.etree.ElementTree as ET


# hits = []
# angles = []
# distances = []
# examples = []

def main():
    """
    Set up global variables and call the decision tree learning algorithm from the book
    """
    tree = ET.parse('/Users/bryancapps/Downloads/fake_data.xml')
    root = tree.getroot()
    hits = []
    angles = []
    distances = []
    examples = []
    for shot in root.iter('capp7507.ShotAttempt'):
        if 'true' == shot.find('missileGone').text:
            examples.append(shot)
            if 'true' == shot.find('shotHitTarget').text:
                hits.append(True)
            else:
                hits.append(False)
            angles.append(shot.find('angle').text)
            distances.append(shot.find('distance').text)
    print('hits: ' + str(hits.count(True)))
    print('misses: ' + str(hits.count(False)))

    tree = decision_tree_learning(examples, ['angle', 'distance'], [])
    print(tree)


# input variables: angle, distance
# output variables: hits

def decision_tree_learning(examples, attributes, parent_examples):
    """
    learn a decision tree based on the given examples where data on the attributes was collected
    :param examples: Examples observed
    :param attributes: the attributes that contribute to the ship's decision to shoot: ['angle', 'distance']
    :param parent_examples: attributes that were used in the previous call to the recursive function
    :return:
    """
    if len(examples) == 0:
        return plurality_value(parent_examples)
    all_same = True
    classification = examples[0].find('shotHitTarget').text
    for example in examples[1:]:
        if classification != example.find('shotHitTarget').text:
            all_same = False
    if all_same:
        return Node(None, None, classification)
    if len(attributes) == 0:
        return plurality_value(examples)
    max_importance = -1  # what initialize?
    max_attribute = None
    # argmaxa ∈ attributes IMPORTANCE(a, examples)
    # The notation argmaxa ∈ S f (a) computes the element a of set S that has the maximum value of f (a).
    for attribute in attributes:
        imp = importance(attribute, examples)
        if imp > max_importance:
            max_importance = imp
            max_attribute = attribute
    tree = Node(max_attribute, {}, None)
    for val in possible_values(max_attribute):
        exs = []
        for e in examples:
            if matches_value(e, max_attribute, val):
                exs.append(e)
        atts = [att for att in attributes if att is not max_attribute]
        subtree = decision_tree_learning(exs, atts, examples)
        branch_label = val
        tree.branches[branch_label] = subtree
    return tree


def importance(attribute, examples):
    remainder = 0
    values = possible_values(attribute)
    d = len(values)
    p = len([ex for ex in examples if hit(ex)])
    n = len([ex for ex in examples if not hit(ex)])
    for k in range(d):
        pk = len([ex for ex in examples if matches_value(ex, attribute, values[k]) and hit(ex)])
        nk = len([ex for ex in examples if matches_value(ex, attribute, values[k]) and not hit(ex)])
        q = pk / (pk + nk) if pk != 0 or nk != 0 else 0
        r = ((pk + nk) / (p + n)) * b(q)
        remainder += r
    q = p / (p + n)
    gain = b(q) - remainder
    return gain


def plurality_value(examples):
    trues = 0
    total = 0
    for example in examples:
        total += 1
        if hit(example):
            trues += 1
    if trues > total / 2:
        return 'True'
    return 'False'


def hit(example):
    return example.find('shotHitTarget').text == 'True'


def possible_values(attribute):
    if attribute == 'angle':
        return [math.pi - (math.pi / 20) * d for d in range(20)]
    if attribute == 'distance':
        return [200 - 10 * d for d in range(20)]


def matches_value(example, attribute, val):
    actual = float(example.find(attribute).text)
    possibles = sorted(possible_values(attribute), reverse=True)
    rounded_down = 0
    for possible in possibles:
        if possible < actual:
            rounded_down = possible
    return rounded_down == val


def b(q):
    if q == 0 or q == 1:
        return 0
    return -(q * math.log(q, 2) + (1 - q) * math.log(1 - q, 2))


class Node:
    def __init__(self, attribute, branches, classification):
        self.attribute = attribute
        self.branches = branches
        self.classification = classification

    def __repr__(self):
        if self.classification is None:
            s = self.attribute + '\n'
            for branch in self.branches:
                s += repr(branch) + '  '
            return s
        else:
            return self.classification


if __name__ == '__main__':
    main()
