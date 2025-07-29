import os

files = [iter for iter in os.listdir() if iter.startswith('slout')]

file_lines = [open(iter,'r').readlines() for iter in files]


