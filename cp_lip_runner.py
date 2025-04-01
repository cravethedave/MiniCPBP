import random
import threading
import subprocess
from threading import Thread

THREAD_COUNT = 7

# mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV12' -Dexec.args="40 maxMarginalStrength false false 2 1 600 3 4"

def call_maven(method: str, thread_number: int):
    print(f"###################### {thread_number}th molecule ######################")
    subprocess.call([
        "mvn",
        "exec:java",
        f"-Dexec.args=40 {method} true false 2 1 600 0 0"
    ],)

deterministic_methods = [
    "firstFailMaxMarginalValue",
    "domWdegMaxMarginalValue",
    "domWdeg",
    "maxMarginal",
    "maxMarginalStrength",
    "maxMarginalStrengthLDS",
    "minEntropy"
]
instances_to_run: list[tuple[str,int,int]] = []
for method in deterministic_methods:
    instances_to_run.append(method)
            
random_methods = [
    "dom-random",
    "domWdegRandom",
]
for method in random_methods:
    for _ in range(11):
        instances_to_run.append(method)

random.shuffle(instances_to_run)
threads: list[Thread] = []
thread_number = 0
while len(instances_to_run) > 0:
    threads = [iter for iter in threads if iter.is_alive()]
    if threading.active_count() < THREAD_COUNT + 1:
        thread_number += 1
        method = instances_to_run.pop()
        threads.append(Thread(target=call_maven, args=(method, thread_number, )))
        threads[-1].start()

for t in threads:
    if t.is_alive():
        t.join()

print("All instances run, data is in files")