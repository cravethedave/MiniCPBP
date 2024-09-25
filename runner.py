#!/usr/bin/env python3
import subprocess
import time

BASE_LINE = "#!/bin/bash\nmodule load maven\nexport JAVA_TOOL_OPTIONS=-Xmx1g\nmvn compile -q\n"
BASE_COMMAND = "mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV7' -q -Dexec.args="
TIME = '1:00:00'
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
            
            identifier = f"{size}_{method}_c{test[4]}b{test[5]}{diff}"
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
            time.sleep(3) # prevents overloading compute canada
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

# minWt, maxWt, minC, maxC, #cycles, #branches
# Results are domWdeg and maxMarginal ??m??s - ??????, ??.??s - ?????
test_cases = [
    ["4500","5000","0","100","1","2"],
    ["4500","5000","0","100","1","3"],
    ["4500","5000","0","100","1","4"],
    ["4500","5000","0","100","2","2"],
    ["4500","5000","0","100","2","3"],
    ["4500","5000","0","100","2","4"],
    ["4500","5000","0","100","3","2"],
    ["4500","5000","0","100","3","3"],
    ["4500","5000","0","100","3","4"],
]

methods = [
    # "domWdeg",
    # "domWdegRestart",
    # "domWdegLDS",
    "maxMarginal",
    # "maxMarginalRestart",
    # "maxMarginalLDS",
    # "maxMarginalStrength",
    # "maxMarginalStrengthLDS",
    "lexicoMarginal",
    # "impact",
    # "impactRestart",
    # "impactLDS",
    # "minEntropy",
    # "minEntropyLDS",
    # "impactMinVal",
    # "impactMinValRestart",
    # "minEntropyBiasedWheel",
    # "domWdegRandom",
]

cc_heuristic_runner(methods, test_cases, size=40)
# cc_random_runner(test_cases, method='domWdegRandom', size=40)

failed = []

# cc_heuristic_runner(methods, test_cases, size=30)
# run_failed(failed)
print("Have a nice day!")