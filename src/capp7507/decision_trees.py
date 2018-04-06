import gzip
import math
import xml.etree.ElementTree as ET

import matplotlib.pyplot as plt


def main():
    """
    Set up global variables and call the decision tree learning algorithm from the book
    Then evaluate
    """
    training_examples = get_examples(
        'shooting_data.xml.gz'
    )

    n_examples = []
    accuracies = []
    biases = []
    for i in range(20, len(training_examples), 20):
        decision_tree = decision_tree_learning(training_examples[:i], ['angle', 'distance', 'targetSpeed'], [])
        print(decision_tree)

        test_examples = get_examples(
            'test_shooting_data.xml.gz'
        )

        accuracy, bias = evaluate(decision_tree, test_examples)
        n_examples.append(i)
        accuracies.append(accuracy)
        biases.append(bias)
    plot(n_examples, accuracies, biases)


def plot(n_examples, accuracies, biases):
    plt.title('Decision Tree Learning')
    plt.xlabel('Examples')
    plt.plot(n_examples, accuracies)
    plt.plot(n_examples, biases)
    plt.legend(['Accuracy', 'Bias'])
    plt.show()


class Example(object):
    def __init__(self, shot):
        self.angle = float(shot.find('angle').text)
        self.distance = float(shot.find('distance').text)
        self.target_speed = float(shot.find('distance').text)
        self.hit = shot.find('shotHitTarget').text == 'true'

    def __getitem__(self, item):
        if item == 'angle':
            return self.angle
        if item == 'distance':
            return self.distance
        if item == 'targetSpeed':
            return self.target_speed
        return None


def get_xml(file_name):
    with gzip.open(file_name, 'rb') as f:
        return ET.parse(f)


def get_examples(file_name):
    xml_tree = get_xml(file_name)
    root = xml_tree.getroot()
    hits = []
    didnt_finish = 0
    examples = []
    for shot in root.iter('capp7507.ShotAttempt'):
        if 'true' == shot.find('missileGone').text:
            example = Example(shot)
            examples.append(example)
            hits.append(example.hit)
        else:
            didnt_finish += 1
    print('hits: ' + str(hits.count(True)))
    print('misses: ' + str(hits.count(False)))
    print('didnt finish: ' + str(didnt_finish))
    return examples


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
        return LeafNode(classification)
    if len(attributes) == 0:
        return plurality_value(examples)
    split_attribute, split_val = find_split(examples, attributes)
    tree = Node(split_attribute, split_val)
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
    tree.left_branch = left_subtree
    tree.right_branch = right_subtree
    return tree


def plurality_value(examples):
    trues = 0
    total = 0
    for example in examples:
        total += 1
        if example.hit:
            trues += 1
    if trues > total / 2:
        return LeafNode(True)
    return LeafNode(False)


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
    def __init__(self, attribute, split):
        self.attribute = attribute
        self.split = split
        self.left_branch = None
        self.right_branch = None
        self.classification = None

    def __str__(self):
        s = '\n' + self.attribute + '\n'
        if self.left_branch.classification is not None:
            s += f'{self.attribute} < {self.split}' + ': ' + str(self.left_branch) + '    '
        if self.right_branch.classification is not None:
            s += f'{self.attribute} >= {self.split}' + ': ' + str(self.right_branch) + '    '
        if self.left_branch.classification is None:
            s += '\n' + f'{self.attribute} < {self.split}' + ':'
            s += str(self.left_branch)
        if self.right_branch.classification is None:
            s += '\n' + f'{self.attribute} >= {self.split}' + ':'
            s += str(self.right_branch)
        return s


class LeafNode:
    def __init__(self, classification):
        self.classification = classification

    def __str__(self):
        return str(self.classification)


def evaluate(tree, examples):
    confusion_matrix = fill_confusion_matrix(tree, examples)
    a, b, c, d = confusion_matrix
    accuracy = (a + d) / (a + b + c + d)
    bias = (a + b) / (a + c)
    return accuracy, bias


def fill_confusion_matrix(tree, examples):
    a = 0  # forecast yes and observed yes
    b = 0  # forecast yes and observed no
    c = 0  # forecast no and observed yes
    d = 0  # forecast no and observed no

    for example in examples:
        node = tree
        while node.classification is None:
            if example[node.attribute] < node.split:
                node = node.left_branch
            else:
                node = node.right_branch
        if node.classification and example.hit:
            a += 1
        elif node.classification and not example.hit:
            b += 1
        elif not node.classification and example.hit:
            c += 1
        elif not node.classification and not example.hit:
            d += 1

    return [a, b, c, d]


if __name__ == '__main__':
    main()
