#!/usr/bin/env python3
import subprocess
import time

BASE_LINE = "#!/bin/bash\nmodule load maven\nexport JAVA_TOOL_OPTIONS=-Xmx4g\nmvn compile -q\n"
# BASE_COMMAND = "mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV7' -q -Dexec.args="
BASE_COMMAND = "mvn exec:java -q -Dexec.args="
TIME = '0:30:00'
MEM = '8G'

NUM_JOBS = 1000
INSTANCES_FOLDER = 'instances'

# minWt, maxWt, minC, maxC, #cycles, #branches
def queue_jobs():
    print("Queueing jobs.")
    for i in range(NUM_JOBS):
        arguments = f"{INSTANCES_FOLDER}/molecules_{i}.txt"
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        name = f"job_{i}.sh"
        with open(name, 'w') as f:
            f.write(file_content)
        
        subprocess.call(["chmod", "+x", name])
        subprocess.Popen([
            "/bin/sh",
            "-c",
            f"sbatch --output=slout_{i}.txt --mem={MEM} --time={TIME} {name}"
        ])
    
    print("Done queueing jobs.")

def split_molecules():
    with open('big_data_exclude/molecules_to_cover.txt', 'r') as f:
        lines = f.readlines()

    n = len(lines)
    instance_size = n // NUM_JOBS
    increase_after = NUM_JOBS - (n % NUM_JOBS)

    written = 0
    for i in range(NUM_JOBS):
        # Make file
        with open(f"{INSTANCES_FOLDER}/molecules_{i}.txt", 'w') as f:
            f.write(''.join(lines[written : written + instance_size]))
        written += instance_size
        if i == increase_after - 1:
            instance_size += 1

split_molecules()
queue_jobs()
