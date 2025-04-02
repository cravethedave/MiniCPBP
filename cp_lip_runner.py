import random
import threading
import subprocess
from threading import Thread

THREAD_COUNT = 7

# mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV12' -Dexec.args="40 maxMarginalStrength false false 2 1 600 3 4"

def call_maven(instance: tuple[str,int,int,int,int], thread_number: int):
    print(f"###################### {thread_number}th molecule ######################")
    method,a,b,c,d = instance
    subprocess.call([
        "mvn",
        "exec:java",
        f"-Dexec.args=40 {method} true false 2 1 600 {a} {b} {c} {d}"
    ],)

weight_combos = [
    (1750,2250),
    (2750,3250),
    (3750,4250)
]

logP_combos = [
    (-400,-300),
    (-200,-100),
    (100,200)
]

deterministic_methods = [
    # "firstFailMaxMarginalValue",
    # "domWdegMaxMarginalValue",
    "domWdeg",
    # "maxMarginal",
    # "maxMarginalStrength",
    # "maxMarginalStrengthLDS",
    # "minEntropy"
]
instances_to_run: list[tuple[str,int,int]] = []
for method in deterministic_methods:
    for a,b in weight_combos:
        for c,d in logP_combos:
            instances_to_run.append((method,a,b,c,d))
            
random_methods = [
    # "dom-random",
    # "domWdegRandom",
]
for method in random_methods:
    for a,b in weight_combos:
        for c,d in logP_combos:
            for _ in range(11):
                instances_to_run.append((method,a,b,c,d))

random.shuffle(instances_to_run)
threads: list[Thread] = []
thread_number = 0
while len(instances_to_run) > 0:
    threads = [iter for iter in threads if iter.is_alive()]
    if threading.active_count() < THREAD_COUNT + 1:
        thread_number += 1
        instance = instances_to_run.pop()
        threads.append(Thread(target=call_maven, args=(instance, thread_number, )))
        threads[-1].start()

for t in threads:
    if t.is_alive():
        t.join()

print("All instances run, data is in files")