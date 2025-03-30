import numpy as np 
import matplotlib.pyplot as plt
import matplotlib as mpl
from mpl_toolkits.axes_grid1 import make_axes_locatable
from math import log

with open("server/perplexity_graph_data.txt", 'r') as f:
    lines = f.readlines()

data: list[list[float]] = []
names = []
failed = 0
for i, line in enumerate(lines):
    try:
        name, values = line.split(':')
        values: list[float] = [-float(iter) for iter in values.split(',')]
        for i in range(len(values), 40):
            values.append(0)
    except ValueError:
        print(f"Failed line {i}")
        failed += 1
        continue
    data.append(values)
    names.append(name)

print(f"failed {failed} out of {failed + len(data)}")

ax = plt.subplot()

ax.set_yticks(range(len(names)), names)

data = np.ma.masked_equal(data, 0)
# data = np.array(data)
im = ax.imshow(data,alpha=1,cmap=mpl.colormaps['YlGn'])

divider = make_axes_locatable(ax)
cax = divider.append_axes("right", size="5%", pad=0.05)
ax.figure.colorbar(im, cax=cax)

plt.show()