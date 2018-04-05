import math
import xml.etree.ElementTree as ET


# hits = []
# angles = []
# distances = []
# examples = []

class Example(object):
    def __init__(self, shot):
        self.angle = float(shot.find('angle').text)
        self.distance = float(shot.find('distance').text)
        self.hit = shot.find('shotHitTarget').text == 'true'

    def __getitem__(self, item):
        if item == 'angle':
            return self.angle
        if item == 'distance':
            return self.distance
        return None


def main():
    """
    Set up global variables and call the decision tree learning algorithm from the book
    """
    tree = ET.parse(
        '/Users/bryancapps/Dropbox/school/college/ou/classes/Artificial Intelligence/AI-spacesettlers/src/capp7507/shooting_data.xml')
    root = tree.getroot()
    hits = []
    didnt_finish = 0
    angles = []
    distances = []
    examples = []
    for shot in root.iter('capp7507.ShotAttempt'):
        if 'true' == shot.find('missileGone').text:
            example = Example(shot)
            examples.append(example)
            hits.append(example.hit)
            angles.append(example.angle)
            distances.append(example.distance)
        else:
            didnt_finish += 1
    print('hits: ' + str(hits.count(True)))
    print('misses: ' + str(hits.count(False)))
    print('didnt finish: ' + str(didnt_finish))

    tree = decision_tree_learning(examples, ['angle', 'distance'], [])
    print(tree)


# input variables: angle, distance
# output variables: hits


def find_split(examples, attributes):
    """
    Find the attribute and split_value the that splits the elements in the best way
    """
    split_attribute = None
    split_value = None
    min_gini = 100_000
    for attribute in attributes:
        examples = sorted(examples, key=lambda ex: ex[attribute])
        right_positives = len([ex for ex in examples if ex.hit])
        right_negatives = len(examples) - right_positives
        left_positives, left_negatives = 0, 0
        classification2 = examples[0].hit
        for i in range(1, len(examples)):
            classification1 = classification2
            classification2 = examples[i].hit
            if classification1:
                left_positives += 1
                right_positives -= 1
            else:
                left_negatives += 1
                right_negatives -= 1
            if classification1 != classification2:
                left_total = left_positives + left_negatives
                right_total = right_positives + right_negatives
                total = left_total + right_total
                proportion_left_pos = left_positives / left_total
                proportion_left_neg = left_negatives / left_total
                proportion_right_pos = right_positives / right_total
                proportion_right_neg = right_negatives / right_total
                gini_left = gini([proportion_left_pos, proportion_left_neg], left_total, total)
                gini_right = gini([proportion_right_pos, proportion_right_neg], right_total, total)
                gini_total = gini_left + gini_right
                if gini_total < min_gini:
                    min_gini = gini_total
                    split_value = examples[i][attribute]
                    split_attribute = attribute
    return split_attribute, split_value


def gini(proportions, group_total, total):
    return (1.0 - sum([prop * prop for prop in proportions])) * (group_total / total)


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
    classification = examples[0].hit
    for example in examples[1:]:
        if classification != example.hit:
            all_same = False
            break
    if all_same:
        return Node(None, None, classification)
    if len(attributes) == 0:
        return plurality_value(examples)
    split_attribute, split_val = find_split(examples, attributes)
    tree = Node(split_attribute, {}, None)
    exs = []
    non_exs = []
    for e in examples:
        example_attribute = e[split_attribute]
        if example_attribute < split_val:
            exs.append(e)
        else:
            non_exs.append(e)
    atts = [att for att in attributes if att is not split_attribute]
    left_subtree = decision_tree_learning(exs, atts, examples)
    right_subtree = decision_tree_learning(non_exs, atts, examples)
    left_label = f'{split_attribute} < {split_val}'
    right_label = f'{split_attribute} >= {split_val}'
    tree.branches[left_label] = left_subtree
    tree.branches[right_label] = right_subtree
    return tree


def plurality_value(examples):
    trues = 0
    total = 0
    for example in examples:
        total += 1
        if example.hit:
            trues += 1
    if trues > total / 2:
        return Node(None, None, 'True')
    return Node(None, None, 'False')


def possible_values(attribute):
    if attribute == 'angle':
        return [math.pi - (math.pi / 20) * d for d in range(20)]
    if attribute == 'distance':
        return [200 - 10 * d for d in range(20)]


def b(q):
    if q == 0 or q == 1:
        return 0
    return -(q * math.log(q, 2) + (1 - q) * math.log(1 - q, 2))


class Node:
    def __init__(self, attribute, branches, classification):
        self.attribute = attribute
        self.branches = branches
        self.classification = classification

    def __str__(self):
        if self.classification is None:
            s = '\n' + self.attribute + '\n'
            for branch_label in self.branches:
                branch = self.branches[branch_label]
                if branch.classification is not None:
                    s += str(branch_label) + ': ' + str(branch) + '    '
            for branch_label in self.branches:
                branch = self.branches[branch_label]
                if branch.classification is None:
                    s += '\n' + str(branch_label) + ':'
                    s += str(branch)
            return s
        else:
            return str(self.classification)


if __name__ == '__main__':
    main()
