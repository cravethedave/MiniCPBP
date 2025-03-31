#!/usr/bin/env python3
import subprocess
import time

BASE_LINE = "#!/bin/bash\nmodule load maven\nexport JAVA_TOOL_OPTIONS=-Xmx1g\nmvn compile -q\n"
# BASE_COMMAND = "mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV7' -q -Dexec.args="
BASE_COMMAND = "mvn exec:java -q -Dexec.args="
TIME = '0:15:00'
MEM = '1G'

# minWt, maxWt, minC, maxC, #cycles, #branches
def cc_heuristic_runner(methods, test_cases, size=20, diff=''):
    print("Starting heuristic jobs.")
    for method in methods:
        for index, test in enumerate(test_cases):
            arguments = f"{size} {method} {' '.join(test)}"
            info_print = f"echo {arguments}\n"
            command = f"{BASE_COMMAND}'{arguments}'"
            file_content = BASE_LINE+info_print+command
            
            identifier = f"{size}_{method}"
            if test[0] == "true":
                identifier += "_lip"
            if test[1] == "true":
                identifier += f"_smpl_k{test[2]}"
            if test[3] != "0":
                identifier += f"_{test[3]}sols"
            if test[4] != "0":
                identifier += f"_{test[4]}secs"
            if test[5] != "0":
                identifier += f"_c{test[5]}"
            if test[6] != "0":
                identifier += f"_b{test[6]}"
            
            identifier = f"{size}_{method}_c{test[5]}b{test[6]}{diff}"
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
            ])
            time.sleep(0.1) # prevents overloading compute canada
            
    print("Done queueing heuristic jobs.")

def cc_random_runner(test_cases, method='rnd', size=20, diff=''):
    print(f"Starting {method} job.")
    for index, test in enumerate(test_cases):
        arguments = f"{size} {method} {' '.join(test)}"
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        for i in range(11):
            identifier = f"{size}_{method}_c{test[4]}b{test[5]}_{i}{diff}"
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
                f.close()
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
            ])
            time.sleep(0.1) # prevents overloading compute canada
    print("Done queueing random jobs.")

def home_runner(method, test, size=20):
    arguments = f"{size} {method} {' '.join(test)}"
    print(arguments)
    command = f"{BASE_COMMAND}'{arguments}'"
    p = subprocess.Popen([
        "/bin/sh",
        "-c",
        f"{command}"
    ])
    p.wait()
    p.kill()

def run_failed(test_cases):
    for i,test in enumerate(test_cases):
        arguments = ' '.join(test)
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        identifier = f"{'_'.join(test)}_{i}"
        name = f"job_{identifier}.sh"
        with open(name, 'w') as f:
            f.write(file_content)
            f.close()
        
        subprocess.call(["chmod", "+x", name])
        subprocess.Popen([
            "/bin/sh",
            "-c",
            f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
        ])
        time.sleep(1) # prevents overloading compute canada
    print("Done queueing failed jobs.")

# doLipinski, doSampling, sampleExponent, #sols, limitInSeconds, #cycles, #branches
# Results are domWdeg and maxMarginal ??m??s - ??????, ??.??s - ?????
test_cases = [
#   ["00000","00001","2","3","004","5","6"],
#   ["lpnsk","sampl","k","#","lim","c","b"],
    ["false","false","2","1","600","1","2"],
    ["false","false","2","1","600","1","3"],
    ["false","false","2","1","600","1","4"],
    ["false","false","2","1","600","2","2"],
    ["false","false","2","1","600","2","3"],
    ["false","false","2","1","600","2","4"],
    ["false","false","2","1","600","3","2"],
    ["false","false","2","1","600","3","3"],
    ["false","false","2","1","600","3","4"],
    
    ["true","false","2","1","600","1","2"],
    ["true","false","2","1","600","1","3"],
    ["true","false","2","1","600","1","4"],
    ["true","false","2","1","600","2","2"],
    ["true","false","2","1","600","2","3"],
    ["true","false","2","1","600","2","4"],
    ["true","false","2","1","600","3","2"],
    ["true","false","2","1","600","3","3"],
    ["true","false","2","1","600","3","4"],
]

methods = [
    "domWdeg",
    # "domWdegLDS",
    # "domWdegRandom",
    "domWdegMaxMarginalValue",
    # "dom-random",
    "maxMarginal",
    # "maxMarginalRestart",
    # "maxMarginalLDS",
    "maxMarginalStrength",
    "maxMarginalStrengthLDS",
    "firstFailMaxMarginalValue",
    # "lexicoMarginal",
    # "impact",
    # "impactRestart",
    # "impactLDS",
    "minEntropy",
    # "minEntropyLDS",
    # "impactMinVal",
    # "impactMinValRestart",
    # "minEntropyBiasedWheel",
]

cc_heuristic_runner(methods, test_cases, size=40)
cc_random_runner(test_cases, method='domWdegRandom', size=40)
cc_random_runner(test_cases, method='dom-random', size=40)

failed = []

# cc_heuristic_runner(methods, test_cases, size=30)
# run_failed(failed)
print("Have a nice day!")