with open("data/lingo_weights.txt", 'r') as f:
    lines = f.readlines()

values = [iter.split(' ') for iter in lines]

# remove 7s and 8s
first_filter = [(lingo,score) for lingo,score in values if '7' not in lingo and '8' not in lingo]

# add x,t,r
second_filter = []
for lingo, score in first_filter:
    tokens = lingo.split(',')
    recombined = [[]]
    for t in tokens:
        if t == 'N':
            to_add = ['N', 'T']
        elif t == 'O':
            to_add = ['O', 'X']
        elif t == 'S':
            to_add = ['S', 'R']
        else:
            to_add = [t]
        new_recombined = []
        for iter in recombined:
            for a in to_add:
                new_recombined.append(iter + [a])
        recombined = new_recombined
    second_filter.extend(','.join(iter) + f" {score}" for iter in recombined)

with open("data/lingo_changed.txt", 'w') as f:
    f.writelines(second_filter)